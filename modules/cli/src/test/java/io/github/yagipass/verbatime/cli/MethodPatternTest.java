package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yagipass.verbatime.format.TraceBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MethodPatternTest {

  @TempDir Path dir;

  @Test
  void aPatternThatNamesSeveralMethodsIsRejectedUnlessAll() throws IOException {
    Path t = TestTraces.trace(dir);
    Cli.Result r = Cli.vbtm("find", t, "App::handle", "--session", "1");
    assertEquals(1, r.code());
    assertEquals(
        List.of(
            "vbtm: 'App::handle' matches 2 methods:",
            "  vbtm find " + t + " com.example.App::handle --session 1",
            "  vbtm find " + t + " com.other.App::handle --session 1",
            "hint: run one of these commands, or add --all to count them together"),
        r.err().lines().toList(),
        "each method comes as a command to run as printed, keeping the options given");
    for (String command : r.err().lines().filter(l -> l.startsWith("  vbtm ")).toList()) {
      List<String> argv = List.of(command.trim().split(" "));
      Cli.Result picked = Cli.vbtm(argv.subList(1, argv.size()).toArray());
      assertEquals(0, picked.code(), command + ": " + picked.err());
      assertTrue(
          picked.line("pattern:").contains(argv.get(3).replace("::", ".") + "()V"),
          command + " picks the method it names: " + picked.out());
    }
    assertEquals(0, Cli.vbtm("find", t, "App::handle", "--all").code());
    assertTrue(
        Cli.vbtm("find", t, "App.handle#2").line("matches:").startsWith("matches: 1 call"),
        "the numbered printed name picks one method");
    assertEquals(1, Cli.vbtm("find", t, "Nope::x").code());
  }

  @Test
  void aRecordedNameWithShellSyntaxIsPrintedAsOneQuotedWord() throws IOException {
    Path t =
        TestTraces.write(
            dir,
            "q.vbtm",
            new TraceBuilder(1_700_000_000_000L, 0)
                .clazz(0, "com.example.Dao", "q()V", "q';touch pwned;'()V")
                .end()
                .bytes());
    Cli.Result r = Cli.vbtm("callers", t, "Dao");
    assertEquals(1, r.code());
    assertTrue(
        r.err()
            .lines()
            .toList()
            .contains("  vbtm callers " + t + " 'com.example.Dao::q'\\'';touch pwned;'\\'''"),
        "a name comes from whoever made the recording, so pasting it must not run a command: "
            + r.err());
    assertEquals(
        0,
        Cli.vbtm("callers", t, "com.example.Dao::q';touch pwned;'").code(),
        "the word the shell passes on picks that method alone");
  }

  @Test
  void aMethodNoPatternNamesAloneIsListedWithoutACommand() throws IOException {
    Path t =
        TestTraces.write(
            dir,
            "d.vbtm",
            new TraceBuilder(1_700_000_000_000L, 0)
                .clazz(0, "App", "handle()V")
                .clazz(1, "com.example.App", "handle()V")
                .end()
                .bytes());
    Cli.Result r = Cli.vbtm("find", t, "App::handle");
    assertEquals(
        List.of(
            "vbtm: 'App::handle' matches 2 methods:",
            "  # App.handle (no pattern names it alone)",
            "  vbtm find " + t + " com.example.App::handle",
            "hint: run one of these commands, or add --all to count them together"),
        r.err().lines().toList(),
        "App::handle in the default package also matches com.example.App, so a command with it"
            + " would only print this list again");
  }

  @Test
  void aRecordedNameThatReadsLikeACommandIsListedAsAComment() throws IOException {
    Path t =
        TestTraces.write(
            dir,
            "c.vbtm",
            new TraceBuilder(1_700_000_000_000L, 0)
                .clazz(0, "com.example.App", "handle()V")
                .clazz(1, "vbtm find c.vbtm com.example.App::handle;touch x;#", "handle()V")
                .end()
                .bytes());
    Cli.Result r = Cli.vbtm("find", t, "handle");
    assertEquals(1, r.code());
    assertEquals(
        List.of(
            "vbtm: 'handle' matches 2 methods:",
            "  vbtm find " + t + " com.example.App::handle",
            "  # vbtm find c.vbtm com.example.App::handle;touch x;#.handle (no pattern names it"
                + " alone)",
            "hint: run one of these commands, or add --all to count them together"),
        r.err().lines().toList(),
        "a class name comes from whoever made the recording, and the '#' in it leaves the method"
            + " without a command, so its line must be one a shell skips when run as printed");
  }
}
