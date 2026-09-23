package io.github.yagipass.verbatime.jmc.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TransferTest {

    @TempDir
    Path dir;

    final FakeAgent agent = new FakeAgent();

    final List<Long> progress = new ArrayList<>();

    Long finished;

    String failed;

    int stopped;

    LongConsumer onProgress = bytes -> {
    };

    private final Transfer.Listener listener = new Transfer.Listener() {
        @Override
        public void progress(final long bytes) {
            progress.add(bytes);
            onProgress.accept(bytes);
        }

        @Override
        public void finished(final long bytes) {
            finished = bytes;
        }

        @Override
        public void failed(final String message) {
            failed = message;
        }

        @Override
        public void stopped() {
            stopped++;
        }
    };

    @Test
    void appendsEveryChunkAndClosesTheStreamEvenOnFailure() throws IOException {
        agent.chunks.add(new byte[3]);
        agent.chunks.add(new byte[2]);
        agent.readError = new IOException("boom");
        final Path local = dir.resolve("r.vbtm");
        new Transfer(agent, 1, local, 0, listener, 1).run(new NullProgressMonitor());
        assertEquals(5, Files.size(local));
        assertEquals(List.of(3L, 5L), progress);
        assertEquals("boom", failed);
        assertNull(finished);
        assertEquals(List.of(100L), agent.closedStreams);
    }

    @Test
    void emptyChunkMeansWaitAndNullMeansEndOfRecording() throws IOException {
        agent.chunks.add(new byte[1]);
        agent.chunks.add(new byte[0]);
        agent.chunks.add(new byte[2]);
        final Path local = dir.resolve("r.vbtm");
        new Transfer(agent, 1, local, 0, listener, 1).run(new NullProgressMonitor());
        assertEquals(List.of(1L, 3L), progress, "an empty chunk is a pause, not progress");
        assertEquals(3L, finished);
        assertNull(failed);
        assertEquals(3, Files.size(local));
    }

    @Test
    void openStreamStartsAtTheLocalOffsetSoResumedBytesAreNotDuplicated() throws IOException {
        final Path local = dir.resolve("r.vbtm");
        Files.write(local, new byte[4]);
        agent.chunks.add(new byte[2]);
        new Transfer(agent, 1, local, 4, listener, 1).run(new NullProgressMonitor());
        assertEquals(List.of(4L), agent.openedAt);
        assertEquals(6, Files.size(local));
        assertEquals(6L, finished);
    }

    @Test
    void cancelStopsBetweenChunksWithoutReportingFailure() throws IOException {
        agent.chunks.add(new byte[1]);
        agent.chunks.add(new byte[1]);
        final NullProgressMonitor monitor = new NullProgressMonitor();
        final Path local = dir.resolve("r.vbtm");
        onProgress = bytes -> monitor.setCanceled(true);
        new Transfer(agent, 1, local, 0, listener, 1).run(monitor);
        assertEquals(1, Files.size(local), "the chunk in flight is kept, the next one is never read");
        assertNull(finished);
        assertNull(failed);
        assertEquals(1, stopped, "the owner waits for this before it opens the file again");
        assertEquals(List.of(100L), agent.closedStreams);
        assertEquals(1, agent.chunks.size(), "the second chunk stays on the agent for the resumed transfer");
    }

    @Test
    void aCancelThatLandsDuringReadStreamKeepsTheChunkAndReportsStopped() throws IOException {
        final NullProgressMonitor monitor = new NullProgressMonitor();
        agent.onRead = () -> monitor.setCanceled(true);
        agent.chunks.add(new byte[3]);
        final Path local = dir.resolve("r.vbtm");
        new Transfer(agent, 1, local, 0, listener, 1).run(monitor);
        assertEquals(3, Files.size(local),
                "the next transfer resumes from the file size, so bytes already fetched are kept rather than refetched");
        assertEquals(1, stopped);
        assertNull(finished);
        assertNull(failed);
        assertEquals(List.of(100L), agent.closedStreams);
    }

    @Test
    void anInterruptWhileWaitingForDataReportsStoppedAndKeepsTheFlag() throws IOException {
        agent.chunks.add(new byte[0]);
        final Path local = dir.resolve("r.vbtm");
        Thread.currentThread().interrupt();
        try {
            new Transfer(agent, 1, local, 0, listener, 60_000).run(new NullProgressMonitor());
            assertTrue(Thread.currentThread().isInterrupted(), "the job's interrupt is left for the caller to see");
        } finally {
            Thread.interrupted();
        }
        assertEquals(1, stopped);
        assertNull(finished);
        assertNull(failed);
        assertEquals(List.of(100L), agent.closedStreams);
    }

    @Test
    void aFailureAfterCancellationIsStoppedNotFailed() throws IOException {
        final NullProgressMonitor monitor = new NullProgressMonitor();
        agent.onRead = () -> monitor.setCanceled(true);
        agent.readError = new IOException("connection closed");
        new Transfer(agent, 1, dir.resolve("r.vbtm"), 0, listener, 1).run(monitor);
        assertEquals(1, stopped, "a cancelled transfer that then loses its socket is not an error to report");
        assertNull(failed);
        assertNull(finished);
    }
}
