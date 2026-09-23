package io.github.yagipass.verbatime.jmc.export;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.github.yagipass.verbatime.format.Vbtm;

final class PatchableFileWriter implements Closeable {

    private final FileChannel channel;

    private final byte[] buf;

    private int len;

    private long flushed;

    private long lines;

    private final byte[] digits = new byte[24];

    PatchableFileWriter(final Path file, final int bufferBytes) throws IOException {
        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        buf = new byte[Math.max(bufferBytes, 16)];
    }

    long position() {
        return flushed + len;
    }

    long lines() {
        return lines;
    }

    void put(final int b) throws IOException {
        if (len == buf.length) {
            flushBuffer();
        }
        buf[len++] = (byte) b;
    }

    void bytes(final byte[] b) throws IOException {
        bytes(b, 0, b.length);
    }

    private void bytes(final byte[] b, final int off, final int n) throws IOException {
        int p = off;
        int left = n;
        while (left > 0) {
            if (len == buf.length) {
                flushBuffer();
            }
            final int k = Math.min(left, buf.length - len);
            System.arraycopy(b, p, buf, len, k);
            len += k;
            p += k;
            left -= k;
        }
    }

    void newline() throws IOException {
        put('\n');
        lines++;
    }

    void num(final long v) throws IOException {
        final int n = formatNum(v);
        bytes(digits, digits.length - n, n);
    }

    void writeTicksAsMs(final long ticks) throws IOException {
        final int n = formatMs(ticks);
        bytes(digits, digits.length - n, n);
    }

    void placeholder(final int width) throws IOException {
        for (int i = 0; i < width; i++) {
            put('?');
        }
    }

    void patchTicksAsMs(final long absOff, final long ticks, final int width) throws IOException {
        final int n = formatMs(ticks);
        if (n > width) {
            throw new IllegalStateException("value " + textOf(n) + " does not fit in " + width + " characters");
        }
        final byte[] field = new byte[width];
        for (int i = 0; i < width - n; i++) {
            field[i] = ' ';
        }
        System.arraycopy(digits, digits.length - n, field, width - n, n);
        patch(absOff, field);
    }

    void patch(final long absOff, final byte[] src) throws IOException {
        final long end = absOff + src.length;
        if (absOff < 0 || end > position()) {
            throw new IllegalStateException("patch [" + absOff + "," + end + ") outside the written " + position());
        }
        if (absOff >= flushed) {
            System.arraycopy(src, 0, buf, (int) (absOff - flushed), src.length);
            return;
        }
        final int direct = (int) Math.min(src.length, flushed - absOff);
        final ByteBuffer bb = ByteBuffer.wrap(src, 0, direct);
        long pos = absOff;
        while (bb.hasRemaining()) {
            pos += channel.write(bb, pos);
        }
        if (direct < src.length) {
            System.arraycopy(src, direct, buf, 0, src.length - direct);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            flushBuffer();
        } finally {
            channel.close();
        }
    }

    private void flushBuffer() throws IOException {
        if (len == 0) {
            return;
        }
        final ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
        while (bb.hasRemaining()) {
            channel.write(bb);
        }
        flushed += len;
        len = 0;
    }

    private int formatNum(long v) {
        int p = digits.length;
        if (v < 0) {
            v = 0;
        }
        do {
            digits[--p] = (byte) ('0' + (v % 10));
            v /= 10;
        } while (v > 0);
        return digits.length - p;
    }

    private int formatMs(long ticks) {
        if (ticks < 0) {
            ticks = 0;
        }
        int p = digits.length;
        long frac = ticks % Vbtm.TICKS_PER_MS;
        for (int i = 0; i < 4; i++) {
            digits[--p] = (byte) ('0' + (frac % 10));
            frac /= 10;
        }
        digits[--p] = '.';
        long whole = ticks / Vbtm.TICKS_PER_MS;
        do {
            digits[--p] = (byte) ('0' + (whole % 10));
            whole /= 10;
        } while (whole > 0);
        return digits.length - p;
    }

    private String textOf(final int n) {
        return new String(digits, digits.length - n, n, StandardCharsets.US_ASCII);
    }
}
