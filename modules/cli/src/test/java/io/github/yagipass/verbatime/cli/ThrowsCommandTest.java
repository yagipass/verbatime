package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ThrowsCommandTest {

    @TempDir
    Path dir;

    @Test
    void eachPropagatingExceptionCountsOnce() throws IOException {
        final Cli.Result r = Cli.vbtm("throws", TestTraces.throwsTrace(dir));
        assertEquals(0, r.code(), r.err());
        assertEquals("scope: all 2 sessions, 7 throws, 4 exception classes, 0.0270 ms in the calls that threw",
                r.line("scope:"));
        assertEquals("by class, thrower and catcher, sorted by total, showing 6. units: ms", r.line("by "));
        final List<String> rows = r.rowsAfter("calls");
        assertTrue(rows.get(0).matches(" *1 +0\\.0080 +2\\.2 +Svc\\.query#2 +- +java\\.sql\\.SQLException"),
                "an exception that leaves the root has no catcher: " + rows.get(0));
        assertTrue(rows.get(1).matches(" *1 +0\\.0070 +1\\.6 +Svc\\.parse +Svc\\.handle +java\\.lang\\.IllegalStateException"),
                rows.get(1));
        assertTrue(rows.get(2).matches(" *2 +0\\.0050 +1\\.5 +Svc\\.query +Svc\\.retry +java\\.sql\\.SQLException"), rows.get(2));
        assertTrue(rows.get(3).matches(" *1 +0\\.0030 +1\\.2 +Svc\\.query +Svc\\.handle +java\\.sql\\.SQLException"), rows.get(3));
        assertTrue(rows.get(4).matches(" *1 +0\\.0030 +1\\.7 +Svc\\.read +Svc\\.parse +java\\.io\\.IOException"), rows.get(4));
        assertTrue(rows.get(5).matches(" *1 +0\\.0010 +1\\.8 +Svc\\.log +Svc\\.handle +unknown"),
                "an exception id the recording does not name prints as unknown: " + rows.get(5));
        assertTrue(r.out().contains("Svc.query#2 = com.other.Svc.query()V"), r.out());
        final Cli.Result old = Cli.vbtm("throws", TestTraces.trace(dir));
        assertEquals("scope: all 3 sessions, 1 throw, 1 exception class, 0.0090 ms in the calls that threw",
                old.line("scope:"));
        assertTrue(old.out().contains("1    0.0090      2.1  App.query  -        java.sql.SQLException"), old.out());
        final Cli.Result none = Cli.vbtm("throws", TestTraces.trace(dir), "1");
        assertEquals("no throws in session 1", none.line("no throws"));
    }

    @Test
    void rowsCollapseByClassThrowerOrCatcher() throws IOException {
        final Path e = TestTraces.throwsTrace(dir);
        final Cli.Result byClass = Cli.vbtm("throws", e, "--by", "class");
        assertEquals(0, byClass.code(), byClass.err());
        assertTrue(byClass.line("    4 ").matches(" *4 +0\\.0160 +\\d\\.\\d +java\\.sql\\.SQLException"), byClass.out());
        assertTrue(byClass.out().contains("0.0010      1.8  unknown"), byClass.out());
        assertFalse(byClass.out().contains("thrower"), "the thrower column is dropped: " + byClass.out());
        final Cli.Result byThrower = Cli.vbtm("throws", e, "--by", "thrower");
        final List<String> rows = byThrower.rowsAfter("calls");
        assertTrue(rows.get(0).matches(" *3 +0\\.0080 +1\\.\\d +Svc\\.query"), rows.get(0));
        assertTrue(rows.get(1).matches(" *1 +0\\.0080 +2\\.2 +Svc\\.query#2"), rows.get(1));
        final Cli.Result byCatcher = Cli.vbtm("throws", e, "--by", "catcher");
        assertTrue(byCatcher.line("    3 ").matches(" *3 +0\\.0110 +1\\.6 +Svc\\.handle"), byCatcher.out());
        assertTrue(byCatcher.line("    1 ").matches(" *1 +0\\.0080 +2\\.2 +-"), byCatcher.out());
        final Cli.Result byCalls = Cli.vbtm("throws", e, "--sort", "calls", "--limit", "2");
        final List<String> top = byCalls.rowsAfter("calls");
        assertTrue(top.get(0).matches(" *2 +0\\.0050 +1\\.5 +Svc\\.query +Svc\\.retry .*"), top.get(0));
        assertEquals("# 4 more rows. next: vbtm throws " + e + " --sort calls --limit 6", byCalls.line("# 4 more"));
    }
}
