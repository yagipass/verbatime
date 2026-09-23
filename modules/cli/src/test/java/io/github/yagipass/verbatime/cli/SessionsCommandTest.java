package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SessionsCommandTest {

    @TempDir
    Path dir;

    @Test
    void everySessionIsSummarized() throws IOException {
        final Cli.Result r = Cli.vbtm("sessions", TestTraces.trace(dir));
        assertEquals(0, r.code(), r.err());
        assertTrue(r.line("file: t.vbtm").contains("status: complete"));
        assertTrue(r.line("length:").contains("sessions: 3  calls: 10"), r.out());
        assertTrue(r.out().contains("recorded: 2023-11-15T07:13:20.000+09:00"), r.out());
        final String s1 = r.line(" 1 ");
        assertTrue(s1.contains("0.1000") && s1.contains("App.handle"), s1);
        final String s2 = r.line(" 2 ");
        assertTrue(s2.matches(".* 1 +0\\.0020 +http-2 +App\\.handle#2$"), "the throw and the GC time are counted: " + s2);
        assertTrue(r.line(" 3 ").endsWith("App.handle ~"), "a session still open is marked: " + r.out());
        assertTrue(r.out().contains("App.handle#2 = com.other.App.handle()V"), "colliding names get a legend: " + r.out());
    }

    @Test
    void sessionsAreSortedFilteredAndCapped() throws IOException {
        final Path t = TestTraces.trace(dir);
        final Cli.Result r = Cli.vbtm("sessions", t, "--sort", "dur", "--limit", "1");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.line(" 1 ").contains("http-1"), r.out());
        assertEquals("# 2 more sessions. next: vbtm sessions " + t + " --sort dur --limit 3", r.line("# 2 more"));
        final Cli.Result filtered = Cli.vbtm("sessions", t, "--root", "com.other.App::handle");
        assertTrue(filtered.out().contains("1 of 3 sessions match"), filtered.out());
        final Cli.Result json = Cli.vbtm("sessions", t, "--json", "--thread", "http-1");
        assertEquals(4, json.lines().size(), "the file, two sessions and one legend name: " + json.out());
        assertTrue(json.lines().get(3).startsWith("{\"type\":\"name\""), json.out());
        assertTrue(json.lines().get(0).startsWith("{\"type\":\"file\",\"file\":\"t.vbtm\""), json.out());
        assertTrue(json.lines().get(2).contains("\"id\":3,") && json.lines().get(2).contains("\"unclosed\":true"),
                json.out());
    }

    @Test
    void throwCountsAgreeWithTheThrowsCommand() throws IOException {
        final Path e = TestTraces.throwsTrace(dir);
        final Cli.Result sessions = Cli.vbtm("sessions", e);
        assertTrue(sessions.line(" 1 ").matches(".* 10 +2 +6 +.*w-1 +Svc\\.handle$"), sessions.out());
        assertTrue(sessions.line(" 2 ").matches(".* 3 +2 +1 +.*w-2 +Svc\\.handle$"), sessions.out());
        long fromSessions = 0;
        for (final String l : Cli.vbtm("sessions", e, "--json").lines()) {
            if (l.startsWith("{\"type\":\"session\"")) {
                fromSessions += Long.parseLong(l.replaceAll(".*\"throws\":(\\d+).*", "$1"));
            }
        }
        long fromThrows = 0;
        for (final String l : Cli.vbtm("throws", e, "--json").lines()) {
            if (l.startsWith("{\"type\":\"throw\"")) {
                fromThrows += Long.parseLong(l.replaceAll(".*\"calls\":(\\d+).*", "$1"));
            }
        }
        assertEquals(7, fromSessions);
        assertEquals(fromSessions, fromThrows, "both count a propagating exception once");
    }
}
