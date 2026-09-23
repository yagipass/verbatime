package io.github.yagipass.verbatime.examples.workload;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AuditLog {

    private static final int KEEP = 64;

    private final Deque<String> recent = new ArrayDeque<>();

    public synchronized void append(final String line) {
        Work.cpu(line, 300);
        recent.addLast(line);
        while (recent.size() > KEEP) {
            recent.removeFirst();
        }
    }
}
