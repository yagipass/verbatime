package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PatchableFileWriterTest {

    @TempDir
    Path dir;

    @Test
    void millisecondsAlwaysCarryFourDecimalsSoOneTickIsVisible() throws IOException {
        final Path f = dir.resolve("ms.txt");
        try (PatchableFileWriter w = new PatchableFileWriter(f, 64)) {
            w.writeTicksAsMs(0);
            w.put(' ');
            w.writeTicksAsMs(1);
            w.put(' ');
            w.writeTicksAsMs(10_000);
            w.put(' ');
            w.writeTicksAsMs(123_456_789L);
            w.put(' ');
            w.num(42);
            w.newline();
            assertEquals(1, w.lines());
        }
        assertEquals("0.0000 0.0001 1.0000 12345.6789 42\n", Files.readString(f));
    }

    @Test
    void patchesLandAtTheirAbsoluteOffsetWhetherBufferedFlushedOrStraddling() throws IOException {
        for (int seed = 1; seed <= 40; seed++) {
            final Random rng = new Random(seed);
            final Path f = dir.resolve("random-" + seed + ".txt");
            final ByteArrayOutputStream model = new ByteArrayOutputStream();
            final List<long[]> slots = new ArrayList<>();
            try (PatchableFileWriter w = new PatchableFileWriter(f, 64)) {
                for (int step = 0; step < 300; step++) {
                    final int op = rng.nextInt(10);
                    if (op < 5) {
                        final byte[] chunk = new byte[1 + rng.nextInt(90)];
                        for (int i = 0; i < chunk.length; i++) {
                            chunk[i] = (byte) ('a' + rng.nextInt(26));
                        }
                        assertEquals(model.size(), w.position());
                        w.bytes(chunk);
                        model.writeBytes(chunk);
                    } else if (op < 7) {
                        final int width = 6 + rng.nextInt(6);
                        slots.add(new long[] { model.size(), width });
                        w.placeholder(width);
                        for (int i = 0; i < width; i++) {
                            model.write('?');
                        }
                    } else if (op < 9 && !slots.isEmpty()) {
                        final long[] slot = slots.remove(rng.nextInt(slots.size()));
                        long limit = 10_000;
                        for (int i = 5; i < slot[1]; i++) {
                            limit *= 10;
                        }
                        final long ticks = rng.nextLong(limit);
                        w.patchTicksAsMs(slot[0], ticks, (int) slot[1]);
                        final String text = String.format(java.util.Locale.ROOT, "%" + slot[1] + "s",
                                ticks / 10_000 + "." + String.format(java.util.Locale.ROOT, "%04d", ticks % 10_000));
                        final byte[] bytes = model.toByteArray();
                        System.arraycopy(text.getBytes(StandardCharsets.US_ASCII), 0, bytes, (int) slot[0], (int) slot[1]);
                        model.reset();
                        model.writeBytes(bytes);
                    } else {
                        w.newline();
                        model.write('\n');
                    }
                }
            }
            assertArrayEquals(model.toByteArray(), Files.readAllBytes(f), "seed " + seed);
        }
    }

    @Test
    void aValueThatDoesNotFitItsSlotFailsLoudlyInsteadOfCorruptingTheLine() throws IOException {
        final Path f = dir.resolve("overflow.txt");
        try (PatchableFileWriter w = new PatchableFileWriter(f, 64)) {
            w.placeholder(6);
            assertThrows(IllegalStateException.class, () -> w.patchTicksAsMs(0, 10_000_000L, 6), "1000.0000 needs 9 chars");
            assertThrows(IllegalStateException.class, () -> w.patch(3, new byte[6]), "beyond the written region");
            w.patchTicksAsMs(0, 10_000, 6);
        }
        assertEquals("1.0000", Files.readString(f));
    }
}
