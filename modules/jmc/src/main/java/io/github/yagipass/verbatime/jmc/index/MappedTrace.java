package io.github.yagipass.verbatime.jmc.index;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

public final class MappedTrace {

  static final class ClosedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private ClosedException() {
      super("trace buffer already unmapped");
    }
  }

  private static final int REGION_SHIFT = 30;

  private static final long REGION_SIZE = 1L << REGION_SHIFT;

  private final AtomicInteger refs = new AtomicInteger(1);

  private MappedByteBuffer @Nullable [] regions;

  private final long size;

  private MappedTrace(MappedByteBuffer[] regions, long size) {
    this.regions = regions;
    this.size = size;
  }

  static MappedTrace open(Path path) throws IOException {
    try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
      long size = ch.size();
      int n = (int) ((size + REGION_SIZE - 1) / REGION_SIZE);
      MappedByteBuffer[] regions = new MappedByteBuffer[Math.max(n, 1)];
      try {
        for (int i = 0; i < regions.length; i++) {
          long off = i * REGION_SIZE;
          long len = Math.min(REGION_SIZE, size - off);
          regions[i] = ch.map(FileChannel.MapMode.READ_ONLY, off, len);
        }
      } catch (IOException | RuntimeException e) {
        unmapAll(regions);
        throw e;
      }
      return new MappedTrace(regions, size);
    }
  }

  public static boolean unmapSupported() {
    return Unmapper.INVOKE_CLEANER != null;
  }

  long size() {
    return size;
  }

  boolean isClosed() {
    return refs.get() == 0;
  }

  public void retain() {
    @Var int n;
    do {
      n = refs.get();
      if (n == 0) {
        throw new ClosedException();
      }
    } while (!refs.compareAndSet(n, n + 1));
  }

  public void release() {
    int n = refs.decrementAndGet();
    if (n == 0) {
      MappedByteBuffer[] r = Objects.requireNonNull(regions);
      regions = null;
      unmapAll(r);
    } else if (n < 0) {
      throw new IllegalStateException("trace buffer released more times than retained");
    }
  }

  int byteAt(long pos) {
    return Objects.requireNonNull(regions)[(int) (pos >>> REGION_SHIFT)].get(
            (int) (pos & (REGION_SIZE - 1)))
        & 0xFF;
  }

  void copy(long pos, byte[] dst, int len) {
    @Var int done = 0;
    while (done < len) {
      long p = pos + done;
      int ri = (int) (p >>> REGION_SHIFT);
      int ro = (int) (p & (REGION_SIZE - 1));
      int take = (int) Math.min(len - done, REGION_SIZE - ro);
      Objects.requireNonNull(regions)[ri].get(ro, dst, done, take);
      done += take;
    }
  }

  private static void unmapAll(MappedByteBuffer[] regions) {
    for (MappedByteBuffer r : regions) {
      if (r != null) {
        Unmapper.unmap(r);
      }
    }
  }

  private static final class Unmapper {

    private static final @Nullable Object UNSAFE;

    private static final @Nullable Method INVOKE_CLEANER;

    static {
      @Var Object unsafe = null;
      @Var Method invoke = null;
      try {
        Class<?> c = Class.forName("sun.misc.Unsafe");
        Field f = c.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = f.get(null);
        invoke = c.getMethod("invokeCleaner", ByteBuffer.class);
        invoke.invoke(unsafe, ByteBuffer.allocateDirect(1));
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
        unsafe = null;
        invoke = null;
      }
      UNSAFE = unsafe;
      INVOKE_CLEANER = invoke;
    }

    private Unmapper() {}

    private static void unmap(MappedByteBuffer region) {
      if (INVOKE_CLEANER == null) {
        return;
      }
      try {
        INVOKE_CLEANER.invoke(UNSAFE, region);
      } catch (ReflectiveOperationException | RuntimeException e) {
        return;
      }
    }
  }
}
