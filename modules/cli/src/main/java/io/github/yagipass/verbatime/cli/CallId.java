package io.github.yagipass.verbatime.cli;

record CallId(int session, long ordinal) {

    static CallId parse(final String ref) {
        final int dot = ref.indexOf('.');
        if (dot > 0) {
            try {
                final int session = Integer.parseInt(ref.substring(0, dot));
                final long ordinal = Long.parseLong(ref.substring(dot + 1));
                if (session > 0 && ordinal >= 0) {
                    return new CallId(session, ordinal);
                }
            } catch (final NumberFormatException notANumber) {
                throw notACallId(ref);
            }
        }
        throw notACallId(ref);
    }

    private static CliException notACallId(final String ref) {
        return CliException.usage("'" + ref + "' is not a call id",
                "call ids look like 7.57, the 57th call entered in session 7, where 7.0 is the root");
    }

    @Override
    public String toString() {
        return session + "." + ordinal;
    }
}
