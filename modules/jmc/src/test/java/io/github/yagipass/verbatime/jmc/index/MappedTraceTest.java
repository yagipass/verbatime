package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MappedTraceTest {

    private static Path fileWith(final byte[] bytes) throws IOException {
        final Path f = TestTraces.tempFile();
        Files.write(f, bytes);
        return f;
    }

    @Test
    void theLastReleaseUnmapsAndALaterRetainIsRefused() throws IOException {
        final MappedTrace b = MappedTrace.open(fileWith(new byte[] { 7, 8, 9 }));
        assertFalse(b.isClosed());
        b.release();
        assertTrue(b.isClosed(), "the opener held the only reference");
        assertThrows(MappedTrace.ClosedException.class, b::retain,
                "a walker that arrives after the unmap must be refused, not handed freed memory");
    }

    @Test
    void anOutstandingRetainDefersTheUnmapPastTheOwnersRelease() throws IOException {
        final MappedTrace b = MappedTrace.open(fileWith(new byte[] { 42, 1, 2 }));
        b.retain();
        b.release();
        assertFalse(b.isClosed(), "a snapshot the viewer draws from outlives the indexer that made it");
        assertEquals(42, b.byteAt(0), "still readable while the second holder is alive");
        b.release();
        assertTrue(b.isClosed());
    }

    @Test
    void releasingMoreThanRetainedFailsLoudly() throws IOException {
        final MappedTrace b = MappedTrace.open(fileWith(new byte[] { 1 }));
        b.release();
        final IllegalStateException e = assertThrows(IllegalStateException.class, b::release);
        assertTrue(e.getMessage().contains("more times than retained"), e.getMessage());
    }

    @Test
    void eagerUnmapIsAvailableOnTheBuildJdk() {
        assertTrue(MappedTrace.unmapSupported(),
                "the fallback to GC is silent, and without it the Windows delete failure comes back unnoticed");
    }

    @Test
    void theFileCanBeDeletedOnceTheLastReferenceIsGone() throws IOException {
        final Path f = fileWith(RandomTraces.random(5));
        final TraceSnapshot data = TraceIndexer.index(f);
        assertFalse(data.buffer.isClosed());
        data.release();
        assertTrue(data.buffer.isClosed(), "index() closed its indexer, so the snapshot held the last reference");
        Files.delete(f);
    }
}
