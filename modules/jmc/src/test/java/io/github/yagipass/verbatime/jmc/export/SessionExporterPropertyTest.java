package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.jmc.export.ParsedExport.BodyLine;
import io.github.yagipass.verbatime.jmc.export.ParsedExport.HotRow;
import io.github.yagipass.verbatime.jmc.export.ParsedExport.OutlineRow;
import io.github.yagipass.verbatime.jmc.export.SessionExporter.Result;
import io.github.yagipass.verbatime.jmc.index.RandomTraces;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder.Call;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SessionExporterPropertyTest {

  @TempDir Path dir;

  private static final long[] FLOORS_NS = {0, 1_000, 10_000, 100_000};

  private static final class Node {
    final Call f;

    final int seq;

    final List<Node> kids = new ArrayList<>();

    long subtreeFrames = 1;

    long subtreeThrown;

    boolean listed;

    long line;

    long subLines;

    Node(Call f, int seq) {
      this.f = f;
      this.seq = seq;
      subtreeThrown = f.thrown() ? 1 : 0;
    }
  }

  @Test
  void everySessionOfRandomTracesMatchesTheReferenceAtEveryFloor() throws IOException {
    for (long seed = 1; seed <= 12; seed++) {
      byte[] bytes = RandomTraces.random(seed);
      ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
      TraceSnapshot d = TestTraces.index(bytes, 1 << 30);
      for (long floorNs : FLOORS_NS) {
        for (Session s : d.sessions) {
          boolean small = (seed + floorNs / 1000 + s.seq) % 2 == 0;
          String ctx =
              "seed " + seed + " floor " + floorNs + " session " + s.seq + (small ? " small" : "");
          Path out = dir.resolve("seed" + seed + "-f" + floorNs + "-s" + s.seq + ".txt");
          Result r =
              SessionExporter.export(
                  d,
                  s,
                  floorNs,
                  out,
                  TraceIndexer.ProgressListener.NONE,
                  small ? 3 : SessionExporter.OUTLINE_LIMIT,
                  small ? 64 : 1 << 16);
          check(ref, d, s, floorNs, out, r, small ? 3 : SessionExporter.OUTLINE_LIMIT, ctx);
          Files.delete(out);
        }
      }
    }
  }

  @Test
  void truncatedRecordingsFlagUnclosedCallsAndSayTheFileIsTruncated() throws IOException {
    byte[] full = RandomTraces.random(7);
    for (int pct : new int[] {30, 60, 90}) {
      byte[] prefix = Arrays.copyOf(full, full.length * pct / 100);
      ReferenceDecoder.Result ref = ReferenceDecoder.decode(prefix);
      TraceSnapshot d = TestTraces.index(prefix, 1 << 30);
      assertTrue(d.truncated, "prefix " + pct + "%");
      for (Session s : d.sessions) {
        String ctx = "prefix " + pct + "% session " + s.seq;
        Path out = dir.resolve("trunc-" + pct + "-" + s.seq + ".txt");
        Result r =
            SessionExporter.export(
                d,
                s,
                100_000,
                out,
                TraceIndexer.ProgressListener.NONE,
                SessionExporter.OUTLINE_LIMIT,
                1 << 16);
        ParsedExport t = check(ref, d, s, 100_000, out, r, SessionExporter.OUTLINE_LIMIT, ctx);
        assertTrue(
            t.line(ParsedExport.LINE_FILE).endsWith("  format: vbtm v1  status: truncated"),
            ctx + ": " + t.line(ParsedExport.LINE_FILE));
        long unclosedRef = ref.calls.stream().filter(f -> f.seq() == s.seq && f.unclosed()).count();
        long unclosedOut = t.body.stream().filter(BodyLine::unclosed).count();
        assertEquals(unclosedRef, unclosedOut, ctx + " every unclosed call is listed and flagged");
        Files.delete(out);
      }
    }
  }

  private ParsedExport check(
      ReferenceDecoder.Result ref,
      TraceSnapshot d,
      Session s,
      long floorNs,
      Path out,
      Result r,
      int outlineNodes,
      String ctx)
      throws IOException {
    ParsedExport t = ParsedExport.read(out);
    t.checkInvariants(ctx);

    List<Call> calls = ref.calls.stream().filter(f -> f.seq() == s.seq).toList();
    Map<Integer, List<Node>> pending = new HashMap<>();
    List<Node> all = new ArrayList<>();
    for (int i = 0; i < calls.size(); i++) {
      Call f = calls.get(i);
      Node n = new Node(f, i + 1);
      List<Node> kids = pending.remove(f.depth() + 1);
      if (kids != null) {
        for (Node k : kids) {
          n.kids.add(k);
          n.subtreeFrames += k.subtreeFrames;
          n.subtreeThrown += k.subtreeThrown;
        }
      }
      pending.computeIfAbsent(f.depth(), k -> new ArrayList<>()).add(n);
      all.add(n);
    }
    List<Node> roots = pending.getOrDefault(0, List.of());
    assertEquals(
        calls.isEmpty() ? 0 : 1, pending.size(), ctx + " only the roots are left without a parent");

    Map<Integer, Integer> excNo = new LinkedHashMap<>();
    for (Call f : calls) {
      if (f.exceptionId() > 0) {
        excNo.putIfAbsent(f.exceptionId(), excNo.size() + 1);
      }
    }
    assertEquals(
        excNo.size(),
        t.exceptions.size(),
        ctx + " the exceptions table lists every class thrown, listed or tiny");
    for (Map.Entry<Integer, Integer> e : excNo.entrySet()) {
      assertEquals(
          d.exceptionName(e.getKey()),
          t.exceptionClass(e.getValue()),
          ctx + " e" + e.getValue() + " is numbered in first-throw order");
    }

    List<String> expected = new ArrayList<>();
    List<Node> lineNodes = new ArrayList<>();
    for (Node root : roots) {
      emit(root, 0, s, floorNs, d, excNo, expected, lineNodes);
    }
    assertEquals(expected.size(), t.body.size(), ctx + " body line count");
    for (int i = 0; i < expected.size(); i++) {
      assertEquals(expected.get(i), describe(t, t.body.get(i)), ctx + " body line " + (i + 1));
    }
    for (int i = 0; i < lineNodes.size(); i++) {
      if (lineNodes.get(i) != null) {
        lineNodes.get(i).line = i + 1L;
      }
    }
    for (Node n : all) {
      if (n.listed) {
        n.subLines = subtreeLines(t, n.line);
      }
    }

    long listed = all.stream().filter(n -> n.listed).count();
    long maxDepth = calls.stream().mapToInt(Call::depth).max().orElse(0);
    Set<Integer> methodIds = new HashSet<>();
    for (Call f : calls) {
      methodIds.add(f.methodId());
    }
    assertEquals(
        "duration: "
            + SessionExporter.msText(Math.max(s.endNs - s.startNs, 0) / 100)
            + " ms  calls: "
            + String.format(java.util.Locale.US, "%,d", s.callCount)
            + "  max depth: "
            + maxDepth
            + "  methods used: "
            + methodIds.size(),
        t.line(ParsedExport.LINE_DURATION),
        ctx);
    assertEquals(
        methodIds.size(), t.methods.size(), ctx + " methods table covers exactly the methods used");
    assertTrue(
        t.line(ParsedExport.LINE_FLOOR)
            .startsWith(
                "floor: "
                    + SessionExporter.floorLabel(floorNs)
                    + "  calls >= floor: "
                    + String.format(java.util.Locale.US, "%,d", listed)
                    + ", one line each  calls < floor: "
                    + String.format(java.util.Locale.US, "%,d", calls.size() - listed)),
        ctx + ": " + t.line(ParsedExport.LINE_FLOOR));
    assertEquals(
        !s.ended,
        t.line(ParsedExport.LINE_SESSION).endsWith("  [unclosed]"),
        ctx + " unclosed session marked: " + t.line(ParsedExport.LINE_SESSION));
    assertEquals(t.lines.size(), r.lines(), ctx);
    assertEquals(Files.size(out), r.bytes(), ctx);
    assertEquals(calls.size(), r.calls(), ctx);
    assertEquals(s.callCount, r.calls(), ctx + " agrees with the index");
    assertEquals(listed, r.listedCalls(), ctx);
    assertEquals(calls.size() - listed, r.belowFloorCalls(), ctx);
    assertEquals(t.body.size(), r.bodyLines(), ctx);
    assertEquals(maxDepth, r.maxDepth(), ctx);

    List<Node> listedNodes =
        all.stream()
            .filter(n -> n.listed)
            .sorted(
                Comparator.comparingLong((Node n) -> n.f.durNs())
                    .reversed()
                    .thenComparing(Comparator.comparingInt((Node n) -> n.seq).reversed()))
            .toList();
    List<Node> top = listedNodes.subList(0, Math.min(outlineNodes, listedNodes.size()));
    Set<Node> topSet = new HashSet<>(top);
    long threshold = top.isEmpty() ? 0 : top.get(top.size() - 1).f.durNs();
    assertEquals(threshold, r.outlineThresholdNs(), ctx);
    assertEquals(top.size(), t.outline.size(), ctx + " outline size");
    Map<Node, Node> parent = new HashMap<>();
    for (Node n : all) {
      for (Node k : n.kids) {
        parent.put(k, n);
      }
    }
    Map<Long, Node> byLine = new HashMap<>();
    for (Node n : all) {
      if (n.listed) {
        byLine.put(n.line, n);
      }
    }
    long denom = Math.max(Math.max(s.endNs - s.startNs, 0) / 100, 1);
    for (OutlineRow o : t.outline) {
      Node n = byLine.get(t.bodyLineOf(o.anchor()));
      assertTrue(n != null, ctx + " anchor resolves to a listed call: " + o.raw());
      assertTrue(topSet.contains(n), ctx + " outline holds exactly the top-K: " + o.raw());
      Node p = parent.get(n);
      assertTrue(
          p == null || topSet.contains(p), ctx + " outline is closed under ancestors: " + o.raw());
      assertEquals((n.f.startNs() - s.startNs) / 100, o.start(), ctx + o.raw());
      assertEquals(n.f.durNs() / 100, o.dur(), ctx + o.raw());
      assertEquals(n.f.depth(), o.depth(), ctx + o.raw());
      assertEquals(d.methodName(n.f.methodId()), t.fullName(o.name()), ctx + o.raw());
      assertEquals(n.kids.isEmpty() ? -1 : n.f.selfNs() / 100, o.self(), ctx + o.raw());
      @Var long outlineKids = 0;
      @Var long outlineKidDur = 0;
      for (Node k : n.kids) {
        if (topSet.contains(k)) {
          outlineKids++;
          outlineKidDur += k.f.durNs();
        }
      }
      assertEquals(n.kids.size() - outlineKids, o.hidden(), ctx + " hidden children: " + o.raw());
      assertEquals(
          o.hidden() > 0 ? Math.max(n.f.durNs() - n.f.selfNs() - outlineKidDur, 0) / 100 : 0,
          o.hiddenDur(),
          ctx + " hidden time: " + o.raw());
      long tenths = (n.f.durNs() / 100 * 1000 + denom / 2) / denom;
      assertEquals(tenths / 10 + "." + tenths % 10, o.pct(), ctx + o.raw());
      assertEquals(n.f.thrown(), o.thrown(), ctx + o.raw());
      assertEquals(
          n.f.exceptionId() > 0 ? excNo.get(n.f.exceptionId()) : 0,
          o.excNo(),
          ctx + " outline names the class thrown: " + o.raw());
      assertEquals(n.f.unclosed(), o.unclosed(), ctx + o.raw());
      assertEquals(n.subLines, o.subLines(), ctx + " [N lines]: " + o.raw());
    }
    String heading =
        "## outline: nodes >= "
            + SessionExporter.msText(threshold / 100)
            + " ms, "
            + top.size()
            + " of "
            + String.format(java.util.Locale.US, "%,d", listed)
            + " body nodes";
    assertEquals(heading, t.line(t.outlineStart), ctx);

    Map<Integer, long[]> agg = new LinkedHashMap<>();
    for (Call f : calls) {
      if (!f.unclosed()) {
        long[] a = agg.computeIfAbsent(f.methodId(), k -> new long[3]);
        a[0]++;
        a[1] += f.durNs() / 100;
        a[2] += f.selfNs() / 100;
      }
    }
    List<Integer> bySelf = new ArrayList<>(agg.keySet());
    bySelf.sort(
        (x, y) -> {
          long[] a = agg.get(x);
          long[] b = agg.get(y);
          @Var int c = Long.compare(b[2], a[2]);
          if (c == 0) {
            c = Long.compare(b[1], a[1]);
          }
          if (c == 0) {
            c = Long.compare(b[0], a[0]);
          }
          return c != 0 ? c : Integer.compare(x, y);
        });
    List<Integer> byCalls = new ArrayList<>(agg.keySet());
    byCalls.sort(
        (x, y) -> {
          long[] a = agg.get(x);
          long[] b = agg.get(y);
          @Var int c = Long.compare(b[0], a[0]);
          if (c == 0) {
            c = Long.compare(b[2], a[2]);
          }
          return c != 0 ? c : Integer.compare(x, y);
        });
    checkHot(t, d, agg, bySelf, SessionExporter.HOT_BY_SELF_LIMIT, t.hotBySelf, ctx + " by self");
    checkHot(
        t, d, agg, byCalls, SessionExporter.HOT_BY_CALLS_LIMIT, t.hotByCalls, ctx + " by calls");
    assertFalse(
        Files.exists(dir.resolve(out.getFileName() + ".part")), ctx + " staging file removed");
    return t;
  }

  private static void checkHot(
      ParsedExport t,
      TraceSnapshot d,
      Map<Integer, long[]> agg,
      List<Integer> order,
      int limit,
      List<HotRow> rows,
      String ctx) {
    assertEquals(Math.min(limit, order.size()), rows.size(), ctx + " rows");
    for (int i = 0; i < rows.size(); i++) {
      int methodId = order.get(i);
      HotRow row = rows.get(i);
      String c = ctx + " row " + (i + 1) + ": " + row;
      assertEquals(d.methodName(methodId), t.fullName(row.name()), c);
      assertEquals(agg.get(methodId)[2], row.self(), c);
      assertEquals(agg.get(methodId)[1], row.total(), c);
      assertEquals(agg.get(methodId)[0], row.calls(), c);
    }
  }

  private static void emit(
      Node n,
      int depth,
      Session s,
      long floorNs,
      TraceSnapshot d,
      Map<Integer, Integer> excNo,
      List<String> out,
      List<Node> lineNodes) {
    n.listed = true;
    long start = (n.f.startNs() - s.startNs) / 100;
    StringBuilder sb = new StringBuilder();
    sb.append(start)
        .append(' ')
        .append(n.f.durNs() / 100)
        .append(' ')
        .append(depth)
        .append(' ')
        .append(d.methodName(n.f.methodId()));
    if (!n.kids.isEmpty()) {
      sb.append(" self ").append(n.f.selfNs() / 100);
    }
    if (n.f.thrown()) {
      sb.append(" !");
      if (n.f.exceptionId() > 0) {
        sb.append('e').append(excNo.get(n.f.exceptionId()));
      }
    }
    if (n.f.unclosed()) {
      sb.append(" ~");
    }
    out.add(sb.toString());
    lineNodes.add(n);
    Map<Integer, Long> tiny = new LinkedHashMap<>();
    @Var long belowFloorCalls = 0;
    @Var long tinyNested = 0;
    @Var long tinyThrown = 0;
    @Var long tinyDur = 0;
    for (Node k : n.kids) {
      boolean listed = k.f.unclosed() || k.f.durNs() >= floorNs;
      if (listed) {
        emit(k, depth + 1, s, floorNs, d, excNo, out, lineNodes);
      } else {
        belowFloorCalls++;
        tinyNested += k.subtreeFrames;
        tinyThrown += k.subtreeThrown;
        tinyDur += k.f.durNs();
        tiny.merge(k.f.methodId(), 1L, Long::sum);
      }
    }
    if (belowFloorCalls > 0) {
      List<Map.Entry<Integer, Long>> items = new ArrayList<>(tiny.entrySet());
      items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
      StringBuilder a = new StringBuilder();
      a.append(start)
          .append(' ')
          .append(tinyDur / 100)
          .append(' ')
          .append(depth + 1)
          .append(" ·")
          .append(belowFloorCalls)
          .append(" calls <")
          .append(SessionExporter.floorLabelCompact(floorNs))
          .append(", ")
          .append(tinyNested)
          .append(" incl. nested");
      if (tinyThrown > 0) {
        a.append(" [!").append(tinyThrown).append(']');
      }
      a.append(": ");
      for (int i = 0; i < items.size(); i++) {
        if (i > 0) {
          a.append(", ");
        }
        a.append(d.methodName(items.get(i).getKey()));
        if (items.get(i).getValue() > 1) {
          a.append('×').append(items.get(i).getValue());
        }
      }
      out.add(a.toString());
      lineNodes.add(null);
    }
  }

  private static String describe(ParsedExport t, BodyLine b) {
    StringBuilder sb = new StringBuilder();
    sb.append(b.start()).append(' ').append(b.dur()).append(' ').append(b.depth()).append(' ');
    if (b.accounting()) {
      sb.append('·')
          .append(b.belowFloorCalls())
          .append(" calls <")
          .append(b.floor())
          .append(", ")
          .append(b.tinyNested())
          .append(" incl. nested");
      if (b.tinyThrown() > 0) {
        sb.append(" [!").append(b.tinyThrown()).append(']');
      }
      sb.append(": ");
      for (int i = 0; i < b.list().size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(t.fullName(b.list().get(i)[0]));
        if (!"1".equals(b.list().get(i)[1])) {
          sb.append('×').append(b.list().get(i)[1]);
        }
      }
      return sb.toString();
    }
    sb.append(t.fullName(b.name()));
    if (b.hasSelf()) {
      sb.append(" self ").append(b.self());
    }
    if (b.thrown()) {
      sb.append(" !");
      if (b.excNo() > 0) {
        sb.append('e').append(b.excNo());
      }
    }
    if (b.unclosed()) {
      sb.append(" ~");
    }
    return sb.toString();
  }

  private static long subtreeLines(ParsedExport t, long line) {
    int depth = t.body.get((int) line - 1).depth();
    @Var long n = 1;
    for (int i = (int) line; i < t.body.size(); i++) {
      if (t.body.get(i).depth() <= depth) {
        break;
      }
      n++;
    }
    return n;
  }
}
