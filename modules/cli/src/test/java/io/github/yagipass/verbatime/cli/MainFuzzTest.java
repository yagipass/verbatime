package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.github.yagipass.verbatime.format.Seeds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class MainFuzzTest {

  private static final List<byte[]> SEEDS = Seeds.recordings();

  private static final List<String> COMMANDS =
      List.of("sessions", "hot", "throws", "tree", "find", "callers");

  private static final List<String> WORDS =
      List.of(
          "--json",
          "--merge",
          "--all",
          "--thrown",
          "--at",
          "--depth",
          "--floor",
          "--limit",
          "--session",
          "--min",
          "--sort",
          "--by",
          "--root",
          "--thread",
          "1",
          "2",
          "#1",
          "1.0",
          "1.3",
          "2.1",
          "0",
          "-1",
          "2147483648",
          "dur",
          "start",
          "calls",
          "self",
          "total",
          "Main",
          "Main.run",
          "Main.work#2",
          "a.Main::run",
          "::",
          "run",
          "worker",
          "1ns",
          "500us",
          "1ms",
          "2s",
          "1.5");

  @FuzzTest
  void anyArgumentsOnAnyBytesEndWithADocumentedExitCodeAndJsonLines(FuzzedDataProvider data)
      throws IOException {
    String command = data.pickValue(COMMANDS);
    boolean json = data.consumeBoolean();
    List<String> rest = new ArrayList<>();
    int words = data.consumeInt(0, 6);
    for (int k = 0; k < words; k++) {
      rest.add(data.consumeBoolean() ? data.pickValue(WORDS) : data.consumeString(16));
    }
    Path file =
        TraceFileFuzzTest.write(
            data.consumeBoolean() ? data.pickValue(SEEDS) : data.consumeRemainingAsBytes());
    try {
      List<Object> argv = new ArrayList<>(List.of(command, file));
      if (json) {
        argv.add("--json");
      }
      argv.addAll(rest);
      Cli.Result r = Cli.vbtm(argv.toArray());
      assertTrue(
          r.code() >= 0 && r.code() <= CliException.CORRUPT,
          "vbtm " + argv + " exits with " + r.code() + ": " + r.err());
      if (json && !rest.contains("--help") && !rest.contains("-h")) {
        for (String line : r.lines()) {
          if (!line.isEmpty()) {
            StrictJson.requireObject(line);
          }
        }
      }
    } finally {
      Files.delete(file);
    }
  }
}
