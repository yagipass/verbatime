package io.github.yagipass.verbatime.agent.probe;

import io.github.yagipass.verbatime.format.EventEncoder;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

final class Session {

  static final long EXIT = EventEncoder.EXIT_BIT;

  static final long THROW = EventEncoder.THROW_BIT;

  private static final int FIRST_CHUNK_EVENTS = 1 << 8;

  private static final int CHUNK_EVENTS = 1 << 14;

  private static final long FLUSH_INTERVAL_NANOS = 1_000_000_000L;

  private static final long[] NO_EVENTS = new long[0];

  private final TraceFileWriter writer;

  long[] buf;

  private final int maxWords;

  final Thread owner;

  final long tid;

  final int seq;

  final int rootId;

  int pos;

  int depth = -1;

  volatile boolean closed;

  @Nullable Throwable failure;

  boolean firstChunkPending = true;

  boolean truncatedByStop;

  long lastTicks;

  private long nextFlushNanos;

  Session(TraceFileWriter writer, int rootId, int seq) {
    this(writer, rootId, seq, FIRST_CHUNK_EVENTS, CHUNK_EVENTS);
  }

  Session(TraceFileWriter writer, int rootId, int seq, int chunkEvents) {
    this(writer, rootId, seq, chunkEvents, chunkEvents);
  }

  Session(TraceFileWriter writer, int rootId, int seq, int firstChunkEvents, int chunkEvents) {
    this.writer = writer;
    this.rootId = rootId;
    this.seq = seq;
    this.buf = new long[firstChunkEvents * 2];
    this.maxWords = chunkEvents * 2;
    this.owner = Thread.currentThread();
    this.tid = owner.threadId();
    this.nextFlushNanos = System.nanoTime() + FLUSH_INTERVAL_NANOS;
  }

  void enter(int id) {
    depth++;
    push(System.nanoTime(), (long) id << 2);
  }

  void exit(int id, long flags) {
    push(System.nanoTime(), ((long) id << 2) | flags);
    depth--;
  }

  void exit(int id, long flags, int exceptionId) {
    push(System.nanoTime(), ((long) exceptionId << 32) | ((long) id << 2) | flags);
    depth--;
  }

  void finish() {
    writer.appendChunk(this, true);
  }

  void flushTruncated() {
    closed = true;
    writer.flushTruncated(this);
    buf = NO_EVENTS;
  }

  private void push(long nanos, long packed) {
    long[] b = buf;
    int p = pos;
    b[p] = nanos;
    b[p + 1] = packed;
    pos = p + 2;
    if (pos == b.length && b.length < maxWords) {
      buf = Arrays.copyOf(b, Math.min(b.length * 2, maxWords));
    } else if (pos == b.length || nanos >= nextFlushNanos) {
      writer.appendChunk(this, false);
      nextFlushNanos = nanos + FLUSH_INTERVAL_NANOS;
    }
  }
}
