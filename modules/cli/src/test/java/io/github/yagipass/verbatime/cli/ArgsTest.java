package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yagipass.verbatime.format.Vbtm;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ArgsTest {

  @Test
  void durationsParseToTicks() {
    assertEquals(10_000, Args.parseTicks("x", "1ms"));
    assertEquals(10_000, Args.parseTicks("x", "1"), "a bare number is ms");
    assertEquals(5_000, Args.parseTicks("x", "500us"));
    assertEquals(5_000, Args.parseTicks("x", "0.5ms"));
    assertEquals(20_000_000, Args.parseTicks("x", "2s"));
    assertEquals(1, Args.parseTicks("x", "1ns"), "a fraction of a tick rounds up to one tick");
    assertThrows(CliException.class, () -> Args.parseTicks("x", "fast"));
    assertThrows(CliException.class, () -> Args.parseTicks("x", "3h"));
  }

  @Test
  void aDurationLongerThanARecordingCanHoldIsRefused() {
    assertEquals(Vbtm.MAX_TICKS, Args.parseTicks("x", Formats.duration(Vbtm.MAX_TICKS)));
    for (String tooLong : List.of(Formats.duration(Vbtm.MAX_TICKS + 1), "1000000000000s")) {
      CliException e =
          assertThrows(
              CliException.class,
              () -> Args.parseTicks("--floor", tooLong),
              "no call is that long, and tree would print a next hint that overflows");
      assertEquals(CliException.USAGE, e.exitCode());
      assertTrue(
          e.getMessage().contains("no longer than a recording can hold"),
          "the value is a duration, just too long: " + e.getMessage());
    }
  }
}
