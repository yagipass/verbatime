package io.github.yagipass.verbatime.cli;

import org.jspecify.annotations.Nullable;

final class CliException extends RuntimeException {

    static final int USAGE = 1;

    static final int UNREADABLE = 2;

    static final int CORRUPT = 3;

    private static final long serialVersionUID = 1L;

    private final int exitCode;

    private final @Nullable String hint;

    CliException(int exitCode, String message, @Nullable String hint) {
        super(message);
        this.exitCode = exitCode;
        this.hint = hint;
    }

    static CliException usage(String message, @Nullable String hint) {
        return new CliException(USAGE, message, hint);
    }

    int exitCode() {
        return exitCode;
    }

    @Nullable
    String hint() {
        return hint;
    }
}
