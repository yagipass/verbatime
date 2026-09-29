package io.github.yagipass.verbatime.format;

import com.google.errorprone.annotations.Var;

public final class EventEncoder {

    public static final int MAX_BYTES = Varint.MAX_BYTES + 5;

    public static final int EXIT_BIT = 1;

    public static final int THROW_BIT = 2;

    private EventEncoder() {
    }

    public static int enter(byte[] b, @Var int off, long delta, long methodId) {
        off = Varint.put(b, off, delta << 1);
        return Varint.put(b, off, methodId);
    }

    public static int exit(byte[] b, int off, long delta) {
        return Varint.put(b, off, (delta << 2) | EXIT_BIT);
    }

    public static int exitThrow(byte[] b, @Var int off, long delta, long exceptionId) {
        off = Varint.put(b, off, (delta << 2) | THROW_BIT | EXIT_BIT);
        return Varint.put(b, off, exceptionId);
    }
}
