package io.github.yagipass.verbatime.jmc.index;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

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

    private MappedByteBuffer[] regions;

    private final long size;

    private MappedTrace(final MappedByteBuffer[] regions, final long size) {
        this.regions = regions;
        this.size = size;
    }

    static MappedTrace open(final Path path) throws IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            final long size = ch.size();
            final int n = (int) ((size + REGION_SIZE - 1) / REGION_SIZE);
            final MappedByteBuffer[] regions = new MappedByteBuffer[Math.max(n, 1)];
            try {
                for (int i = 0; i < regions.length; i++) {
                    final long off = i * REGION_SIZE;
                    final long len = Math.min(REGION_SIZE, size - off);
                    regions[i] = ch.map(FileChannel.MapMode.READ_ONLY, off, len);
                }
            } catch (final IOException | RuntimeException e) {
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
        int n;
        do {
            n = refs.get();
            if (n == 0) {
                throw new ClosedException();
            }
        } while (!refs.compareAndSet(n, n + 1));
    }

    public void release() {
        final int n = refs.decrementAndGet();
        if (n == 0) {
            final MappedByteBuffer[] r = regions;
            regions = null;
            unmapAll(r);
        } else if (n < 0) {
            throw new IllegalStateException("trace buffer released more times than retained");
        }
    }

    int byteAt(final long pos) {
        return regions[(int) (pos >>> REGION_SHIFT)].get((int) (pos & (REGION_SIZE - 1))) & 0xFF;
    }

    void copy(final long pos, final byte[] dst, final int len) {
        int done = 0;
        while (done < len) {
            final long p = pos + done;
            final int ri = (int) (p >>> REGION_SHIFT);
            final int ro = (int) (p & (REGION_SIZE - 1));
            final int take = (int) Math.min(len - done, REGION_SIZE - ro);
            regions[ri].get(ro, dst, done, take);
            done += take;
        }
    }

    private static void unmapAll(final MappedByteBuffer[] regions) {
        for (final MappedByteBuffer r : regions) {
            if (r != null) {
                Unmapper.unmap(r);
            }
        }
    }

    private static final class Unmapper {

        private static final Object UNSAFE;

        private static final Method INVOKE_CLEANER;

        static {
            Object unsafe = null;
            Method invoke = null;
            try {
                final Class<?> c = Class.forName("sun.misc.Unsafe");
                final Field f = c.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                unsafe = f.get(null);
                invoke = c.getMethod("invokeCleaner", ByteBuffer.class);
                invoke.invoke(unsafe, ByteBuffer.allocateDirect(1));
            } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
                unsafe = null;
                invoke = null;
            }
            UNSAFE = unsafe;
            INVOKE_CLEANER = invoke;
        }

        private Unmapper() {
        }

        private static void unmap(final MappedByteBuffer region) {
            if (INVOKE_CLEANER == null) {
                return;
            }
            try {
                INVOKE_CLEANER.invoke(UNSAFE, region);
            } catch (final ReflectiveOperationException | RuntimeException e) {
                return;
            }
        }
    }
}
