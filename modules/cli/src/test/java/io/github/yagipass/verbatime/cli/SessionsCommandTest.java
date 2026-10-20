package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.TraceBuilder;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SessionsCommandTest {

  @TempDir Path dir;

  @Test
  void everySessionIsSummarized() throws IOException {
    Cli.Result r = Cli.vbtm("sessions", TestTraces.trace(dir));
    assertEquals(0, r.code(), r.err());
    assertTrue(r.line("file: t.vbtm").contains("status: complete"));
    assertTrue(r.line("length:").contains("sessions: 3  calls: 10"), r.out());
    assertTrue(r.out().contains("recorded: 2023-11-15T07:13:20.000+09:00"), r.out());
    String s1 = r.line(" 1 ");
    assertTrue(s1.contains("0.1000") && s1.contains("App.handle"), s1);
    String s2 = r.line(" 2 ");
    assertTrue(
        s2.matches(".* 1 +0\\.0020 +http-2 +App\\.handle#2$"),
        "the throw and the GC time are counted: " + s2);
    assertTrue(
        r.line(" 3 ").endsWith("App.handle ~"), "a session still open is marked: " + r.out());
    assertTrue(
        r.out().contains("App.handle#2 = com.other.App.handle()V"),
        "colliding names get a legend: " + r.out());
  }

  @Test
  void sessionsAreSortedFilteredAndCapped() throws IOException {
    Path t = TestTraces.trace(dir);
    Cli.Result r = Cli.vbtm("sessions", t, "--sort", "dur", "--limit", "1");
    assertEquals(0, r.code(), r.err());
    assertTrue(r.line(" 1 ").contains("http-1"), r.out());
    assertEquals(
        "# 2 more sessions. next: vbtm sessions " + t + " --sort dur --limit 3",
        r.line("# 2 more"));
    Cli.Result filtered = Cli.vbtm("sessions", t, "--root", "com.other.App::handle");
    assertTrue(filtered.out().contains("1 of 3 sessions match"), filtered.out());
    Cli.Result json = Cli.vbtm("sessions", t, "--json", "--thread", "http-1");
    assertEquals(
        4, json.lines().size(), "the file, two sessions and one legend name: " + json.out());
    assertTrue(json.lines().get(3).startsWith("{\"type\":\"name\""), json.out());
    assertTrue(
        json.lines().get(0).startsWith("{\"type\":\"file\",\"file\":\"t.vbtm\""), json.out());
    assertTrue(
        json.lines().get(2).contains("\"id\":3,")
            && json.lines().get(2).contains("\"unclosed\":true"),
        json.out());
  }

  @Test
  void throwCountsAgreeWithTheThrowsCommand() throws IOException {
    Path e = TestTraces.throwsTrace(dir);
    Cli.Result sessions = Cli.vbtm("sessions", e);
    assertTrue(sessions.line(" 1 ").matches(".* 10 +2 +6 +.*w-1 +Svc\\.handle$"), sessions.out());
    assertTrue(sessions.line(" 2 ").matches(".* 3 +2 +1 +.*w-2 +Svc\\.handle$"), sessions.out());
    @Var long fromSessions = 0;
    for (String l : Cli.vbtm("sessions", e, "--json").lines()) {
      if (l.startsWith("{\"type\":\"session\"")) {
        fromSessions += Long.parseLong(l.replaceAll(".*\"throws\":(\\d+).*", "$1"));
      }
    }
    @Var long fromThrows = 0;
    for (String l : Cli.vbtm("throws", e, "--json").lines()) {
      if (l.startsWith("{\"type\":\"throw\"")) {
        fromThrows += Long.parseLong(l.replaceAll(".*\"calls\":(\\d+).*", "$1"));
      }
    }
    assertEquals(7, fromSessions);
    assertEquals(fromSessions, fromThrows, "both count a propagating exception once");
  }

  @Test
  void aSessionStartsAtItsFirstChunkEvenWhenThatChunkHoldsNoEvents() throws IOException {
    String later =
        sessionJson(
            "later.vbtm",
            mainThread().chunk(1, 0, new byte[0], false).chunk(1, 16, runCallingWork(16), true));
    assertTrue(
        later.contains("\"start_ms\":0.0000,\"dur_ms\":0.0019"),
        "the session lasts from its empty first chunk, as the JMC plugin shows it: " + later);
    String earlier =
        sessionJson(
            "earlier.vbtm",
            mainThread().chunk(1, 16, new byte[0], false).chunk(1, 0, runCallingWork(0), true));
    assertTrue(
        earlier.contains("\"start_ms\":0.0016,\"dur_ms\":0.0003"),
        "a chunk that goes back before the session start is moved up to it: " + earlier);
  }

  @Test
  void noControlCharacterInARecordedNameReachesAnyOutput() throws IOException {
    TraceBuilder w =
        new TraceBuilder(1_700_000_000_000L, 0)
            .thread(1, "w\u001b]0;title\u0007\u001b[2K\tx\n\u009b" + (char) 0x202E + "1")
            .clazz(0, "a.Main", "r\u0001un()V", "work()V")
            .chunk(1, 0, runCallingWork(0), true);
    Path t = TestTraces.write(dir, "control.vbtm", w.end().bytes());
    String shown = "w\\x1b]0;title\\x07\\x1b[2K\\x09x\\x0a\\x9b\\u202e1";

    Cli.Result text = Cli.vbtm("sessions", t);
    assertNoControlCharacter(text);
    assertTrue(
        text.line(" 1 ").contains(shown) && text.line(" 1 ").contains("Main.r\\x01un"),
        "each character is shown, so names that differ only in them stay apart: " + text.out());
    Cli.Result json = Cli.vbtm("sessions", t, "--json");
    assertNoControlCharacter(json);
    assertTrue(
        json.out().contains("\"thread\":\"" + shown.replace("\\", "\\\\") + "\""),
        "JSON shows the name as the text output does: " + json.out());
    assertNoControlCharacter(Cli.vbtm("tree", t, "1"));
    assertTrue(
        Cli.vbtm("sessions", t, "--thread", shown).line(" 1 ").contains(shown),
        "the name as printed selects its thread again");
  }

  private static void assertNoControlCharacter(Cli.Result r) {
    String out = r.out();
    for (int i = 0; i < out.length(); i++) {
      char c = out.charAt(i);
      if ((c < 0x20 && c != '\n')
          || (c >= 0x7F && c <= 0x9F)
          || (c >= 0x2028 && c <= 0x202E)
          || (c >= 0x2066 && c <= 0x2069)) {
        fail(String.format("U+%04X at %d would reach the terminal as it is:%n%s", (int) c, i, out));
      }
    }
  }

  private static TraceBuilder mainThread() {
    return new TraceBuilder(1_700_000_000_000L, 0)
        .thread(1, "main")
        .clazz(0, "a.Main", "run()V", "work()V");
  }

  private static byte[] runCallingWork(long at) {
    return new TraceBuilder.Payload(at)
        .enter(at, 0)
        .enter(at + 1, 1)
        .exit(at + 2)
        .exit(at + 3)
        .bytes();
  }

  private String sessionJson(String name, TraceBuilder w) throws IOException {
    Cli.Result r = Cli.vbtm("sessions", TestTraces.write(dir, name, w.end().bytes()), "--json");
    assertEquals(0, r.code(), r.err());
    return r.line("{\"type\":\"session\"");
  }
}
