package io.github.yagipass.verbatime.jmc.index;

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

public final class TraceSnapshot {

    public final Path path;

    public final MappedTrace buffer;

    public final boolean truncated;

    public final long corruptOffset;

    public final String corruptReason;

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

    private TraceSnapshot(final Builder b) {
        path = b.path;
        buffer = b.buffer;
        buffer.retain();
        truncated = b.truncated;
        corruptOffset = b.corruptOffset;
        corruptReason = b.corruptReason;
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
        final List<Session> ss = new ArrayList<>(b.sessions.size());
        for (final SessionState s : b.sessions) {
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

    public String methodName(final int id) {
        final String s = id >= 0 && id < methodNames.length ? methodNames[id] : null;
        return s != null ? s : "<unknown#" + id + ">";
    }

    public String exceptionName(final int id) {
        if (id == 0) {
            return "<unknown>";
        }
        final String s = id > 0 && id < exceptionNames.length ? exceptionNames[id] : null;
        return s != null ? s : "<unknown#" + id + ">";
    }

    public String threadName(final long tid) {
        final String s = threadNames.get(tid);
        return s != null ? s : "tid-" + tid;
    }

    public OffsetDateTime wallClock(final long ns) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(startEpochMs).plusNanos(ns),
                ZoneOffset.ofTotalSeconds(utcOffsetSeconds));
    }

    public ThreadIndex thread(final long tid) {
        for (final ThreadIndex t : threads) {
            if (t.tid == tid) {
                return t;
            }
        }
        return null;
    }

    public Session sessionAt(final long tid, final long ns) {
        for (final Session s : sessions) {
            if (s.tid == tid && ns >= s.startNs && ns <= s.startNs + s.durNs()) {
                return s;
            }
        }
        return null;
    }

    static final class Builder {

        Path path;

        MappedTrace buffer;

        public boolean truncated;

        public long corruptOffset = -1;

        public String corruptReason;

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

        Builder copy() {
            final Builder c = new Builder();
            c.path = path;
            c.buffer = buffer;
            c.truncated = truncated;
            c.corruptOffset = corruptOffset;
            c.corruptReason = corruptReason;
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
            for (final SessionState s : sessions) {
                c.sessions.add(new SessionState(s));
            }
            c.callsByMethod = callsByMethod.clone();
            c.exceptionNames = exceptionNames.clone();
            c.totalExceptions = totalExceptions;
            c.gc.copyFrom(gc);
            return c;
        }

        TraceSnapshot build() {
            return new TraceSnapshot(this);
        }
    }

    public static final class GcPauses {

        public final int count;

        public final long[] startNs;

        public final long[] durNs;

        public final int[] action;

        public final String[] collector;

        public final String[] cause;

        public final long totalNs;

        public record Overlap(long ns, int pauses) {
        }

        private GcPauses(final Builder b) {
            count = b.count;
            startNs = b.startNs;
            durNs = b.durNs;
            action = b.action;
            collector = b.collector;
            cause = b.cause;
            totalNs = b.totalNs;
        }

        public Overlap overlap(final long frameStartNs, final long frameDurNs) {
            final long frameEnd = frameStartNs + frameDurNs;
            long ns = 0;
            int n = 0;
            for (int i = 0; i < count; i++) {
                final long a = Math.max(startNs[i], frameStartNs);
                final long b = Math.min(startNs[i] + durNs[i], frameEnd);
                if (b > a) {
                    ns += b - a;
                    n++;
                }
            }
            return new Overlap(ns, n);
        }

        static final class Builder {

            private int count;

            private long[] startNs = new long[16];

            private long[] durNs = new long[16];

            private int[] action = new int[16];

            private String[] collector = new String[16];

            private String[] cause = new String[16];

            private long totalNs;

            private Builder() {
            }

            void append(final long start, final long dur, final int act, final String collector, final String why) {
                if (count == startNs.length) {
                    final int cap = count * 2;
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

            private void copyFrom(final Builder src) {
                count = src.count;
                totalNs = src.totalNs;
                final int cap = Math.max(count, 16);
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

        SessionState(final int seq, final long tid, final long startNs) {
            this.seq = seq;
            this.tid = tid;
            this.startNs = startNs;
        }

        private SessionState(final SessionState src) {
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

        private Session(final SessionState s) {
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

        ThreadIndex(final long tid, final int maxDepth, final long totalCalls, final ChunkTable chunks,
                final Calls overview) {
            this.tid = tid;
            this.maxDepth = maxDepth;
            this.totalCalls = totalCalls;
            this.chunks = chunks;
            this.overview = overview;
        }
    }
}
