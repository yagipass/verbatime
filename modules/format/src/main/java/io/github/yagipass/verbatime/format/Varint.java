package io.github.yagipass.verbatime.format;

public final class Varint {

    public static final int MAX_BYTES = 10;

    private Varint() {
    }

    public static int put(final byte[] b, int off, long v) {
        while ((v & ~0x7FL) != 0) {
            b[off++] = (byte) ((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        b[off++] = (byte) v;
        return off;
    }

    static int size(long v) {
        int n = 1;
        while ((v & ~0x7FL) != 0) {
            v >>>= 7;
            n++;
        }
        return n;
    }
}
