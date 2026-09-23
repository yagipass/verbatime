package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TreeCommandTest {

    @TempDir
    Path dir;

    @Test
    void everyCallIsPrintedWithItsIdAndCallsBelowTheFloorAreFolded() throws IOException {
        final Path t = TestTraces.trace(dir);
        final Cli.Result all = Cli.vbtm("tree", t, "1");
        assertEquals(0, all.code(), all.err());
        assertTrue(all.out().contains("floor: none, every call is printed"), all.out());
        assertEquals("1.0 0.0000 0.1000 0 App.handle self 0.0130", all.line("1.0 "));
        assertEquals("1.1 0.0010 0.0300 1 App.query", all.line("1.1 "));
        assertEquals("1.4 0.0440 0.0030 2 App.fib self 0.0020", all.line("1.4 "));
        assertEquals("1.5 0.0450 0.0010 3 App.fib", all.line("1.5 "));

        final Cli.Result floored = Cli.vbtm("tree", t, "1", "--floor", "15us");
        assertTrue(floored.out().contains("floor: 15us  depth: all"), floored.out());
        assertEquals("- 0.0440 0.0030 2 ·1 call of 1.3 < 15us, 2 incl. nested: App.fib", floored.line("- 0.0440"));
        assertEquals("- 0.0320 0.0100 1 ·1 call of 1.0 < 15us: App.query", floored.line("- 0.0320"));
        assertFalse(floored.out().contains("1.2 "), "the short second query is folded: " + floored.out());
    }

    @Test
    void atPrintsThePathAndOnlyTheSubtree() throws IOException {
        final Cli.Result r = Cli.vbtm("tree", TestTraces.trace(dir), "--at", "1.3", "--depth", "1");
        assertEquals(0, r.code(), r.err());
        assertEquals("path: 1.0 App.handle > 1.3 App.render", r.line("path:"));
        assertEquals("1.4 0.0440 0.0030 2 App.fib self 0.0020", r.line("1.4 "));
        assertEquals("- 0.0450 0.0010 3 ·1 call of 1.4 deeper than --depth: App.fib", r.line("- "));
        assertFalse(r.out().contains("1.1 "), "calls outside the subtree are not printed: " + r.out());
        final Cli.Result missing = Cli.vbtm("tree", TestTraces.trace(dir), "--at", "1.99");
        assertEquals(1, missing.code());
        assertTrue(missing.err().contains("no call 1.99 in session 1"), missing.err());
    }

    @Test
    void throwsAndOpenCallsAreMarked() throws IOException {
        final Path t = TestTraces.trace(dir);
        final Cli.Result thrown = Cli.vbtm("tree", t, "2");
        assertTrue(thrown.out().contains("exceptions: e1 = java.sql.SQLException"), thrown.out());
        assertEquals("2.1 0.0010 0.0090 1 App.query !e1", thrown.line("2.1 "));
        assertTrue(thrown.out().contains("gc: 1 pause overlap"), thrown.out());
        final Cli.Result open = Cli.vbtm("tree", t, "3");
        assertEquals("3.1 0.0010 0.0000 1 App.query ~", open.line("3.1 "));
        assertTrue(open.out().contains("[unclosed]"), open.out());
    }

    @Test
    void mergeSumsCallsOnTheSamePath() throws IOException {
        final Cli.Result r = Cli.vbtm("tree", TestTraces.trace(dir), "1", "--merge");
        assertEquals(0, r.code(), r.err());
        assertEquals("0.0400 2 0.0400 1 App.query [slowest 1.1 0.0300]", r.line("0.0400 2"));
        assertEquals("0.0030 1 0.0020 2 App.fib [1.4]", r.line("0.0030 1"));
    }

    @Test
    void theExceptionsLegendCountsThrowsInThePrintedScope() throws IOException {
        final Path e = TestTraces.throwsTrace(dir);
        final Cli.Result whole = Cli.vbtm("tree", e, "1", "--floor", "1ms");
        assertEquals(0, whole.code(), whole.err());
        assertFalse(whole.out().contains("1.2 "), whole.out());
        assertEquals("exceptions: e1 = java.sql.SQLException ×3; e2 = java.io.IOException ×1;"
                + " e3 = java.lang.IllegalStateException ×1", whole.line("exceptions:"),
                "throws below the floor are still counted");
        assertEquals("exceptions: e1 = java.sql.SQLException ×2",
                Cli.vbtm("tree", e, "--at", "1.3").line("exceptions:"));
        assertEquals("exceptions: e2 = java.io.IOException ×1; e3 = java.lang.IllegalStateException ×1",
                Cli.vbtm("tree", e, "--at", "1.6").line("exceptions:"));
        assertTrue(Cli.vbtm("tree", e, "1", "--json").out()
                .contains("{\"type\":\"exception\",\"id\":\"e1\",\"class\":\"java.sql.SQLException\",\"thrown\":3}"));
        assertEquals("exceptions: e1 = java.sql.SQLException ×2",
                Cli.vbtm("tree", e, "--at", "1.3", "--merge").line("exceptions:"));
    }
}
