package io.github.yagipass.verbatime.cli;

final class CliException extends RuntimeException {

    static final int USAGE = 1;

    static final int UNREADABLE = 2;

    static final int CORRUPT = 3;

    private static final long serialVersionUID = 1L;

    private final int exitCode;

    private final String hint;

    CliException(final int exitCode, final String message, final String hint) {
        super(message);
        this.exitCode = exitCode;
        this.hint = hint;
    }

    static CliException usage(final String message, final String hint) {
        return new CliException(USAGE, message, hint);
    }

    int exitCode() {
        return exitCode;
    }

    String hint() {
        return hint;
    }
}
