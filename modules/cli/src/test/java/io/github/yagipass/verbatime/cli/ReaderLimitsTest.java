package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yagipass.verbatime.format.ReadLimitException;
import io.github.yagipass.verbatime.format.ReaderBudget;
import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReaderLimitsTest {

  @TempDir Path dir;

  @Test
  void emptySessionsCannotAccumulatePastTheReaderBudget() throws IOException {
    long limit = 2L * (ReaderBudget.SESSION_BYTES + ReaderBudget.CHUNK_BYTES);
    TraceBuilder trace = new TraceBuilder(0, 0);
    trace.chunk(1, 0, new byte[0], true).chunk(1, 0, new byte[0], true);
    Path boundary = write("two-sessions.vbtm", trace);
    assertEquals(
        2,
        TraceFile.open(boundary, new ReaderBudget(limit)).sessions.size(),
        "a recording at the budget boundary remains readable");
    Path excessive = write("three-sessions.vbtm", trace.chunk(1, 0, new byte[0], true));
    ReadLimitException failure =
        assertThrows(
            ReadLimitException.class, () -> TraceFile.open(excessive, new ReaderBudget(limit)));
    assertTrue(
        failure.getMessage().contains("sessions"),
        "empty sessions still retain objects, so their count must be bounded: "
            + failure.getMessage());
  }

  @Test
  void chunksAreBoundedEvenWhenTheyBelongToOneSession() throws IOException {
    long limit = ReaderBudget.SESSION_BYTES + 2L * ReaderBudget.CHUNK_BYTES;
    TraceBuilder trace = new TraceBuilder(0, 0);
    trace.chunk(1, 0, new byte[0], false).chunk(1, 0, new byte[0], false);
    TraceFile file = TraceFile.open(write("two-chunks.vbtm", trace), new ReaderBudget(limit));
    assertEquals(1, file.sessions.size(), "continuing chunks share the existing session");
    assertEquals(2, file.sessions.get(0).chunks.size());
    Path excessive = write("three-chunks.vbtm", trace.chunk(1, 0, new byte[0], true));
    ReadLimitException failure =
        assertThrows(
            ReadLimitException.class, () -> TraceFile.open(excessive, new ReaderBudget(limit)));
    assertTrue(
        failure.getMessage().contains("chunks"),
        "a session count limit alone would leave unlimited chunk metadata: "
            + failure.getMessage());
  }

  @Test
  void redeclaringAThreadDoesNotConsumeTheAllowanceForAnotherThread() throws IOException {
    TraceBuilder trace = new TraceBuilder(0, 0).thread(1, "first").thread(1, "renamed");
    TraceFile file =
        TraceFile.open(
            write("renamed-thread.vbtm", trace), new ReaderBudget(ReaderBudget.THREAD_NAME_BYTES));
    assertEquals("renamed", file.threadName(1));
    Path excessive = write("two-threads.vbtm", trace.thread(2, "second"));
    ReadLimitException failure =
        assertThrows(
            ReadLimitException.class,
            () -> TraceFile.open(excessive, new ReaderBudget(ReaderBudget.THREAD_NAME_BYTES)));
    assertTrue(
        failure.getMessage().contains("threads"),
        "new thread names retain map entries even without sessions: " + failure.getMessage());
  }

  @Test
  void gcPausesCannotAccumulatePastTheBudgetInARecordingWithoutSessions() throws IOException {
    TraceBuilder trace = new TraceBuilder(0, 0).gc(0, 1, 0, "", "").gc(1, 1, 0, "", "");
    TraceFile file =
        TraceFile.open(
            write("two-gc-pauses.vbtm", trace), new ReaderBudget(2L * ReaderBudget.GC_BYTES));
    assertEquals(2, file.gcPauses.size(), "a recording at the budget boundary keeps every pause");
    Path excessive = write("three-gc-pauses.vbtm", trace.gc(2, 1, 0, "", ""));
    ReadLimitException failure =
        assertThrows(
            ReadLimitException.class,
            () -> TraceFile.open(excessive, new ReaderBudget(2L * ReaderBudget.GC_BYTES)));
    assertTrue(
        failure.getMessage().contains("gc pauses"),
        "every pause is retained, so six-byte GC records alone could exhaust the heap: "
            + failure.getMessage());
  }

  @Test
  void aNewThreadForEverySessionCostsOnlyItsNameAsWithVirtualThreads() throws IOException {
    TraceBuilder trace = new TraceBuilder(0, 0);
    for (int tid = 1; tid <= 4; tid++) {
      trace.thread(tid, "virtual-" + tid).chunk(tid, 0, new byte[0], true);
    }
    long limit =
        4L
            * (ReaderBudget.THREAD_NAME_BYTES
                + ReaderBudget.SESSION_BYTES
                + ReaderBudget.CHUNK_BYTES);
    assertEquals(
        4,
        TraceFile.open(write("thread-per-session.vbtm", trace), new ReaderBudget(limit))
            .sessions
            .size(),
        "the CLI keeps only a name per thread, so charging the indexer's per-thread state would"
            + " reject recordings of servers that run each request on a new virtual thread");
  }

  @Test
  void depthContinuesAcrossChunksAndRepeatedWalksReuseTheAllowance() throws IOException {
    byte[] first = enters(64);
    byte[] second = enters(1);
    Path path =
        write(
            "deep-chunks.vbtm",
            new TraceBuilder(0, 0).chunk(1, 0, first, false).chunk(1, 0, second, true));
    long metadata = ReaderBudget.SESSION_BYTES + 2L * ReaderBudget.CHUNK_BYTES;
    long scratch = 2L * first.length;
    TraceFile allowed =
        TraceFile.open(path, new ReaderBudget(metadata + scratch + 65L * ReaderBudget.FRAME_BYTES));
    SessionWalker walker = new SessionWalker(allowed);
    for (int i = 0; i < 3; i++) {
      SessionSummary summary = SessionSummary.of(walker, allowed.sessions.get(0));
      assertEquals(65, summary.calls);
      assertEquals(64, summary.maxDepth);
      assertTrue(summary.unclosed, "open calls are still summarized at the end of a session");
    }
    TraceFile excessive =
        TraceFile.open(path, new ReaderBudget(metadata + scratch + 64L * ReaderBudget.FRAME_BYTES));
    ReadLimitException failure =
        assertThrows(
            ReadLimitException.class,
            () -> SessionSummary.of(new SessionWalker(excessive), excessive.sessions.get(0)));
    assertTrue(
        failure.getMessage().contains("call depth"),
        "splitting nested calls into chunks must not reset the depth limit: "
            + failure.getMessage());
  }

  @Test
  void walkingAnotherSessionChargesOnlyAdditionalDepthAndScratchCapacity() throws IOException {
    byte[] first = enters(65);
    byte[] second = enters(66);
    Path path =
        write(
            "two-deep-sessions.vbtm",
            new TraceBuilder(0, 0).chunk(1, 0, first, true).chunk(1, 0, second, true));
    long limit =
        2L * (ReaderBudget.SESSION_BYTES + ReaderBudget.CHUNK_BYTES)
            + 2L * second.length
            + 66L * ReaderBudget.FRAME_BYTES;
    TraceFile file = TraceFile.open(path, new ReaderBudget(limit));
    SessionWalker walker = new SessionWalker(file);
    assertEquals(65, SessionSummary.of(walker, file.sessions.get(0)).calls);
    assertEquals(
        66,
        SessionSummary.of(walker, file.sessions.get(1)).calls,
        "sequential sessions reuse the walker's buffers instead of accumulating all their depths");
  }

  @Test
  void payloadBuffersAreCheckedBeforeTheChunkIsDecoded() throws IOException {
    byte[] payload = enters(65);
    Path path = write("large-chunk.vbtm", new TraceBuilder(0, 0).chunk(1, 0, payload, true));
    long limit = ReaderBudget.SESSION_BYTES + ReaderBudget.CHUNK_BYTES + 2L * payload.length - 1;
    TraceFile file = TraceFile.open(path, new ReaderBudget(limit));
    ReadLimitException failure =
        assertThrows(
            ReadLimitException.class,
            () -> SessionSummary.of(new SessionWalker(file), file.sessions.get(0)));
    assertTrue(
        failure.getMessage().contains("chunk payload buffer"),
        "a payload allocation can exhaust the heap before any frame is pushed: "
            + failure.getMessage());
  }

  @Test
  void budgetFailuresExitWithTwoAndExplainTheLimitWithoutAStackTrace() throws IOException {
    Path path = write("limited.vbtm", new TraceBuilder(0, 0).chunk(1, 0, new byte[0], true));
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    int code;
    try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
      code =
          Main.run(
              List.of("sessions", path.toString()),
              out,
              err,
              (args, output) -> {
                TraceFile.open(path, new ReaderBudget(ReaderBudget.SESSION_BYTES - 1));
                return 0;
              });
    }
    assertEquals(2, code, "a memory budget failure must be a controlled CLI error");
    assertEquals("", stdout.toString(StandardCharsets.UTF_8));
    String error = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(error.startsWith("vbtm: recording exceeds the reader memory budget"), error);
    assertTrue(error.contains("sessions"), error);
    assertEquals(1, error.lines().count(), "the CLI must not print a Java exception stack trace");
  }

  private static byte[] enters(int count) {
    TraceBuilder.Payload payload = new TraceBuilder.Payload(0);
    for (int i = 0; i < count; i++) {
      payload.enter(0, 0);
    }
    return payload.bytes();
  }

  private Path write(String name, TraceBuilder trace) throws IOException {
    byte[] records = trace.bytes();
    byte[] complete = Arrays.copyOf(records, records.length + 1);
    complete[records.length] = (byte) Vbtm.RECORD_END;
    return TestTraces.write(dir, name, complete);
  }
}
