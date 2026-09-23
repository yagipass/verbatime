package io.github.yagipass.verbatime.agent;

import io.github.yagipass.verbatime.agent.probe.TraceFileWriter;

public final class Recording {

    private final long id;

    private final String name;

    private final TraceFileWriter writer;

    private final boolean spooled;

    private volatile boolean closed;

    private volatile boolean delivered;

    Recording(final long id, final String name, final TraceFileWriter writer, final boolean spooled) {
        this.id = id;
        this.name = name;
        this.writer = writer;
        this.spooled = spooled;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TraceFileWriter writer() {
        return writer;
    }

    public boolean spooled() {
        return spooled;
    }

    public boolean closed() {
        return closed;
    }

    void markClosed() {
        closed = true;
    }

    public boolean delivered() {
        return delivered;
    }

    public void markDelivered() {
        delivered = true;
    }
}
