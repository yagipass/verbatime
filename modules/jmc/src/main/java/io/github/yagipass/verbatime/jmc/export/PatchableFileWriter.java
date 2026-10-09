package io.github.yagipass.verbatime.jmc.export;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.Vbtm;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class PatchableFileWriter implements Closeable {

  private final FileChannel channel;

  private final byte[] buf;

  private int len;

  private long flushed;

  private long lines;

  private final byte[] digits = new byte[24];

  PatchableFileWriter(Path file, int bufferBytes) throws IOException {
    channel =
        FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
    buf = new byte[Math.max(bufferBytes, 16)];
  }

  @Override
  public void close() throws IOException {
    try {
      flushBuffer();
    } finally {
      channel.close();
    }
  }

  long position() {
    return flushed + len;
  }

  long lines() {
    return lines;
  }

  void put(int b) throws IOException {
    if (len == buf.length) {
      flushBuffer();
    }
    buf[len++] = (byte) b;
  }

  void bytes(byte[] b) throws IOException {
    bytes(b, 0, b.length);
  }

  void newline() throws IOException {
    put('\n');
    lines++;
  }

  void num(long v) throws IOException {
    int n = formatNum(v);
    bytes(digits, digits.length - n, n);
  }

  void writeTicksAsMs(long ticks) throws IOException {
    int n = formatMs(ticks);
    bytes(digits, digits.length - n, n);
  }

  void placeholder(int width) throws IOException {
    for (int i = 0; i < width; i++) {
      put('?');
    }
  }

  void patchTicksAsMs(long absOff, long ticks, int width) throws IOException {
    int n = formatMs(ticks);
    if (n > width) {
      throw new IllegalStateException(
          "value " + textOf(n) + " does not fit in " + width + " characters");
    }
    byte[] field = new byte[width];
    for (int i = 0; i < width - n; i++) {
      field[i] = ' ';
    }
    System.arraycopy(digits, digits.length - n, field, width - n, n);
    patch(absOff, field);
  }

  void patch(long absOff, byte[] src) throws IOException {
    long end = absOff + src.length;
    if (absOff < 0 || end > position()) {
      throw new IllegalStateException(
          "patch [" + absOff + "," + end + ") outside the written " + position());
    }
    if (absOff >= flushed) {
      System.arraycopy(src, 0, buf, (int) (absOff - flushed), src.length);
      return;
    }
    int direct = (int) Math.min(src.length, flushed - absOff);
    ByteBuffer bb = ByteBuffer.wrap(src, 0, direct);
    @Var long pos = absOff;
    while (bb.hasRemaining()) {
      pos += channel.write(bb, pos);
    }
    if (direct < src.length) {
      System.arraycopy(src, direct, buf, 0, src.length - direct);
    }
  }

  private void bytes(byte[] b, int off, int n) throws IOException {
    @Var int p = off;
    @Var int left = n;
    while (left > 0) {
      if (len == buf.length) {
        flushBuffer();
      }
      int k = Math.min(left, buf.length - len);
      System.arraycopy(b, p, buf, len, k);
      len += k;
      p += k;
      left -= k;
    }
  }

  private void flushBuffer() throws IOException {
    if (len == 0) {
      return;
    }
    ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
    while (bb.hasRemaining()) {
      channel.write(bb);
    }
    flushed += len;
    len = 0;
  }

  private int formatNum(@Var long v) {
    @Var int p = digits.length;
    if (v < 0) {
      v = 0;
    }
    do {
      digits[--p] = (byte) ('0' + (v % 10));
      v /= 10;
    } while (v > 0);
    return digits.length - p;
  }

  private int formatMs(@Var long ticks) {
    if (ticks < 0) {
      ticks = 0;
    }
    @Var int p = digits.length;
    @Var long frac = ticks % Vbtm.TICKS_PER_MS;
    for (int i = 0; i < 4; i++) {
      digits[--p] = (byte) ('0' + (frac % 10));
      frac /= 10;
    }
    digits[--p] = '.';
    @Var long whole = ticks / Vbtm.TICKS_PER_MS;
    do {
      digits[--p] = (byte) ('0' + (whole % 10));
      whole /= 10;
    } while (whole > 0);
    return digits.length - p;
  }

  private String textOf(int n) {
    return new String(digits, digits.length - n, n, StandardCharsets.US_ASCII);
  }
}
