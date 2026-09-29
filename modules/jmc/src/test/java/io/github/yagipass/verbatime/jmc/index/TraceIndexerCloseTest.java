package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.errorprone.annotations.Var;

import io.github.yagipass.verbatime.jmc.index.TraceIndexer.ProgressListener;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

final class TraceIndexerCloseTest {

    private static long exits(TraceSnapshot data) {
        @Var long n = 0;
        for (ThreadIndex m : data.threads) {
            long[] count = { 0 };
            ChunkWalker.walkRange(data, m, Long.MIN_VALUE, Long.MAX_VALUE,
                    (startNs, durNs, childNs, methodId, sessionDepth, sp, exc) -> {
                        count[0]++;
                        return true;
                    });
            n += count[0];
        }
        return n;
    }

    @Test
    void closeCancelsLaterAdvancesAndSnapshots() throws IOException {
        Path f = TestTraces.tempFile();
        Files.write(f, RandomTraces.random(3));
        TraceIndexer ix = TraceIndexer.open(f, 64);
        ix.advance(ProgressListener.NONE);
        ix.close();
        assertThrows(TraceIndexer.CancelledException.class, () -> ix.advance(ProgressListener.NONE),
                "a load job that outlives dispose() must not map the file again");
        assertThrows(TraceIndexer.CancelledException.class, ix::snapshot);
        ix.close();
    }

    @Test
    void aSnapshotTakenBeforeCloseStaysReadableUntilItIsReleased() throws IOException {
        Path f = TestTraces.tempFile();
        Files.write(f, RandomTraces.random(3));
        TraceIndexer ix = TraceIndexer.open(f, 64);
        ix.advance(ProgressListener.NONE);
        TraceSnapshot s = ix.snapshot();
        TraceSnapshot expected = TraceIndexer.index(f, 64, ProgressListener.NONE);
        ix.close();

        assertFalse(s.buffer.isClosed(), "the extractor may still walk the last snapshot after the editor closed");
        assertEquals(exits(expected), exits(s), "the walk reads the same bytes as a fresh index");
        s.release();
        assertTrue(s.buffer.isClosed());
        expected.release();
    }

    @Test
    void aRemapReleasesTheOldMappingOnlyWhenItsSnapshotsAreGone() throws IOException {
        byte[] full = RandomTraces.random(7);
        Path f = TestTraces.tempFile();
        int half = full.length / 2;
        TestTraces.append(f, full, 0, half);
        TraceIndexer ix = TraceIndexer.open(f, 64);
        ix.advance(ProgressListener.NONE);
        TraceSnapshot s1 = ix.snapshot();

        TestTraces.append(f, full, half, full.length);
        ix.advance(ProgressListener.NONE);
        TraceSnapshot s2 = ix.snapshot();

        assertFalse(s1.buffer.isClosed(), "the viewer may still be answering requests on the pre-growth snapshot");
        s1.release();
        assertTrue(s1.buffer.isClosed(), "the indexer gave its own reference up at the remap");
        assertFalse(s2.buffer.isClosed());
        ix.close();
        assertFalse(s2.buffer.isClosed());
        s2.release();
        assertTrue(s2.buffer.isClosed());
    }

    @Test
    void closeDuringAnAdvanceLetsTheScanFinishOnTheMapping() throws IOException {
        Path f = TestTraces.tempFile();
        Files.write(f, RandomTraces.random(11));
        TraceIndexer ix = TraceIndexer.open(f, 64);
        int[] calls = { 0 };
        ix.advance((done, total) -> {
            if (calls[0]++ == 0) {
                ix.close();
            }
            return false;
        });
        assertTrue(calls[0] > 1, "the scan carried on past the close instead of stopping or unmapping under itself");
        assertThrows(TraceIndexer.CancelledException.class, ix::snapshot, "but nothing new can be read afterwards");
    }

    @Test
    void aChunkCursorReleasesItsReferenceExactlyOnce() throws IOException {
        Path f = TestTraces.tempFile();
        Files.write(f, RandomTraces.random(3));
        TraceSnapshot data = TraceIndexer.index(f, 64, ProgressListener.NONE);
        ThreadIndex m = data.threads.get(0);
        ChunkCursor cursor = new ChunkCursor(data.buffer, m, 0, m.chunks.count - 1);
        while (cursor.next()) {
        }
        cursor.release();
        assertFalse(data.buffer.isClosed(), "the double release must not take the snapshot's reference with it");
        data.release();
        assertTrue(data.buffer.isClosed());
    }

    @Test
    void walkingAReleasedSnapshotIsRefusedInsteadOfCrashing() throws IOException {
        Path f = TestTraces.tempFile();
        Files.write(f, RandomTraces.random(3));
        TraceSnapshot data = TraceIndexer.index(f, 64, ProgressListener.NONE);
        data.release();
        assertThrows(MappedTrace.ClosedException.class, () -> exits(data),
                "a stale extractor task must surface as an error the editor already reports");
    }
}
