package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FindCommandTest {

    @TempDir
    Path dir;

    @Test
    void callsAreListedSlowestFirst() throws IOException {
        final Path t = TestTraces.trace(dir);
        final Cli.Result r = Cli.vbtm("find", t, "App::query");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.line("matches:").startsWith("matches: 4 calls, 0.0490 ms in total"), r.out());
        final List<String> rows = r.rowsAfter(" id");
        assertTrue(rows.get(0).matches(" *1\\.1 +0\\.0010 +0\\.0300 .* App\\.query +App\\.handle$"), rows.get(0));
        assertTrue(rows.get(2).contains("App.handle#2  !SQLException"), rows.get(2));
        assertTrue(rows.get(3).endsWith("~"), "the call still open when the recording ended: " + rows.get(3));
        final Cli.Result min = Cli.vbtm("find", t, "query", "--min", "0.01ms", "--sort", "start");
        assertTrue(min.line("matches:").startsWith("matches: 2 calls >= 10us"), min.out());
    }

    @Test
    void thrownKeepsOnlyTheCallsThatThrew() throws IOException {
        final Path e = TestTraces.throwsTrace(dir);
        assertTrue(Cli.vbtm("find", e, "com.example.Svc::query").line("matches:").startsWith("matches: 4 calls,"));
        final Cli.Result thrown = Cli.vbtm("find", e, "com.example.Svc::query", "--thrown");
        assertEquals(0, thrown.code(), thrown.err());
        assertTrue(thrown.line("matches:").startsWith("matches: 3 calls that threw, 0.0080 ms in total"), thrown.out());
        assertFalse(thrown.out().contains("1.9 "), "the query that returned is left out: " + thrown.out());
        final Cli.Result min = Cli.vbtm("find", e, "com.example.Svc::query", "--thrown", "--min", "0.0025ms");
        assertTrue(min.line("matches:").startsWith("matches: 2 calls >= 2.5us that threw"), min.out());
        final Cli.Result old = Cli.vbtm("find", TestTraces.trace(dir), "App::query", "--thrown");
        assertTrue(old.line("matches:").startsWith("matches: 1 calls that threw"), old.out());
        assertTrue(old.line("2.1 ").contains("!SQLException"), old.out());
    }
}
