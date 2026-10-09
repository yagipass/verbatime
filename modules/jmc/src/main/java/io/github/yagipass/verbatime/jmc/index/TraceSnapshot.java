package io.github.yagipass.verbatime.jmc.index;

import com.google.errorprone.annotations.Var;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

public final class TraceSnapshot {

  public record Corruption(long offset, String reason) {}

  static final class Builder {

    final Path path;

    public boolean truncated;

    public @Nullable Corruption corruption;

    public long startEpochMs;

    public int utcOffsetSeconds;

    public long minNs;

    public long maxNs;

    public long totalCalls;

    public long overviewThresholdNs;

    public long generation;

    String[] methodNames = new String[0];

    public int totalMethods;

    final Map<Long, String> threadNames = new LinkedHashMap<>();

    final List<SessionState> sessions = new ArrayList<>();

    public List<ThreadIndex> threads = new ArrayList<>();

    long[] callsByMethod = new long[0];

    public String[] exceptionNames = new String[0];

    public int totalExceptions;

    final GcPauses.Builder gc = new GcPauses.Builder();

    Builder(Path path) {
      this.path = path;
    }

    Builder copy() {
      Builder c = new Builder(path);
      c.truncated = truncated;
      c.corruption = corruption;
      c.startEpochMs = startEpochMs;
      c.utcOffsetSeconds = utcOffsetSeconds;
      c.minNs = minNs;
      c.maxNs = maxNs;
      c.totalCalls = totalCalls;
      c.overviewThresholdNs = overviewThresholdNs;
      c.generation = generation;
      c.methodNames = methodNames.clone();
      c.totalMethods = totalMethods;
      c.threadNames.putAll(threadNames);
      for (SessionState s : sessions) {
        c.sessions.add(new SessionState(s));
      }
      c.callsByMethod = callsByMethod.clone();
      c.exceptionNames = exceptionNames.clone();
      c.totalExceptions = totalExceptions;
      c.gc.copyFrom(gc);
      return c;
    }

    TraceSnapshot build(MappedTrace buffer) {
      return new TraceSnapshot(this, buffer);
    }
  }

  public static final class GcPauses {

    public record Overlap(long ns, int pauses) {}

    static final class Builder {

      private int count;

      private long[] startNs = new long[16];

      private long[] durNs = new long[16];

      private int[] action = new int[16];

      private String[] collector = new String[16];

      private String[] cause = new String[16];

      private long totalNs;

      private Builder() {}

      void append(long start, long dur, int act, String collector, String why) {
        if (count == startNs.length) {
          int cap = count * 2;
          startNs = Arrays.copyOf(startNs, cap);
          durNs = Arrays.copyOf(durNs, cap);
          action = Arrays.copyOf(action, cap);
          this.collector = Arrays.copyOf(this.collector, cap);
          cause = Arrays.copyOf(cause, cap);
        }
        startNs[count] = start;
        durNs[count] = dur;
        action[count] = act;
        this.collector[count] = collector;
        cause[count] = why;
        count++;
        totalNs += dur;
      }

      private void copyFrom(Builder src) {
        count = src.count;
        totalNs = src.totalNs;
        int cap = Math.max(count, 16);
        startNs = Arrays.copyOf(src.startNs, cap);
        durNs = Arrays.copyOf(src.durNs, cap);
        action = Arrays.copyOf(src.action, cap);
        collector = Arrays.copyOf(src.collector, cap);
        cause = Arrays.copyOf(src.cause, cap);
      }

      private GcPauses build() {
        return new GcPauses(this);
      }
    }

    public final int count;

    public final long[] startNs;

    public final long[] durNs;

    public final int[] action;

    public final String[] collector;

    public final String[] cause;

    public final long totalNs;

    private GcPauses(Builder b) {
      count = b.count;
      startNs = b.startNs;
      durNs = b.durNs;
      action = b.action;
      collector = b.collector;
      cause = b.cause;
      totalNs = b.totalNs;
    }

    public Overlap overlap(long frameStartNs, long frameDurNs) {
      long frameEnd = frameStartNs + frameDurNs;
      @Var long ns = 0;
      @Var int n = 0;
      for (int i = 0; i < count; i++) {
        long a = Math.max(startNs[i], frameStartNs);
        long b = Math.min(startNs[i] + durNs[i], frameEnd);
        if (b > a) {
          ns += b - a;
          n++;
        }
      }
      return new Overlap(ns, n);
    }
  }

  static final class SessionState {

    final int seq;

    private final long tid;

    int rootMethodId = -1;

    private final long startNs;

    long endNs;

    boolean ended;

    long callCount;

    int firstChunk = -1;

    int lastChunk = -1;

    SessionState(int seq, long tid, long startNs) {
      this.seq = seq;
      this.tid = tid;
      this.startNs = startNs;
    }

    private SessionState(SessionState src) {
      this(src.seq, src.tid, src.startNs);
      rootMethodId = src.rootMethodId;
      endNs = src.endNs;
      ended = src.ended;
      callCount = src.callCount;
      firstChunk = src.firstChunk;
      lastChunk = src.lastChunk;
    }

    private Session freeze() {
      return new Session(this);
    }
  }

  public static final class Session {

    public final int seq;

    public final long tid;

    public final int rootMethodId;

    public final long startNs;

    public final long endNs;

    public final boolean ended;

    public final long callCount;

    public final int firstChunk;

    public final int lastChunk;

    private Session(SessionState s) {
      seq = s.seq;
      tid = s.tid;
      rootMethodId = s.rootMethodId;
      startNs = s.startNs;
      endNs = s.endNs;
      ended = s.ended;
      callCount = s.callCount;
      firstChunk = s.firstChunk;
      lastChunk = s.lastChunk;
    }

    public long durNs() {
      return Math.max(endNs - startNs, 0);
    }
  }

  public static final class ThreadIndex {

    public final long tid;

    public final int maxDepth;

    public final long totalCalls;

    public final ChunkTable chunks;

    public final Calls overview;

    ThreadIndex(long tid, int maxDepth, long totalCalls, ChunkTable chunks, Calls overview) {
      this.tid = tid;
      this.maxDepth = maxDepth;
      this.totalCalls = totalCalls;
      this.chunks = chunks;
      this.overview = overview;
    }
  }

  public final Path path;

  public final MappedTrace buffer;

  public final boolean truncated;

  public final @Nullable Corruption corruption;

  public final long startEpochMs;

  public final int utcOffsetSeconds;

  public final long minNs;

  public final long maxNs;

  public final long totalCalls;

  public final long overviewThresholdNs;

  public final long generation;

  public final String[] methodNames;

  public final int totalMethods;

  final Map<Long, String> threadNames;

  public final List<Session> sessions;

  public final List<ThreadIndex> threads;

  public final long[] callsByMethod;

  public final String[] exceptionNames;

  public final int totalExceptions;

  public final GcPauses gc;

  private final AtomicBoolean released = new AtomicBoolean();

  private TraceSnapshot(Builder b, MappedTrace buffer) {
    path = b.path;
    this.buffer = buffer;
    buffer.retain();
    truncated = b.truncated;
    corruption = b.corruption;
    startEpochMs = b.startEpochMs;
    utcOffsetSeconds = b.utcOffsetSeconds;
    minNs = b.minNs;
    maxNs = b.maxNs;
    totalCalls = b.totalCalls;
    overviewThresholdNs = b.overviewThresholdNs;
    generation = b.generation;
    methodNames = b.methodNames;
    totalMethods = b.totalMethods;
    threadNames = Collections.unmodifiableMap(new LinkedHashMap<>(b.threadNames));
    List<Session> ss = new ArrayList<>(b.sessions.size());
    for (SessionState s : b.sessions) {
      ss.add(s.freeze());
    }
    sessions = List.copyOf(ss);
    threads = List.copyOf(b.threads);
    callsByMethod = b.callsByMethod;
    exceptionNames = b.exceptionNames;
    totalExceptions = b.totalExceptions;
    gc = b.gc.build();
  }

  public void release() {
    if (released.compareAndSet(false, true)) {
      buffer.release();
    }
  }

  public String methodName(int id) {
    String s = id >= 0 && id < methodNames.length ? methodNames[id] : null;
    return s != null ? s : "<unknown#" + id + ">";
  }

  public String exceptionName(int id) {
    if (id == 0) {
      return "<unknown>";
    }
    String s = id > 0 && id < exceptionNames.length ? exceptionNames[id] : null;
    return s != null ? s : "<unknown#" + id + ">";
  }

  public String threadName(long tid) {
    String s = threadNames.get(tid);
    return s != null ? s : "tid-" + tid;
  }

  public OffsetDateTime wallClock(long ns) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(startEpochMs).plusNanos(ns),
        ZoneOffset.ofTotalSeconds(utcOffsetSeconds));
  }

  public @Nullable ThreadIndex thread(long tid) {
    for (ThreadIndex t : threads) {
      if (t.tid == tid) {
        return t;
      }
    }
    return null;
  }

  public @Nullable Session sessionAt(long tid, long ns) {
    for (Session s : sessions) {
      if (s.tid == tid && ns >= s.startNs && ns <= s.startNs + s.durNs()) {
        return s;
      }
    }
    return null;
  }
}
