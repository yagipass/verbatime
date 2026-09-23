package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer.ProgressListener;

final class TraceIndexerIncrementalTest {

    private static TraceSnapshot fromScratch(final Path f, final int budget) throws IOException {
        return TraceIndexer.index(f, budget, ProgressListener.NONE);
    }

    @Test
    void growingFileMatchesFromScratchAtEveryByteBoundary() throws IOException {
        final long[] seeds = { 42, 1, 2 };
        final int[] budgets = { 1 << 30, 64, 48 };
        for (int k = 0; k < seeds.length; k++) {
            final long seed = seeds[k];
            final int budget = budgets[k];
            final byte[] full = RandomTraces.random(seed);
            final Path f = TestTraces.tempFile();
            final TraceIndexer ix = TraceIndexer.open(f, budget);

            TestTraces.append(f, full, 0, 3);
            assertThrows(TraceIndexer.NotTraceFormatException.class, () -> ix.advance(ProgressListener.NONE));
            int prev = 3;

            for (int cut = 4; cut <= full.length; cut += cut < 2000 ? 1 : 7) {
                TestTraces.append(f, full, prev, cut);
                prev = cut;
                ix.advance(ProgressListener.NONE);
                final TraceSnapshot snap = ix.snapshot();
                final String ctx = "seed " + seed + " budget " + budget + " cut " + cut;
                TestTraces.assertSameTraceData(fromScratch(f, budget), snap, ctx);
                if (budget == 1 << 30) {
                    TraceIndexerTest.assertMatchesReference(ReferenceDecoder.decode(Arrays.copyOf(full, cut)), snap, ctx);
                }
            }
            if (prev < full.length) {
                TestTraces.append(f, full, prev, full.length);
                ix.advance(ProgressListener.NONE);
            }
            final TraceSnapshot last = ix.snapshot();
            assertFalse(last.truncated, "seed " + seed + ": the footer arrived");
            TestTraces.assertSameTraceData(fromScratch(f, budget), last, "seed " + seed + " final");
            if (budget != 1 << 30) {
                final ReferenceDecoder.Result ref = ReferenceDecoder.decode(full);
                assertEquals(TestTraces.expectedThreshold(ref.calls, budget), last.overviewThresholdNs,
                        "seed " + seed + ": compaction across advances lands on the two-pass threshold");
            }
        }
    }

    @Test
    void aSnapshotNeverChangesUnderLaterAdvances() throws IOException {
        final byte[] full = RandomTraces.random(7);
        final Path f = TestTraces.tempFile();
        final int half = full.length / 2;
        final TraceIndexer ix = TraceIndexer.open(f, 64);
        TestTraces.append(f, full, 0, half);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot s1 = ix.snapshot();
        final TraceSnapshot expected = fromScratch(f, 64);
        TestTraces.assertSameTraceData(expected, s1, "half");

        TestTraces.append(f, full, half, full.length);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot s2 = ix.snapshot();

        TestTraces.assertSameTraceData(expected, s1, "half, after the file grew");
        assertEquals(half, s1.buffer.size());
        assertTrue(s1.sessions.size() > 0 && s1.threads.size() > 0, "half of seed 7 holds frames");
        assertNotSame(s1.sessions.get(0), s2.sessions.get(0));
        assertNotSame(s1.threads.get(0), s2.threads.get(0));
        TestTraces.assertSameTraceData(fromScratch(f, 64), s2, "full");
    }

    @Test
    void advanceWithoutGrowthIsIdempotentAndDoesNotRemap() throws IOException {
        final byte[] full = RandomTraces.random(3);
        final Path f = TestTraces.tempFile();
        TestTraces.append(f, full, 0, full.length);
        final TraceIndexer ix = TraceIndexer.open(f, 64);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot s1 = ix.snapshot();
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot s2 = ix.snapshot();
        TestTraces.assertSameTraceData(s1, s2, "unchanged file");
        assertSame(s1.buffer, s2.buffer, "an unchanged file is not mapped again");
    }

    @Test
    void bytesAfterAFooterSeenByAnEarlierAdvanceAreCorrupt() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200);
        w.chunk(3, 100, p.bytes(), true);
        final long footer = w.bytes().length;
        w.end();
        final byte[] clean = w.bytes();
        w.thread(5, "late");
        final byte[] late = w.bytes();

        final Path f = TestTraces.tempFile();
        TestTraces.append(f, clean, 0, clean.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        assertFalse(ix.snapshot().truncated, "a footer as the last byte is a clean recording");
        TestTraces.append(f, late, clean.length, late.length);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot d = ix.snapshot();
        assertEquals(footer, d.corruptOffset, "the same finding as TraceIndexerTest.dataAfterFooterIsCorrupt");
        assertTrue(d.corruptReason.contains("footer"));
        assertFalse(d.truncated);
        TestTraces.assertSameTraceData(fromScratch(f, 1 << 20), d, "late thread record");
    }

    @Test
    void aCorruptFileStaysCorruptWhateverIsAppended() throws IOException {
        final byte[] bad = corruptBytes();
        final Path f = TestTraces.tempFile();
        TestTraces.append(f, bad, 0, bad.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot d1 = ix.snapshot();
        assertEquals(bad.length - 1, d1.corruptOffset);

        final TraceBuilder more = TestTraces.writer();
        more.chunk(3, 300, new TraceBuilder.Payload(300).enter(300, 1).exit(400).bytes(), true).end();
        final byte[] tail = more.bytes();
        TestTraces.append(f, tail, Vbtm.HEADER_BYTES, tail.length);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot d2 = ix.snapshot();
        TestTraces.assertSameTraceData(fromScratch(f, 1 << 20), d2, "corrupt, then grown");
        assertEquals(d1.generation, d2.generation, "an append never rescans a corrupt file from the start, only a replacement does");
        assertEquals(1, d2.totalCalls, "nothing after the corrupt offset is ever read");
    }

    @Test
    void aCancelledAdvanceResumesFromWhereItStopped() throws IOException {
        final byte[] full = RandomTraces.random(11);
        final Path f = TestTraces.tempFile();
        TestTraces.append(f, full, 0, full.length);
        final TraceIndexer ix = TraceIndexer.open(f, 64);
        final int[] calls = { 0 };
        assertThrows(TraceIndexer.CancelledException.class, () -> ix.advance((done, total) -> calls[0]++ == 0));
        assertTrue(calls[0] > 0, "the cancel came from the progress callback");
        ix.advance(ProgressListener.NONE);
        TestTraces.assertSameTraceData(fromScratch(f, 64), ix.snapshot(), "after a cancel");
    }

    @Test
    void aFileShorterThanWhatWasConsumedIsANewFile() throws IOException {
        final byte[] first = RandomTraces.random(1);
        final byte[] second = RandomTraces.random(2);
        final Path f = TestTraces.tempFile();
        TestTraces.append(f, first, 0, first.length);
        final TraceIndexer ix = TraceIndexer.open(f, 64);
        ix.advance(ProgressListener.NONE);
        TestTraces.assertSameTraceData(fromScratch(f, 64), ix.snapshot(), "first");

        final byte[] shorter = Arrays.copyOf(second, Math.min(second.length, first.length - 1));
        Files.write(f, shorter);
        ix.advance(ProgressListener.NONE);
        TestTraces.assertSameTraceData(fromScratch(f, 64), ix.snapshot(), "replaced");
    }

    @Test
    void theEpochSurvivesGrowthAndChangesWhenTheFileIsReplaced() throws IOException {
        final byte[] first = RandomTraces.random(1);
        final Path f = TestTraces.tempFile();
        final int half = first.length / 2;
        TestTraces.append(f, first, 0, half);
        final TraceIndexer ix = TraceIndexer.open(f, 64);
        ix.advance(ProgressListener.NONE);
        final long generation = ix.snapshot().generation;

        TestTraces.append(f, first, half, first.length);
        ix.advance(ProgressListener.NONE);
        assertEquals(generation, ix.snapshot().generation, "growth keeps the generation, so a viewer can patch its session list");
        assertNotEquals(generation, fromScratch(f, 64).generation, "another indexer over the same bytes never shares a generation");

        Files.write(f, Arrays.copyOf(RandomTraces.random(2), half));
        ix.advance(ProgressListener.NONE);
        assertNotEquals(generation, ix.snapshot().generation, "a replaced file is a new recording whose seq numbers restart");
    }

    private static byte[] withAnchor(final byte[] trace, final long epochMs, final int utcOffsetSeconds) {
        final byte[] out = trace.clone();
        System.arraycopy(new TraceBuilder(epochMs, utcOffsetSeconds).bytes(), 0, out, 0, Vbtm.HEADER_BYTES);
        return out;
    }

    private static byte[] corruptBytes() {
        final TraceBuilder w = TestTraces.writer();
        w.chunk(3, 100, new TraceBuilder.Payload(100).enter(100, 1).exit(200).bytes(), true);
        w.rawBytes(0x7E);
        return w.bytes();
    }

    @Test
    void aLargerFileWithAnotherAnchorIsANewRecordingNotAnAppend() throws IOException {
        final byte[] first = RandomTraces.random(1);
        final Path f = TestTraces.tempFile();
        TestTraces.append(f, first, 0, first.length / 2);
        final TraceIndexer ix = TraceIndexer.open(f, 64);
        ix.advance(ProgressListener.NONE);
        final long generation = ix.snapshot().generation;

        final byte[] second = withAnchor(RandomTraces.random(2), TestTraces.DEFAULT_START_EPOCH_MS + 1,
                TestTraces.DEFAULT_UTC_OFFSET_SECONDS);
        assertTrue(second.length > first.length / 2, "the replacement outgrew what was consumed, so size alone cannot tell");
        Files.write(f, second);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot d = ix.snapshot();
        TestTraces.assertSameTraceData(fromScratch(f, 64), d, "resuming at the old offset would read mid-record garbage");
        assertNotEquals(generation, d.generation, "the viewer must drop the old recording's sessions");
    }

    @Test
    void aCorruptFileIsRereadOnceItIsReplaced() throws IOException {
        final byte[] bad = corruptBytes();
        final Path f = TestTraces.tempFile();
        TestTraces.append(f, bad, 0, bad.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        assertEquals(bad.length - 1, ix.snapshot().corruptOffset);

        final byte[] fresh = withAnchor(RandomTraces.random(3), TestTraces.DEFAULT_START_EPOCH_MS + 1,
                TestTraces.DEFAULT_UTC_OFFSET_SECONDS);
        assertTrue(fresh.length > bad.length, "larger than the corrupt file, so only the anchor tells them apart");
        Files.write(f, fresh);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot d = ix.snapshot();
        assertEquals(-1, d.corruptOffset, "the corrupt file is gone, so a reload must not keep reporting it");
        TestTraces.assertSameTraceData(fromScratch(f, 1 << 20), d, "corrupt, then replaced");
    }

    @Test
    void aCorruptFileIsRereadWhenItShrinks() throws IOException {
        final byte[] bad = corruptBytes();
        final Path f = TestTraces.tempFile();
        TestTraces.append(f, bad, 0, bad.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        assertEquals(bad.length - 1, ix.snapshot().corruptOffset);

        final byte[] shorter = Arrays.copyOf(bad, bad.length - 1);
        Files.write(f, shorter);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot d = ix.snapshot();
        assertEquals(-1, d.corruptOffset, "a shorter file is a new file even while the old one was corrupt");
        TestTraces.assertSameTraceData(fromScratch(f, 1 << 20), d, "corrupt, then shrunk");
    }
}
