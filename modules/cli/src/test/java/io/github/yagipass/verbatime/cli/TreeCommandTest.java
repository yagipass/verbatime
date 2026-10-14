package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.TraceBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TreeCommandTest {

  @TempDir Path dir;

  @Test
  void everyCallIsPrintedWithItsIdAndCallsBelowTheFloorAreFolded() throws IOException {
    Path t = TestTraces.trace(dir);
    Cli.Result all = Cli.vbtm("tree", t, "1");
    assertEquals(0, all.code(), all.err());
    assertTrue(all.out().contains("floor: none, every call is printed"), all.out());
    assertEquals("1.0 0.0000 0.1000 0 App.handle self 0.0130", all.line("1.0 "));
    assertEquals("1.1 0.0010 0.0300 1 App.query", all.line("1.1 "));
    assertEquals("1.4 0.0440 0.0030 2 App.fib self 0.0020", all.line("1.4 "));
    assertEquals("1.5 0.0450 0.0010 3 App.fib", all.line("1.5 "));

    Cli.Result floored = Cli.vbtm("tree", t, "1", "--floor", "15us");
    assertTrue(floored.out().contains("floor: 15us  depth: all"), floored.out());
    assertEquals(
        "- 0.0440 0.0030 2 ·1 call of 1.3 < 15us, 2 incl. nested: App.fib",
        floored.line("- 0.0440"));
    assertEquals("- 0.0320 0.0100 1 ·1 call of 1.0 < 15us: App.query", floored.line("- 0.0320"));
    assertFalse(
        floored.out().contains("1.2 "), "the short second query is folded: " + floored.out());
  }

  @Test
  void theChosenFloorKeepsALongCallThatComesAfterALoop() throws IOException {
    TraceBuilder.Payload p = new TraceBuilder.Payload(0).enter(0, 0);
    @Var long t = 10;
    for (int step = 0; step < 40; step++) {
      p.enter(t, 1);
      for (int query = 0; query < 5; query++) {
        p.enter(t + 2, 2);
        p.enter(t + 4, 3).exit(t + 7);
        p.enter(t + 8, 3).exit(t + 11);
        p.exit(t + 70);
        t += 72;
      }
      p.exit(t + 5);
      t += 10;
    }
    p.enter(t, 4).exit(t + 300_000).exit(t + 300_010);
    Path loop =
        TestTraces.write(
            dir,
            "loop.vbtm",
            new TraceBuilder(1_700_000_000_000L, 0)
                .thread(1, "http-1")
                .clazz(
                    0,
                    "com.example.App",
                    "handle()V",
                    "step()V",
                    "query()V",
                    "bind()V",
                    "render()V")
                .chunk(1, 0, p.bytes(), true)
                .end()
                .bytes());

    Cli.Result r = Cli.vbtm("tree", loop, "1");
    assertEquals(0, r.code(), r.err());
    assertTrue(
        r.line("1.641 ").endsWith(" 30.0000 1 App.render"),
        "the 30 ms render after 640 short calls is the longest child and must be printed: "
            + r.out());
    assertTrue(printedLines(r) <= TreeCommand.DEFAULT_LIMIT, r.out());
    assertFalse(r.out().contains("more lines"), r.out());
  }

  @Test
  void theChosenFloorPrintsAsManyOfTheLongestCallsAsTheLimitAllows() throws IOException {
    TraceBuilder.Payload p = new TraceBuilder.Payload(0).enter(0, 0);
    @Var long t = 1;
    for (int dur = 1; dur <= 300; dur++) {
      p.enter(t, 1).exit(t + dur);
      t += dur + 1;
    }
    p.exit(t);
    Path flat =
        TestTraces.write(
            dir,
            "flat.vbtm",
            new TraceBuilder(1_700_000_000_000L, 0)
                .thread(1, "http-1")
                .clazz(0, "com.example.App", "handle()V", "query()V")
                .chunk(1, 0, p.bytes(), true)
                .end()
                .bytes());

    Cli.Result r = Cli.vbtm("tree", flat, "1");
    assertEquals(0, r.code(), r.err());
    assertEquals(
        TreeCommand.DEFAULT_LIMIT,
        printedLines(r),
        "the root, its fold and the 198 longest queries fill the limit: " + r.out());
    assertEquals("floor: 10.3us, chosen to fit --limit 200  depth: all", r.line("floor:"));
    assertTrue(r.line("1.103 ").endsWith(" 0.0103 1 App.query"), r.out());
    assertFalse(r.out().contains("1.102 "), r.out());
    assertTrue(r.line("- ").contains(" ·102 calls of 1.0 < 10.3us: App.query"), r.out());
  }

  @Test
  void atPrintsThePathAndOnlyTheSubtree() throws IOException {
    Cli.Result r = Cli.vbtm("tree", TestTraces.trace(dir), "--at", "1.3", "--depth", "1");
    assertEquals(0, r.code(), r.err());
    assertEquals("path: 1.0 App.handle > 1.3 App.render", r.line("path:"));
    assertEquals("1.4 0.0440 0.0030 2 App.fib self 0.0020", r.line("1.4 "));
    assertEquals("- 0.0450 0.0010 3 ·1 call of 1.4 deeper than --depth: App.fib", r.line("- "));
    assertFalse(r.out().contains("1.1 "), "calls outside the subtree are not printed: " + r.out());
    Cli.Result missing = Cli.vbtm("tree", TestTraces.trace(dir), "--at", "1.99");
    assertEquals(1, missing.code());
    assertTrue(missing.err().contains("no call 1.99 in session 1"), missing.err());
  }

  @Test
  void throwsAndOpenCallsAreMarked() throws IOException {
    Path t = TestTraces.trace(dir);
    Cli.Result thrown = Cli.vbtm("tree", t, "2");
    assertTrue(thrown.out().contains("exceptions: e1 = java.sql.SQLException"), thrown.out());
    assertEquals("2.1 0.0010 0.0090 1 App.query !e1", thrown.line("2.1 "));
    assertTrue(thrown.out().contains("gc: 1 pause overlap"), thrown.out());
    Cli.Result open = Cli.vbtm("tree", t, "3");
    assertEquals("3.1 0.0010 0.0000 1 App.query ~", open.line("3.1 "));
    assertTrue(open.out().contains("[unclosed]"), open.out());
  }

  @Test
  void mergeSumsCallsOnTheSamePath() throws IOException {
    Cli.Result r = Cli.vbtm("tree", TestTraces.trace(dir), "1", "--merge");
    assertEquals(0, r.code(), r.err());
    assertEquals("0.0400 2 0.0400 1 App.query [slowest 1.1 0.0300]", r.line("0.0400 2"));
    assertEquals("0.0030 1 0.0020 2 App.fib [1.4]", r.line("0.0030 1"));
  }

  @Test
  void mergeKeepsALongPathThatComesAfterABigSubtree() throws IOException {
    TraceBuilder.Payload p = new TraceBuilder.Payload(0).enter(0, 0).enter(10, 1);
    List<String> methods = new ArrayList<>(List.of("handle()V", "load()V", "leaf()V"));
    @Var long t = 20;
    for (int i = 0; i < 300; i++) {
      p.enter(t, 3 + i).enter(t + 2, 2).exit(t + 5).exit(t + 1000 + i);
      t += 1010 + i;
      methods.add("step" + i + "()V");
    }
    methods.add("render()V");
    p.exit(t).enter(t + 10, 303).exit(t + 300_010).exit(t + 300_100);
    Path big =
        TestTraces.write(
            dir,
            "big.vbtm",
            new TraceBuilder(1_700_000_000_000L, 0)
                .thread(1, "main")
                .clazz(0, "com.example.App", methods.toArray(String[]::new))
                .chunk(1, 0, p.bytes(), true)
                .end()
                .bytes());

    Cli.Result r = Cli.vbtm("tree", big, "1", "--merge");
    assertEquals(0, r.code(), r.err());
    assertEquals(
        "30.0000 1 30.0000 1 App.render [1.602]",
        r.line("30.0000 1 "),
        "the 30 ms render is the second longest path and must not be crowded out by the 300 paths"
            + " under load");
    assertTrue(
        r.lines().stream().filter(l -> !l.isEmpty() && Character.isDigit(l.charAt(0))).count()
            <= TreeCommand.DEFAULT_LIMIT,
        r.out());
    assertFalse(r.out().contains("more lines"), r.out());
  }

  @Test
  void mergePrintsTheRootPathEvenWhenTheFloorIsLongerThanTheSession() throws IOException {
    Cli.Result r = Cli.vbtm("tree", TestTraces.trace(dir), "1", "--merge", "--floor", "1s");
    assertEquals(0, r.code(), r.err());
    assertEquals("0.1000 1 0.0130 0 App.handle [1.0]", r.line("0.1000 "));
    assertTrue(
        r.line("0.0870 ").startsWith("0.0870 3 - 1 ·2 paths under App.handle < 1s: "),
        "the paths below the floor are folded, so the session does not look empty: " + r.out());
  }

  @Test
  void theExceptionsLegendCountsThrowsInThePrintedScope() throws IOException {
    Path e = TestTraces.throwsTrace(dir);
    Cli.Result whole = Cli.vbtm("tree", e, "1", "--floor", "1ms");
    assertEquals(0, whole.code(), whole.err());
    assertFalse(whole.out().contains("1.2 "), whole.out());
    assertEquals(
        "exceptions: e1 = java.sql.SQLException ×3; e2 = java.io.IOException ×1;"
            + " e3 = java.lang.IllegalStateException ×1",
        whole.line("exceptions:"),
        "throws below the floor are still counted");
    assertEquals(
        "exceptions: e1 = java.sql.SQLException ×2",
        Cli.vbtm("tree", e, "--at", "1.3").line("exceptions:"));
    assertEquals(
        "exceptions: e2 = java.io.IOException ×1; e3 = java.lang.IllegalStateException ×1",
        Cli.vbtm("tree", e, "--at", "1.6").line("exceptions:"));
    assertTrue(
        Cli.vbtm("tree", e, "1", "--json")
            .out()
            .contains(
                "{\"type\":\"exception\",\"id\":\"e1\",\"class\":\"java.sql.SQLException\",\"thrown\":3}"));
    assertEquals(
        "exceptions: e1 = java.sql.SQLException ×2",
        Cli.vbtm("tree", e, "--at", "1.3", "--merge").line("exceptions:"));
  }

  private static long printedLines(Cli.Result r) {
    return r.lines().stream().filter(l -> l.startsWith("1.") || l.startsWith("- ")).count();
  }
}
