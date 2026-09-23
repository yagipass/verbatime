package io.github.yagipass.verbatime.jmc.export;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;

final class ExportHead {

    private static final DateTimeFormatter RECORDED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx",
            Locale.ROOT);

    private record Section(String heading, List<String> rows) {
    }

    private record GcSection(List<String> rows, long ticks) {
    }

    private final TraceSnapshot data;

    private final Session session;

    private final long floorNs;

    private final ExportNames names;

    private final BodyPass pass;

    private final OutlineHeap top;

    private final int width;

    private final long sessionStartTicks;

    private final long sessionDurTicks;

    private long bodyEnd;

    ExportHead(final TraceSnapshot data, final Session session, final long floorNs, final ExportNames names,
            final BodyPass pass, final OutlineHeap top, final int width, final long sessionStartTicks,
            final long sessionDurTicks) {
        this.data = data;
        this.session = session;
        this.floorNs = floorNs;
        this.names = names;
        this.pass = pass;
        this.top = top;
        this.width = width;
        this.sessionStartTicks = sessionStartTicks;
        this.sessionDurTicks = sessionDurTicks;
    }

    long bodyEnd() {
        return bodyEnd;
    }

    byte[] build(final long bodyLines) {
        for (int id = pass.used().nextSetBit(0); id >= 0; id = pass.used().nextSetBit(id + 1)) {
            names.displayName(id);
        }
        final int methodsUsed = names.registeredCount();
        final int[] order = top.byLine();
        final int callsWidth = callsWidth();
        final List<String> hotSelf = hotRows(true, SessionExporter.HOT_BY_SELF_LIMIT, callsWidth);
        final List<String> hotCalls = hotRows(false, SessionExporter.HOT_BY_CALLS_LIMIT, callsWidth);
        final GcSection gc = gcRows();
        final List<String> gcRows = gc.rows();
        final int exceptionCount = pass.exceptionCount();

        final StringBuilder sb = new StringBuilder(1 << 16);
        header(sb, methodsUsed, gcRows.size(), gc.ticks());
        final long headerLines = newlines(sb);
        sb.append('\n');
        final String[] what = {
                "outline: the " + grouped(order.length) + " longest calls as an index into the body, where L numbers are file lines",
                "hot methods by self: " + grouped(hotSelf.size()) + " methods, self / total / calls over closed calls only",
                "hot methods by calls: " + grouped(hotCalls.size()) + " methods",
                "methods: " + grouped(methodsUsed) + " short names -> full signatures, where #2 and #3 mark colliding short names",
                "exceptions: " + grouped(exceptionCount) + " classes thrown in this session, eN -> class name",
                "gc pauses: " + grouped(gcRows.size()) + " stop-the-world pauses in this session, start dur kind collector: cause",
                "body: " + grouped(bodyLines) + " lines, one call per line in time order" };

        final long[] rowCounts = { what.length, order.length, hotSelf.size() + 1, hotCalls.size() + 1, methodsUsed,
                exceptionCount, gcRows.size(), bodyLines };
        final long[] starts = new long[rowCounts.length];
        final long[] ends = new long[rowCounts.length];
        long start = headerLines + 2;
        for (int i = 0; i < rowCounts.length; i++) {
            starts[i] = start;
            ends[i] = rowCounts[i] == 0 ? start : start + 1 + rowCounts[i];
            start = ends[i] + 2;
        }
        final long bodyStart = starts[rowCounts.length - 1];
        bodyEnd = ends[rowCounts.length - 1];
        final String[] ranges = new String[what.length];
        int rangeWidth = 0;
        for (int i = 0; i < ranges.length; i++) {
            ranges[i] = "L" + starts[i + 1] + "-L" + ends[i + 1];
            rangeWidth = Math.max(rangeWidth, ranges[i].length());
        }
        final List<String> contents = new ArrayList<>(ranges.length);
        for (int i = 0; i < ranges.length; i++) {
            final StringBuilder row = new StringBuilder(ranges[i]);
            pad(row, rangeWidth - ranges[i].length() + 2);
            contents.add(row.append(what[i]).toString());
        }
        final List<String> selfRows = new ArrayList<>(hotSelf.size() + 1);
        selfRows.add(hotHeader(callsWidth));
        selfRows.addAll(hotSelf);
        final List<String> callRows = new ArrayList<>(hotCalls.size() + 1);
        callRows.add(hotHeader(callsWidth));
        callRows.addAll(hotCalls);
        final List<String> methodRows = new ArrayList<>(methodsUsed);
        for (int i = 0; i < methodsUsed; i++) {
            final int id = names.registeredIdAt(i);
            methodRows.add(names.displayName(id) + " = " + names.fullName(id));
        }
        final List<String> excRows = new ArrayList<>(exceptionCount);
        for (int i = 0; i < exceptionCount; i++) {
            excRows.add("e" + (i + 1) + " = " + data.exceptionName(pass.exceptionIdAt(i)));
        }
        final List<Section> sections = List.of(new Section("## contents", contents),
                new Section("## outline: nodes >= " + SessionExporter.msText(top.thresholdTicks()) + " ms, "
                        + order.length + " of " + grouped(pass.listedCalls()) + " body nodes",
                        outlineRows(order, bodyStart + 1)),
                new Section("## hot methods by self, top " + SessionExporter.HOT_BY_SELF_LIMIT, selfRows),
                new Section("## hot methods by calls, top " + SessionExporter.HOT_BY_CALLS_LIMIT, callRows),
                new Section("## methods: " + grouped(methodsUsed) + " used in this session", methodRows),
                new Section("## exceptions: " + grouped(exceptionCount) + " classes thrown in this session", excRows),
                new Section("## gc pauses: " + grouped(gcRows.size()) + " in this session, clipped to it", gcRows));
        for (final Section s : sections) {
            sb.append(s.heading()).append("\n\n");
            for (final String r : s.rows()) {
                sb.append(r).append('\n');
            }
            if (!s.rows().isEmpty()) {
                sb.append('\n');
            }
        }
        sb.append("## body: ").append(grouped(bodyLines)).append(" lines\n");
        if (bodyLines > 0) {
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void header(final StringBuilder sb, final int methodsUsed, final int gcPauses, final long gcTicks) {
        final String status = data.corruptOffset >= 0 ? "corrupt at offset " + data.corruptOffset
                : data.truncated ? "truncated" : "complete";
        final String root = session.rootMethodId >= 0 ? names.displayName(session.rootMethodId) : "<no enter>";
        final String compact = SessionExporter.floorLabelCompact(floorNs);
        final long calls = pass.totalCalls();
        final long listed = pass.listedCalls();
        sb.append("# verbatime session export v1\n\n");
        sb.append("file: ").append(ExportNames.sanitize(data.path.getFileName().toString())).append("  format: ")
                .append(Vbtm.MAGIC).append(" v").append(Vbtm.VERSION).append("  status: ").append(status).append('\n');
        sb.append("session: #").append(session.seq).append("  thread: ")
                .append(ExportNames.sanitize(data.threadName(session.tid))).append("  root: ").append(root)
                .append(session.ended ? "" : "  [unclosed]").append('\n');
        sb.append("duration: ").append(SessionExporter.msText(sessionDurTicks)).append(" ms  calls: ").append(grouped(calls))
                .append("  max depth: ").append(pass.maxDepth()).append("  methods used: ").append(grouped(methodsUsed))
                .append('\n');
        sb.append("recorded: ").append(RECORDED.format(data.wallClock(session.startNs))).append('\n');
        sb.append("gc: ").append(grouped(gcPauses)).append(gcPauses == 1 ? " pause" : " pauses")
                .append(" in this session, ").append(SessionExporter.msText(gcTicks))
                .append(" ms stop-the-world across every thread, listed under ## gc pauses and not subtracted from self\n");
        sb.append("floor: ").append(SessionExporter.floorLabel(floorNs)).append("  calls >= floor: ")
                .append(grouped(listed)).append(", one line each  calls < floor: ").append(grouped(calls - listed))
                .append(", kept as per-parent counts\n");
        sb.append("units: ms, where 0.0001 ms = 1 tick of 100 ns. body line = \"start dur depth Name [self S] [!eN] [~]\"\n");
        sb.append("  \"!eN\" = ended by throw, where eN is the exception class listed under ## exceptions. A bare \"!\" = class unknown\n");
        sb.append("  \"·N calls <").append(compact)
                .append(", M incl. nested [!K]: a×3, b×2, c\" = calls below the floor made directly\n");
        sb.append("  by the previous line's method. M also counts their descendants, K ended by throw, and the line is the last child\n");
        sb.append("how to read: start from the outline and find every section's line range in the contents below. \"[N lines]\" is the\n");
        sb.append("  subtree size in the body. The ancestors of a body line are the nearest lines above it with a smaller depth, and depth 0 is the root\n");
    }

    private GcSection gcRows() {
        final TraceSnapshot.GcPauses gc = data.gc;
        final long sessionEndNs = session.endNs;
        final List<long[]> inside = new ArrayList<>();
        for (int i = 0; i < gc.count; i++) {
            final long a = Math.max(gc.startNs[i], session.startNs);
            final long b = Math.min(gc.startNs[i] + gc.durNs[i], sessionEndNs);
            if (b > a) {
                inside.add(new long[] { a, b, i });
            }
        }
        inside.sort((x, y) -> Long.compare(x[0], y[0]));
        final List<String> rows = new ArrayList<>(inside.size());
        long ticks = 0;
        for (final long[] p : inside) {
            final long startTicks = (p[0] - session.startNs) / Vbtm.NANOS_PER_TICK;
            final long durTicks = (p[1] - p[0]) / Vbtm.NANOS_PER_TICK;
            ticks += durTicks;
            final int i = (int) p[2];
            final String kind = gc.action[i] == Vbtm.GC_ACTION_MINOR ? "minor"
                    : gc.action[i] == Vbtm.GC_ACTION_MAJOR ? "major" : "pause";
            rows.add(SessionExporter.msText(startTicks) + " " + SessionExporter.msText(durTicks) + " " + kind + " "
                    + ExportNames.sanitize(gc.collector[i]) + ": " + ExportNames.sanitize(gc.cause[i]));
        }
        return new GcSection(rows, ticks);
    }

    private List<String> outlineRows(final int[] order, final long anchorBase) {
        final List<String> rows = new ArrayList<>(order.length);
        final int n = order.length;
        final long[] outlineChildren = new long[n];
        final long[] outlineChildDur = new long[n];
        final int[] lastAtDepth = new int[pass.maxDepth() + 2];
        Arrays.fill(lastAtDepth, -1);
        for (int i = 0; i < n; i++) {
            final int e = order[i];
            final int d = top.depth(e);
            if (d > 0 && lastAtDepth[d - 1] >= 0) {
                final int p = lastAtDepth[d - 1];
                outlineChildren[p]++;
                outlineChildDur[p] += top.dur(e);
            }
            lastAtDepth[d] = i;
            for (int k = d + 1; k < lastAtDepth.length; k++) {
                if (lastAtDepth[k] < 0) {
                    break;
                }
                lastAtDepth[k] = -1;
            }
        }
        final int anchorWidth = digits(anchorBase + (n > 0 ? top.line(order[n - 1]) : 0)) + 1;
        final long denom = Math.max(sessionDurTicks, 1);
        for (int i = 0; i < n; i++) {
            final int e = order[i];
            final StringBuilder sb = new StringBuilder(160);
            final String anchor = "L" + (anchorBase + top.line(e));
            sb.append(anchor);
            pad(sb, anchorWidth - anchor.length() + 1);
            padLeft(sb, SessionExporter.msText(top.start(e) - sessionStartTicks), width);
            sb.append(' ');
            padLeft(sb, SessionExporter.msText(top.dur(e)), width);
            sb.append(' ');
            final long tenths = (top.dur(e) * 1000 + denom / 2) / denom;
            padLeft(sb, tenths / 10 + "." + tenths % 10 + "%", 6);
            sb.append("  ");
            pad(sb, top.depth(e));
            sb.append(names.displayName(top.methodId(e)));
            if (top.children(e) > 0) {
                sb.append(" self ").append(SessionExporter.msText(top.self(e)));
            }
            final long hidden = top.children(e) - outlineChildren[i];
            if (hidden > 0) {
                sb.append(" hidden ").append(hidden).append(" calls ")
                        .append(SessionExporter.msText(top.dur(e) - top.self(e) - outlineChildDur[i]));
            }
            if (top.thrown(e)) {
                sb.append(" !");
                if (top.excNo(e) > 0) {
                    sb.append('e').append(top.excNo(e));
                }
            }
            if (top.unclosed(e)) {
                sb.append(" ~");
            }
            sb.append("  [").append(top.subLines(e)).append(" lines]");
            rows.add(sb.toString());
        }
        return rows;
    }

    private String hotHeader(final int callsWidth) {
        final int w = Math.max(width, 8);
        final StringBuilder sb = new StringBuilder();
        padLeft(sb, "self_ms", w);
        sb.append(' ');
        padLeft(sb, "total_ms", w);
        sb.append(' ');
        padLeft(sb, "calls", callsWidth);
        sb.append("  method");
        return sb.toString();
    }

    private int callsWidth() {
        long max = 0;
        for (final long c : pass.calls()) {
            if (c > max) {
                max = c;
            }
        }
        return Math.max(digits(max), 5);
    }

    private List<String> hotRows(final boolean bySelf, final int limit, final int callsWidth) {
        final long[] calls = pass.calls();
        final long[] totalTicks = pass.totalTicks();
        final long[] selfTicks = pass.selfTicks();
        final List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < calls.length; i++) {
            if (calls[i] > 0) {
                ids.add(i);
            }
        }
        if (bySelf) {
            ids.sort((a, b) -> {
                int c = Long.compare(selfTicks[b], selfTicks[a]);
                if (c == 0) {
                    c = Long.compare(totalTicks[b], totalTicks[a]);
                }
                if (c == 0) {
                    c = Long.compare(calls[b], calls[a]);
                }
                return c != 0 ? c : Integer.compare(a, b);
            });
        } else {
            ids.sort((a, b) -> {
                int c = Long.compare(calls[b], calls[a]);
                if (c == 0) {
                    c = Long.compare(selfTicks[b], selfTicks[a]);
                }
                return c != 0 ? c : Integer.compare(a, b);
            });
        }
        final int w = Math.max(width, 8);
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, ids.size()); i++) {
            final int id = ids.get(i);
            final StringBuilder sb = new StringBuilder();
            padLeft(sb, SessionExporter.msText(selfTicks[id]), w);
            sb.append(' ');
            padLeft(sb, SessionExporter.msText(totalTicks[id]), w);
            sb.append(' ');
            padLeft(sb, Long.toString(calls[id]), callsWidth);
            sb.append("  ").append(names.displayName(id));
            rows.add(sb.toString());
        }
        return rows;
    }

    private static long newlines(final CharSequence cs) {
        long n = 0;
        for (int i = 0; i < cs.length(); i++) {
            if (cs.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static String grouped(final long v) {
        return String.format(Locale.US, "%,d", v);
    }

    private static int digits(final long v) {
        return Long.toString(Math.max(v, 0)).length();
    }

    private static void pad(final StringBuilder sb, final int n) {
        for (int i = 0; i < n; i++) {
            sb.append(' ');
        }
    }

    private static void padLeft(final StringBuilder sb, final String s, final int w) {
        pad(sb, w - s.length());
        sb.append(s);
    }
}
