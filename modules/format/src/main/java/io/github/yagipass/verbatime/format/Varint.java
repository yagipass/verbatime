package io.github.yagipass.verbatime.format;

import com.google.errorprone.annotations.Var;

public final class Varint {

    public static final int MAX_BYTES = 10;

    private Varint() {
    }

    public static int put(byte[] b, @Var int off, @Var long v) {
        while ((v & ~0x7FL) != 0) {
            b[off++] = (byte) ((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        b[off++] = (byte) v;
        return off;
    }

    static int size(@Var long v) {
        @Var int n = 1;
        while ((v & ~0x7FL) != 0) {
            v >>>= 7;
            n++;
        }
        return n;
    }
}
