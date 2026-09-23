package io.github.yagipass.verbatime.jmc.index;

final class DecodeScratch {

    private static final int MIN_BYTES = 1 << 16;

    private static final ThreadLocal<byte[]> FREE = new ThreadLocal<>();

    private DecodeScratch() {
    }

    static byte[] take() {
        final byte[] b = FREE.get();
        if (b == null) {
            return new byte[MIN_BYTES];
        }
        FREE.set(null);
        return b;
    }

    static byte[] allocate(final int len) {
        return new byte[Math.max(Integer.highestOneBit(len) * 2, MIN_BYTES)];
    }

    static void give(final byte[] b) {
        if (b != null) {
            FREE.set(b);
        }
    }
}
