package io.github.yagipass.verbatime.format;

public final class CorruptTraceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long offset;

    CorruptTraceException(final long offset, final String message) {
        super(message);
        this.offset = offset;
    }

    public long offset() {
        return offset;
    }
}
