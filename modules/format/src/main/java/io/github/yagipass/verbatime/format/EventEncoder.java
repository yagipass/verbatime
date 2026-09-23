package io.github.yagipass.verbatime.format;

public final class EventEncoder {

    public static final int MAX_BYTES = Varint.MAX_BYTES + 5;

    public static final int EXIT_BIT = 1;

    public static final int THROW_BIT = 2;

    private EventEncoder() {
    }

    public static int enter(final byte[] b, int off, final long delta, final long methodId) {
        off = Varint.put(b, off, delta << 1);
        return Varint.put(b, off, methodId);
    }

    public static int exit(final byte[] b, final int off, final long delta) {
        return Varint.put(b, off, (delta << 2) | EXIT_BIT);
    }

    public static int exitThrow(final byte[] b, int off, final long delta, final long exceptionId) {
        off = Varint.put(b, off, (delta << 2) | THROW_BIT | EXIT_BIT);
        return Varint.put(b, off, exceptionId);
    }
}
