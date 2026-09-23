package io.github.yagipass.verbatime.cli;

import java.util.List;

final class Scope {

    final List<TraceFile.Session> sessions;

    final String label;

    private Scope(final List<TraceFile.Session> sessions, final String label) {
        this.sessions = sessions;
        this.label = label;
    }

    static Scope of(final TraceFile file, final String sessionRef) {
        if (sessionRef == null) {
            final int n = file.sessions.size();
            return new Scope(file.sessions, n == 1 ? "the only session" : "all " + Formats.grouped(n) + " sessions");
        }
        final TraceFile.Session s = file.session(sessionRef);
        return new Scope(List.of(s), "session " + s.number);
    }
}
