package io.github.yagipass.verbatime.agent.probe;

import io.github.yagipass.verbatime.format.EventEncoder;

final class Session {

    static final long EXIT = EventEncoder.EXIT_BIT;

    static final long THROW = EventEncoder.THROW_BIT;

    private static final int CHUNK_EVENTS = 1 << 14;

    private static final long FLUSH_INTERVAL_NANOS = 1_000_000_000L;

    private final TraceFileWriter writer;

    final long[] buf;

    final Thread owner;

    final long tid;

    final int seq;

    final int rootId;

    int pos;

    int depth = -1;

    volatile boolean closed;

    Throwable failure;

    boolean firstChunkPending = true;

    boolean truncatedByStop;

    long lastTicks;

    private long nextFlushNanos;

    Session(final TraceFileWriter writer, final int rootId, final int seq) {
        this(writer, rootId, seq, CHUNK_EVENTS);
    }

    Session(final TraceFileWriter writer, final int rootId, final int seq, final int chunkEvents) {
        this.writer = writer;
        this.rootId = rootId;
        this.seq = seq;
        this.buf = new long[chunkEvents * 2];
        this.owner = Thread.currentThread();
        this.tid = owner.threadId();
        this.nextFlushNanos = System.nanoTime() + FLUSH_INTERVAL_NANOS;
    }

    void enter(final int id) {
        depth++;
        push(System.nanoTime(), (long) id << 2);
    }

    void exit(final int id, final long flags) {
        push(System.nanoTime(), ((long) id << 2) | flags);
        depth--;
    }

    void exit(final int id, final long flags, final int exceptionId) {
        push(System.nanoTime(), ((long) exceptionId << 32) | ((long) id << 2) | flags);
        depth--;
    }

    private void push(final long nanos, final long packed) {
        final long[] b = buf;
        final int p = pos;
        b[p] = nanos;
        b[p + 1] = packed;
        pos = p + 2;
        if (pos == b.length || nanos >= nextFlushNanos) {
            writer.appendChunk(this, false);
            nextFlushNanos = nanos + FLUSH_INTERVAL_NANOS;
        }
    }

    void finish() {
        writer.appendChunk(this, true);
    }

    void flushTruncated() {
        closed = true;
        writer.flushTruncated(this);
    }
}
