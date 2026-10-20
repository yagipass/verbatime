package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.github.yagipass.verbatime.format.Vbtm;

final class ArgsFuzzTest {

  @FuzzTest
  void aDurationIsRefusedUnlessARecordingCanHoldIt(FuzzedDataProvider data) {
    String text = data.consumeRemainingAsString();
    long ticks;
    try {
      ticks = Args.parseTicks("--floor", text);
    } catch (CliException e) {
      assertEquals(CliException.USAGE, e.exitCode(), e.getMessage());
      return;
    }
    assertTrue(
        ticks >= 0 && ticks <= Vbtm.MAX_TICKS,
        "'" + text + "' is accepted as " + ticks + " ticks, longer than a recording can hold");
  }
}
