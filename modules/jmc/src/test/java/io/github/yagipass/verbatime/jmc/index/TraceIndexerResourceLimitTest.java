package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yagipass.verbatime.format.ReadLimitException;
import io.github.yagipass.verbatime.format.ReaderBudget;
import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer.ProgressListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TraceIndexerResourceLimitTest {

  private static final long EMPTY_SESSION_BYTES =
      ReaderBudget.THREAD_BYTES + ReaderBudget.SESSION_BYTES + ReaderBudget.CHUNK_BYTES;

  @Test
  void emptySessionsAreLimitedBeforeAnUnboundedIndexCanBeBuilt() throws IOException {
    TraceBuilder w = TestTraces.writer();
    w.chunk(1, 0, new byte[0], true).chunk(1, 0, new byte[0], true).end();
    Path f = write(w);
    TraceIndexer ix = TraceIndexer.open(f, 1, new ReaderBudget(EMPTY_SESSION_BYTES));
    try {
      ReadLimitException failure =
          assertThrows(ReadLimitException.class, () -> ix.advance(ProgressListener.NONE));
      assertTrue(failure.getMessage().contains("sessions"));
      assertSame(
          failure,
          assertThrows(ReadLimitException.class, () -> ix.advance(ProgressListener.NONE)),
          "retrying a rejected index must not resume inside the rejected record");
      assertSame(failure, assertThrows(ReadLimitException.class, ix::snapshot));
    } finally {
      ix.close();
    }
  }

  @Test
  void emptyEndChunksStillCloseSessionsAfterTheirEventsWereFlushed() throws IOException {
    TraceBuilder w = TestTraces.writer();
    byte[] events = new TraceBuilder.Payload(100).enter(100, 0).exit(120).bytes();
    w.chunk(1, 100, events, false).chunk(1, 0, new byte[0], true).end();
    long bytes =
        EMPTY_SESSION_BYTES
            + ReaderBudget.CHUNK_BYTES
            + ReaderBudget.FRAME_BYTES
            + ReaderBudget.CALL_BYTES;
    TraceSnapshot d = index(w, bytes);
    try {
      assertTrue(d.sessions.get(0).ended, "the empty chunk carries the real session end");
      assertEquals(1, d.totalCalls);
      assertEquals(2, d.threads.get(0).chunks.count);
      assertEquals(120, d.threads.get(0).chunks.baseTicks[1]);
    } finally {
      d.release();
    }
  }

  @Test
  void newThreadIdsCannotEachAllocateAnUnboundedSetOfIndexArrays() throws IOException {
    TraceBuilder w = TestTraces.writer();
    w.chunk(1, 0, new byte[0], true).chunk(2, 0, new byte[0], true).end();
    assertLimit(w, EMPTY_SESSION_BYTES, "threads");
  }

  @Test
  void threadDeclarationsAreLimitedEvenWithoutAnyCalls() throws IOException {
    TraceBuilder w = TestTraces.writer().thread(1, "first").thread(2, "second").end();
    assertLimit(w, ReaderBudget.THREAD_BYTES, "threads");
  }

  @Test
  void threadNamesAndChunksShareOneReservationForTheirThread() throws IOException {
    TraceBuilder w = TestTraces.writer().thread(1, "first");
    byte[] events = new TraceBuilder.Payload(0).enter(0, 0).exit(1).bytes();
    w.chunk(1, 0, events, true).thread(1, "renamed").end();
    TraceSnapshot d =
        index(w, EMPTY_SESSION_BYTES + ReaderBudget.FRAME_BYTES + ReaderBudget.CALL_BYTES);
    try {
      assertEquals("renamed", d.threadName(1));
      assertEquals(1, d.totalCalls, "declaring a thread must not reduce the room for its calls");
    } finally {
      d.release();
    }
  }

  @Test
  void gcPausesAreLimitedBecauseCompactionNeverDropsThem() throws IOException {
    TraceSnapshot d = index(TestTraces.writer().gc(0, 1, 0, "", "").end(), ReaderBudget.GC_BYTES);
    try {
      assertEquals(1, d.gc.count, "a recording at the budget boundary keeps its pause");
    } finally {
      d.release();
    }
    TraceBuilder w = TestTraces.writer().gc(0, 1, 0, "", "").gc(1, 1, 0, "", "").end();
    assertLimit(w, ReaderBudget.GC_BYTES, "gc pauses");
  }

  @Test
  void depthGrowthIsLimitedAcrossChunkBoundaries() throws IOException {
    TraceBuilder w = TestTraces.writer();
    byte[] enter = new TraceBuilder.Payload(0).enter(0, 0).bytes();
    w.chunk(1, 0, enter, false).chunk(1, 0, enter, false).chunk(1, 0, enter, false).end();
    long bytes =
        EMPTY_SESSION_BYTES + 2L * ReaderBudget.CHUNK_BYTES + 2L * ReaderBudget.FRAME_BYTES;
    assertLimit(w, bytes, "call depth");
  }

  @Test
  void aLargePayloadIsRejectedBeforeAllocatingItsDecodeBuffer() throws IOException {
    TraceBuilder w = TestTraces.writer().chunk(1, 0, new byte[(1 << 16) + 1], true).end();
    assertLimit(w, EMPTY_SESSION_BYTES, "chunk decode buffer");
  }

  @Test
  void equalDurationCallsCannotEscapeTheBudgetWhenCompactionKeepsThemAll() throws IOException {
    TraceBuilder.Payload p = new TraceBuilder.Payload(0);
    for (int i = 0; i < 4; i++) {
      p.enter(0, 0).exit(0);
    }
    TraceBuilder w = TestTraces.writer().chunk(1, 0, p.bytes(), true).end();
    long bytes = EMPTY_SESSION_BYTES + ReaderBudget.FRAME_BYTES + 3L * ReaderBudget.CALL_BYTES;
    assertLimit(w, bytes, "overview calls");
  }

  @Test
  void budgetPressureCompactsNormalCallsBeforeTheScheduledCompaction() throws IOException {
    TraceBuilder.Payload p = new TraceBuilder.Payload(0);
    for (int i = 0; i < 4; i++) {
      p.enter(i * 10L, 0).exit(i * 10L + i + 1);
    }
    TraceBuilder w = TestTraces.writer().chunk(1, 0, p.bytes(), true).end();
    long bytes = EMPTY_SESSION_BYTES + ReaderBudget.FRAME_BYTES + 3L * ReaderBudget.CALL_BYTES;
    TraceIndexer ix = TraceIndexer.open(write(w), 2, new ReaderBudget(bytes));
    try {
      ix.advance(ProgressListener.NONE);
      TraceSnapshot d = ix.snapshot();
      try {
        assertEquals(
            4, d.totalCalls, "reusable overview storage must not reject a normal recording");
        assertEquals(2, d.threads.get(0).overview.count);
        assertEquals(
            300,
            d.overviewThresholdNs,
            "early compaction must keep the same duration threshold as the requested overview");
      } finally {
        d.release();
      }
    } finally {
      ix.close();
    }
  }

  @Test
  void earlyCompactionCanOmitTheIncomingShortCallWithoutLosingItsStatistics() throws IOException {
    byte[] events =
        new TraceBuilder.Payload(0)
            .enter(0, 0)
            .exit(1)
            .enter(10, 0)
            .exit(12)
            .enter(20, 0)
            .exit(23)
            .enter(30, 0)
            .exit(30)
            .bytes();
    TraceBuilder w = TestTraces.writer().chunk(1, 0, events, true).end();
    long bytes = EMPTY_SESSION_BYTES + ReaderBudget.FRAME_BYTES + 3L * ReaderBudget.CALL_BYTES;
    TraceIndexer ix = TraceIndexer.open(write(w), 2, new ReaderBudget(bytes));
    try {
      ix.advance(ProgressListener.NONE);
      TraceSnapshot d = ix.snapshot();
      try {
        assertEquals(
            4, d.totalCalls, "overview filtering must not lose the recording's exact call count");
        assertEquals(4, d.sessions.get(0).callCount);
        assertEquals(2, d.threads.get(0).overview.count);
        assertEquals(200, d.overviewThresholdNs);
      } finally {
        d.release();
      }
    } finally {
      ix.close();
    }
  }

  @Test
  void earlyCompactionCannotDiscardUnclosedCallsBelowTheNewThreshold() throws IOException {
    byte[] events = new TraceBuilder.Payload(0).enter(0, 0).exit(10).enter(10, 0).bytes();
    TraceBuilder w = TestTraces.writer().chunk(1, 0, events, false).end();
    long bytes = EMPTY_SESSION_BYTES + ReaderBudget.FRAME_BYTES + ReaderBudget.CALL_BYTES;
    TraceIndexer ix = TraceIndexer.open(write(w), 1, new ReaderBudget(bytes));
    try {
      ix.advance(ProgressListener.NONE);
      ReadLimitException failure = assertThrows(ReadLimitException.class, ix::snapshot);
      assertTrue(
          failure.getMessage().contains("overview calls"),
          "unfinished calls must stay visible even when their duration is below the overview floor");
      assertSame(failure, assertThrows(ReadLimitException.class, ix::snapshot));
    } finally {
      ix.close();
    }
  }

  @Test
  void reusingDepthAndCompactedOverviewSpaceDoesNotChargeEveryCall() throws IOException {
    TraceBuilder.Payload p = new TraceBuilder.Payload(0);
    for (int i = 0; i < 100; i++) {
      p.enter(i * 100L, 0).exit(i * 100L + i + 1);
    }
    TraceBuilder w = TestTraces.writer().chunk(1, 0, p.bytes(), true).end();
    long bytes = EMPTY_SESSION_BYTES + ReaderBudget.FRAME_BYTES + 3L * ReaderBudget.CALL_BYTES;
    TraceSnapshot d = index(w, bytes);
    try {
      assertEquals(
          100, d.totalCalls, "a long trace can reuse a bounded overview and shallow stack");
      assertEquals(1, d.threads.get(0).overview.count);
    } finally {
      d.release();
    }
  }

  @Test
  void incrementalAdvancesKeepTheirExistingReservations() throws IOException {
    TraceBuilder w = TestTraces.writer().chunk(1, 0, new byte[0], true);
    Path f = write(w);
    TraceIndexer ix = TraceIndexer.open(f, 1, new ReaderBudget(EMPTY_SESSION_BYTES));
    try {
      ix.advance(ProgressListener.NONE);
      int before = w.bytes().length;
      byte[] all = w.chunk(1, 0, new byte[0], true).end().bytes();
      TestTraces.append(f, all, before, all.length);
      ReadLimitException failure =
          assertThrows(ReadLimitException.class, () -> ix.advance(ProgressListener.NONE));
      assertTrue(
          failure.getMessage().contains("sessions"), "a live append cannot reset its budget");
    } finally {
      ix.close();
    }
  }

  @Test
  void snapshotsCanFinishOpenCallsWithoutConsumingTheLiveIndexersBudget() throws IOException {
    byte[] enter = new TraceBuilder.Payload(0).enter(0, 0).bytes();
    TraceBuilder w = TestTraces.writer().chunk(1, 0, enter, false);
    Path f = write(w);
    long bytes = EMPTY_SESSION_BYTES + ReaderBudget.FRAME_BYTES + ReaderBudget.CALL_BYTES;
    TraceIndexer ix = TraceIndexer.open(f, 1, new ReaderBudget(bytes));
    try {
      ix.advance(ProgressListener.NONE);
      for (int i = 0; i < 2; i++) {
        TraceSnapshot d = ix.snapshot();
        try {
          assertEquals(1, d.totalCalls);
          assertFalse(d.sessions.get(0).ended);
        } finally {
          d.release();
        }
      }
      int before = w.bytes().length;
      byte[] all = w.chunk(1, 0, enter, false).bytes();
      TestTraces.append(f, all, before, all.length);
      ReadLimitException failure =
          assertThrows(ReadLimitException.class, () -> ix.advance(ProgressListener.NONE));
      assertTrue(
          failure.getMessage().contains("call depth"),
          "snapshot reservations must not consume the remaining live-index chunk reservation");
    } finally {
      ix.close();
    }
  }

  @Test
  void replacingARejectedRecordingStartsWithAFreshBudget() throws IOException {
    TraceBuilder w = TestTraces.writer();
    w.chunk(1, 0, new byte[0], true).chunk(1, 0, new byte[0], true).end();
    Path f = write(w);
    TraceIndexer ix = TraceIndexer.open(f, 1, new ReaderBudget(EMPTY_SESSION_BYTES));
    try {
      assertThrows(ReadLimitException.class, () -> ix.advance(ProgressListener.NONE));
      Files.write(f, TestTraces.writer().bytes());
      ix.advance(ProgressListener.NONE);
      byte[] replacement = TestTraces.writer().chunk(1, 0, new byte[0], true).end().bytes();
      TestTraces.append(f, replacement, Vbtm.HEADER_BYTES, replacement.length);
      ix.advance(ProgressListener.NONE);
      TraceSnapshot d = ix.snapshot();
      try {
        assertEquals(1, d.sessions.size(), "a new recording must not inherit an earlier rejection");
        assertTrue(d.sessions.get(0).ended);
      } finally {
        d.release();
      }
    } finally {
      ix.close();
    }
  }

  private static Path write(TraceBuilder w) throws IOException {
    Path f = TestTraces.tempFile();
    Files.write(f, w.bytes());
    return f;
  }

  private static TraceSnapshot index(TraceBuilder w, long bytes) throws IOException {
    TraceIndexer ix = TraceIndexer.open(write(w), 1, new ReaderBudget(bytes));
    try {
      ix.advance(ProgressListener.NONE);
      return ix.snapshot();
    } finally {
      ix.close();
    }
  }

  private static void assertLimit(TraceBuilder w, long bytes, String resource) throws IOException {
    TraceIndexer ix = TraceIndexer.open(write(w), 1, new ReaderBudget(bytes));
    try {
      ReadLimitException failure =
          assertThrows(ReadLimitException.class, () -> ix.advance(ProgressListener.NONE));
      assertTrue(failure.getMessage().contains(resource));
    } finally {
      ix.close();
    }
  }
}
