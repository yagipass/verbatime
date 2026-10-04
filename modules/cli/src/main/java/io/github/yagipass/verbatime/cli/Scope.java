package io.github.yagipass.verbatime.cli;

import java.util.List;

import org.jspecify.annotations.Nullable;

final class Scope {

    final List<TraceFile.Session> sessions;

    final String label;

    private Scope(List<TraceFile.Session> sessions, String label) {
        this.sessions = sessions;
        this.label = label;
    }

    static Scope of(TraceFile file, @Nullable String sessionRef) {
        if (sessionRef == null) {
            int n = file.sessions.size();
            return new Scope(file.sessions, n == 1 ? "the only session" : "all " + Formats.grouped(n) + " sessions");
        }
        TraceFile.Session s = file.session(sessionRef);
        return new Scope(List.of(s), "session " + s.number);
    }
}
