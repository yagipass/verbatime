package io.github.yagipass.verbatime.format;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ReaderBudgetTest {

  @Test
  void aRejectedAllocationDoesNotConsumeTheRemainingAllowance() {
    ReaderBudget budget = new ReaderBudget(1024);
    budget.reserve(768, "sessions");
    ReadLimitException failure =
        assertThrows(ReadLimitException.class, () -> budget.reserve(257, "chunks"));
    assertTrue(
        failure.getMessage().contains("chunks"), "the error identifies the limiting resource");
    assertDoesNotThrow(() -> budget.reserve(256, "chunks"), "the exact boundary remains usable");
    assertThrows(ReadLimitException.class, () -> budget.reserve(1, "sessions"));
  }

  @Test
  void aSnapshotInheritsUsageWithoutSpendingTheLiveReadersAllowance() {
    ReaderBudget live = new ReaderBudget(1024);
    live.reserve(512, "sessions");
    ReaderBudget snapshot = live.copy();
    snapshot.reserve(512, "call depth");
    assertThrows(ReadLimitException.class, () -> snapshot.reserve(1, "call depth"));
    assertDoesNotThrow(
        () -> live.reserve(512, "chunks"),
        "snapshot-only work does not consume the live allowance");
    assertThrows(ReadLimitException.class, () -> live.reserve(1, "chunks"));
  }

  @Test
  void replacingARecordingDiscardsItsPreviousUsage() {
    ReaderBudget budget = new ReaderBudget(1024);
    budget.reserve(1024, "sessions");
    budget.reset();
    assertDoesNotThrow(() -> budget.reserve(1024, "threads"));
    assertThrows(ReadLimitException.class, () -> budget.reserve(1, "threads"));
  }

  @Test
  void largeReservationsCannotOverflowAndBypassTheLimit() {
    ReaderBudget budget = new ReaderBudget(Long.MAX_VALUE);
    budget.reserve(Long.MAX_VALUE - 1, "sessions");
    assertThrows(ReadLimitException.class, () -> budget.reserve(2, "chunks"));
    assertDoesNotThrow(() -> budget.reserve(1, "chunks"));
  }

  @Test
  void invalidBudgetsAndReservationsCannotCreateMoreAllowance() {
    assertThrows(IllegalArgumentException.class, () -> new ReaderBudget(0));
    assertThrows(IllegalArgumentException.class, () -> new ReaderBudget(-1));
    ReaderBudget budget = new ReaderBudget(1);
    assertThrows(IllegalArgumentException.class, () -> budget.reserve(-1, "sessions"));
    budget.reserve(1, "sessions");
    assertThrows(ReadLimitException.class, () -> budget.reserve(1, "sessions"));
  }
}
