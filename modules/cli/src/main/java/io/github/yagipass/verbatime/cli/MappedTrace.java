package io.github.yagipass.verbatime.cli;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class MappedTrace {

  private static final int REGION_SHIFT = 30;

  private static final long REGION_SIZE = 1L << REGION_SHIFT;

  private final MappedByteBuffer[] regions;

  private final long size;

  private MappedTrace(MappedByteBuffer[] regions, long size) {
    this.regions = regions;
    this.size = size;
  }

  static MappedTrace open(Path path) throws IOException {
    try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
      long size = ch.size();
      int n = (int) ((size + REGION_SIZE - 1) / REGION_SIZE);
      MappedByteBuffer[] regions = new MappedByteBuffer[n];
      for (int i = 0; i < n; i++) {
        long off = i * REGION_SIZE;
        regions[i] = ch.map(FileChannel.MapMode.READ_ONLY, off, Math.min(REGION_SIZE, size - off));
      }
      return new MappedTrace(regions, size);
    }
  }

  long size() {
    return size;
  }

  int byteAt(long pos) {
    return regions[(int) (pos >>> REGION_SHIFT)].get((int) (pos & (REGION_SIZE - 1))) & 0xFF;
  }

  void copy(long pos, byte[] dst, int len) {
    @Var int done = 0;
    while (done < len) {
      long p = pos + done;
      int ri = (int) (p >>> REGION_SHIFT);
      int ro = (int) (p & (REGION_SIZE - 1));
      int take = (int) Math.min(len - done, REGION_SIZE - ro);
      regions[ri].get(ro, dst, done, take);
      done += take;
    }
  }
}
