package io.github.yagipass.verbatime.jmc.index;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.FrameStack;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.SessionState;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

public final class TraceIndexer {

    public static final int DEFAULT_OVERVIEW_BUDGET = 120_000;

    public interface ProgressListener {

        ProgressListener NONE = (done, total) -> false;

        boolean report(long bytesDone, long bytesTotal);
    }

    public static final class CancelledException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public CancelledException() {
            super(null, null, false, false);
        }
    }

    public static final class NotTraceFormatException extends IOException {

        private static final long serialVersionUID = 1L;

        private NotTraceFormatException(final String message) {
            super(message);
        }
    }

    static TraceSnapshot index(final Path path) throws IOException {
        return index(path, DEFAULT_OVERVIEW_BUDGET, ProgressListener.NONE);
    }

    static TraceSnapshot index(final Path path, final int overviewBudget, final ProgressListener progress)
            throws IOException {
        final TraceIndexer ix = open(path, overviewBudget);
        try {
            ix.advance(progress);
            return ix.snapshot();
        } finally {
            ix.close();
        }
    }

    public static TraceIndexer open(final Path path, final int overviewBudget) {
        return new TraceIndexer(path, overviewBudget);
    }

    private static final AtomicLong GENERATIONS = new AtomicLong();

    private final Path path;

    private final int overviewBudget;

    private final Object lock = new Object();

    private boolean closed;

    private MappedTrace buf;

    private long size;

    private long pos;

    private TraceSnapshot.Builder data;

    private long[] durationHistogram;

    private Map<Long, ThreadState> states;

    private byte[] scratch = new byte[1 << 16];

    private final EventCursor eventCursor = new EventCursor();

    private long overviewThresholdNs;

    private long overviewTotal;

    private long compactTrigger;

    private long minNs;

    private long maxNs;

    private int recordCounter;

    private boolean endSeen;

    private long endOffset;

    private boolean magicChecked;

    private boolean versionChecked;

    private boolean anchorRead;

    private byte[] header;

    private final Map<String, String> gcLabels = new HashMap<>();

    private static final class ThreadState {

        private final long tid;

        private SessionState session;

        private long lastTicks;

        private final FrameStack stack;

        private int maxDepth;

        private long totalCalls;

        private final ChunkTable chunks;

        private final Calls overview;

        private ThreadState(final long tid) {
            this.tid = tid;
            stack = new FrameStack();
            chunks = new ChunkTable();
            overview = new Calls(tid);
        }

        private ThreadState(final ThreadState src, final List<SessionState> sessions) {
            tid = src.tid;
            session = src.session == null ? null : sessions.get(src.session.seq - 1);
            lastTicks = src.lastTicks;
            stack = src.stack.copy();
            maxDepth = src.maxDepth;
            totalCalls = src.totalCalls;
            chunks = src.chunks.copy();
            overview = src.overview.copy();
        }

        private ThreadIndex freeze() {
            return new ThreadIndex(tid, maxDepth, totalCalls, chunks, overview);
        }
    }

    private TraceIndexer(final Path path, final int overviewBudget) {
        this.path = path;
        this.overviewBudget = Math.max(overviewBudget, 1);
        reset();
    }

    private TraceIndexer(final TraceIndexer src) {
        path = src.path;
        overviewBudget = src.overviewBudget;
        buf = src.buf;
        size = src.size;
        pos = src.pos;
        data = src.data.copy();
        durationHistogram = src.durationHistogram.clone();
        states = new HashMap<>();
        for (final Map.Entry<Long, ThreadState> e : src.states.entrySet()) {
            states.put(e.getKey(), new ThreadState(e.getValue(), data.sessions));
        }
        overviewThresholdNs = src.overviewThresholdNs;
        overviewTotal = src.overviewTotal;
        compactTrigger = src.compactTrigger;
        minNs = src.minNs;
        maxNs = src.maxNs;
        endSeen = src.endSeen;
        endOffset = src.endOffset;
        magicChecked = src.magicChecked;
        versionChecked = src.versionChecked;
        anchorRead = src.anchorRead;
    }

    private void reset() {
        data = new TraceSnapshot.Builder();
        data.path = path;
        data.generation = GENERATIONS.incrementAndGet();
        durationHistogram = new long[DurationHistogram.SIZE];
        states = new HashMap<>();
        pos = 0;
        overviewThresholdNs = 0;
        overviewTotal = 0;
        compactTrigger = 2L * overviewBudget;
        minNs = Long.MAX_VALUE;
        maxNs = 0;
        endSeen = false;
        endOffset = 0;
        magicChecked = false;
        versionChecked = false;
        anchorRead = false;
    }

    public void close() {
        final MappedTrace b;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            b = buf;
        }
        if (b != null) {
            b.release();
        }
    }

    private MappedTrace acquire() {
        synchronized (lock) {
            if (closed) {
                throw new CancelledException();
            }
            if (buf != null) {
                buf.retain();
            }
            return buf;
        }
    }

    public void advance(final ProgressListener progress) throws IOException {
        MappedTrace guard = acquire();
        try {
            final long fileSize = Files.size(path);
            if (guard == null || fileSize != size) {
                guard = remap(guard);
                if (size < pos || headerChanged()) {
                    reset();
                }
                data.buffer = buf;
            }
            if (data.corruptOffset >= 0) {
                return;
            }
            if (!magicChecked) {
                checkMagic();
            }
            if (!versionChecked && !checkVersion()) {
                return;
            }
            if (!anchorRead && !readAnchor()) {
                return;
            }
            try {
                scan(false, progress, pos);
            } catch (final CorruptException c) {
                corrupt(c.offset, c.reason);
            }
        } finally {
            if (guard != null) {
                guard.release();
            }
        }
    }

    private MappedTrace remap(final MappedTrace guard) throws IOException {
        final MappedTrace fresh = MappedTrace.open(path);
        final MappedTrace old;
        synchronized (lock) {
            if (closed) {
                fresh.release();
                throw new CancelledException();
            }
            old = buf;
            buf = fresh;
            fresh.retain();
        }
        size = fresh.size();
        if (guard != null) {
            guard.release();
        }
        if (old != null) {
            old.release();
        }
        return fresh;
    }

    public TraceSnapshot snapshot() {
        final MappedTrace guard = acquire();
        if (guard == null) {
            throw new IllegalStateException("advance() before snapshot()");
        }
        try {
            final TraceIndexer c = new TraceIndexer(this);
            c.complete();
            return c.data.build();
        } finally {
            guard.release();
        }
    }

    private void checkMagic() throws NotTraceFormatException {
        if (size < Vbtm.MAGIC_BYTES) {
            throw new NotTraceFormatException(path.getFileName() + ": file shorter than the " + Vbtm.MAGIC_BYTES
                    + "-byte magic \"" + Vbtm.MAGIC + "\"");
        }
        final byte[] magic = new byte[Vbtm.MAGIC_BYTES];
        buf.copy(0, magic, magic.length);
        if (!Vbtm.hasMagic(magic, 0, magic.length)) {
            throw new NotTraceFormatException(
                    path.getFileName() + ": not a supported recording, expected the magic \"" + Vbtm.MAGIC + "\"");
        }
        pos = Vbtm.MAGIC_BYTES;
        magicChecked = true;
    }

    private boolean checkVersion() throws NotTraceFormatException {
        if (size <= Vbtm.VERSION_OFFSET) {
            return false;
        }
        final int version = buf.byteAt(Vbtm.VERSION_OFFSET);
        if (version != Vbtm.VERSION) {
            throw new NotTraceFormatException(path.getFileName() + ": format version " + version
                    + " is not supported, this plugin reads version " + Vbtm.VERSION);
        }
        pos = Vbtm.ANCHOR_OFFSET;
        versionChecked = true;
        return true;
    }

    private boolean readAnchor() {
        final long at = Vbtm.ANCHOR_OFFSET;
        if (size <= at) {
            return false;
        }
        if (buf.byteAt(at) != Vbtm.RECORD_ANCHOR) {
            corrupt(at, "expected the anchor record at offset " + at);
            return false;
        }
        if (size < Vbtm.HEADER_BYTES) {
            return false;
        }
        final byte[] body = new byte[Vbtm.ANCHOR_BYTES - 1];
        buf.copy(at + 1, body, body.length);
        final ByteBuffer b = ByteBuffer.wrap(body);
        final long epochMs = b.getLong();
        final int offsetSeconds = b.getInt();
        if (offsetSeconds < -Vbtm.MAX_UTC_OFFSET_SECONDS || offsetSeconds > Vbtm.MAX_UTC_OFFSET_SECONDS) {
            corrupt(at, "UTC offset " + offsetSeconds + " s out of range in the anchor record at offset " + at);
            return false;
        }
        data.startEpochMs = epochMs;
        data.utcOffsetSeconds = offsetSeconds;
        header = new byte[Vbtm.HEADER_BYTES];
        buf.copy(0, header, header.length);
        pos = Vbtm.HEADER_BYTES;
        anchorRead = true;
        return true;
    }

    private boolean headerChanged() {
        if (!anchorRead) {
            return false;
        }
        final byte[] now = new byte[Vbtm.HEADER_BYTES];
        buf.copy(0, now, now.length);
        return !Arrays.equals(header, now);
    }

    private void complete() {
        if (data.corruptOffset < 0 && !anchorRead) {
            data.truncated = true;
        } else if (data.corruptOffset < 0) {
            try {
                scan(true, ProgressListener.NONE, pos);
                if (!endSeen && data.corruptOffset < 0) {
                    data.truncated = true;
                }
            } catch (final TruncatedException t) {
                data.truncated = true;
            } catch (final CorruptException c) {
                corrupt(c.offset, c.reason);
            }
        }
        finish();
    }

    private void scan(final boolean partialOk, final ProgressListener progress, final long start) {
        while (pos < size) {
            if (endSeen) {
                corrupt(endOffset, "data after the end-of-recording footer at offset " + endOffset);
                return;
            }
            if ((++recordCounter & 255) == 0 && progress.report(pos - start, size - start)) {
                throw new CancelledException();
            }
            final long recStart = pos;
            final int type = buf.byteAt(pos++);
            try {
                switch (type) {
                    case Vbtm.RECORD_THREAD -> {
                        final long tid = varint();
                        final String name = string(recStart, varint());
                        data.threadNames.put(tid, name);
                    }
                    case Vbtm.RECORD_CHUNK, Vbtm.RECORD_CHUNK_END -> {
                        if (!readChunk(recStart, type == Vbtm.RECORD_CHUNK_END, partialOk)) {
                            return;
                        }
                        if (progress.report(pos - start, size - start)) {
                            throw new CancelledException();
                        }
                    }
                    case Vbtm.RECORD_CLASS -> {
                        final long baseId = varint();
                        final long count = varint();
                        if (baseId < 0 || count < 0 || baseId > Vbtm.METHOD_ID_LIMIT
                                || count > Vbtm.METHOD_ID_LIMIT - baseId) {
                            corrupt(recStart, "method ids exceed the 2^22 format limit");
                            return;
                        }
                        final String cls = string(recStart, varint());
                        final String[] sigs = new String[(int) Math.min(count, size - pos)];
                        int parsed = 0;
                        try {
                            for (; parsed < count; parsed++) {
                                sigs[parsed] = string(recStart, varint());
                            }
                        } catch (final TruncatedException t) {
                            if (partialOk) {
                                registerMethods(baseId, count, cls, sigs, parsed);
                            }
                            throw t;
                        }
                        registerMethods(baseId, count, cls, sigs, parsed);
                    }
                    case Vbtm.RECORD_EXCEPTION -> {
                        final long id = varint();
                        if (id <= 0 || id >= Vbtm.EXCEPTION_ID_LIMIT) {
                            corrupt(recStart, "exception id " + id + " outside 1.." + (Vbtm.EXCEPTION_ID_LIMIT - 1)
                                    + " at offset " + recStart);
                            return;
                        }
                        final String name = string(recStart, varint());
                        ensureExceptionNames((int) id + 1);
                        if (data.exceptionNames[(int) id] == null) {
                            data.totalExceptions++;
                        }
                        data.exceptionNames[(int) id] = name;
                    }
                    case Vbtm.RECORD_GC -> {
                        final long gcStart = varint();
                        final long durTicks = varint();
                        final long action = varint();
                        if (gcStart < 0 || gcStart > Vbtm.MAX_TICKS || durTicks < 0 || durTicks > Vbtm.MAX_TICKS - gcStart) {
                            corrupt(recStart, "GC pause ticks out of range at offset " + recStart);
                            return;
                        }
                        if (action < 0 || action > Vbtm.GC_ACTION_MAJOR) {
                            corrupt(recStart, "unknown GC action " + action + " at offset " + recStart);
                            return;
                        }
                        final long nameLen = varint();
                        if (nameLen > Vbtm.MAX_GC_LABEL_BYTES) {
                            corrupt(recStart, "GC collector name of " + nameLen + " bytes at offset " + recStart);
                            return;
                        }
                        final String name = gcLabel(string(recStart, nameLen));
                        final long causeLen = varint();
                        if (causeLen > Vbtm.MAX_GC_LABEL_BYTES) {
                            corrupt(recStart, "GC cause of " + causeLen + " bytes at offset " + recStart);
                            return;
                        }
                        final String cause = gcLabel(string(recStart, causeLen));
                        data.gc.append(gcStart * Vbtm.NANOS_PER_TICK, durTicks * Vbtm.NANOS_PER_TICK, (int) action,
                                name, cause);
                    }
                    case Vbtm.RECORD_END -> {
                        endSeen = true;
                        endOffset = recStart;
                    }
                    default -> {
                        corrupt(recStart, "unknown record type " + type + " at offset " + recStart);
                        return;
                    }
                }
            } catch (final TruncatedException t) {
                if (partialOk) {
                    throw t;
                }
                pos = recStart;
                return;
            }
        }
    }

    private boolean readChunk(final long recStart, final boolean endsSession, final boolean partialOk) {
        final long tid = varint();
        final long baseTicks = varint();
        final long payloadLen = varint();
        final long payloadStart = pos;
        if (baseTicks < 0 || baseTicks > Vbtm.MAX_TICKS) {
            corrupt(recStart, "chunk base ticks out of range at offset " + recStart);
            return false;
        }
        if (payloadLen < 0 || payloadLen > Vbtm.MAX_CHUNK_PAYLOAD_BYTES) {
            corrupt(payloadStart, "implausible chunk payload length " + payloadLen);
            return false;
        }
        final long end = payloadStart + payloadLen;
        final boolean partial = end > size;
        if (partial && !partialOk) {
            throw TruncatedException.INSTANCE;
        }
        final long limit = Math.min(end, size);

        final ThreadState st = states.computeIfAbsent(tid, ThreadState::new);
        long base = baseTicks;
        if (base < st.lastTicks) {
            base = st.lastTicks;
        }
        SessionState s = st.session;
        if (s == null) {
            s = st.session = new SessionState(data.sessions.size() + 1, tid, base * Vbtm.NANOS_PER_TICK);
            data.sessions.add(s);
            s.firstChunk = st.chunks.count;
            st.lastTicks = base;
        }
        final long stop = decodePayload(st, s, payloadStart, (int) (limit - payloadStart), base);
        final int ci = st.chunks.count - 1;
        s.lastChunk = ci;
        pos = stop;
        if (data.corruptOffset >= 0) {
            return false;
        }
        if (partial) {
            throw TruncatedException.INSTANCE;
        }
        if (stop != end) {
            corrupt(stop, "chunk payload ends mid-event at offset " + stop);
            return false;
        }
        if (endsSession) {
            st.chunks.markSessionEnd(ci);
            s.ended = true;
            closeOpenCalls(st, s);
            st.session = null;
        }
        return true;
    }

    private long decodePayload(final ThreadState st, final SessionState s, final long fileOffset, final int len,
            final long baseTicks) {
        if (scratch.length < len) {
            scratch = DecodeScratch.allocate(len);
        }
        buf.copy(fileOffset, scratch, len);
        final int openDepthAtStart = st.stack.depth();
        final EventCursor cur = eventCursor;
        cur.reset(scratch, 0, len, baseTicks);
        final FrameStack stack = st.stack;
        while (true) {
            final EventCursor.Event e = cur.next();
            if (e == EventCursor.Event.ENTER) {
                if (s.rootMethodId < 0) {
                    s.rootMethodId = cur.methodId();
                }
                stack.push(cur.ticks(), cur.methodId());
                if (stack.depth() - 1 > st.maxDepth) {
                    st.maxDepth = stack.depth() - 1;
                }
            } else if (e == EventCursor.Event.EXIT) {
                if (stack.depth() == 0) {
                    final long at = fileOffset + cur.eventIndex();
                    corrupt(at, "exit with no open frame in session #" + s.seq + " at offset " + at);
                    if (cur.decodedEvents() > 1) {
                        st.lastTicks = cur.ticks();
                    }
                    st.chunks.append(fileOffset, at, baseTicks, cur.ticks(), openDepthAtStart);
                    return at;
                }
                stack.pop(cur.ticks());
                onCall(st, s, stack.startNs(), stack.durNs(), stack.methodId(), stack.depth(), stack.selfNs(), false,
                        cur.exceptionId());
            } else {
                if (e == EventCursor.Event.CORRUPT) {
                    corrupt(fileOffset + cur.eventIndex(),
                            faultReason(cur.fault(), cur.faultValue(), fileOffset + cur.eventIndex()));
                }
                if (cur.decodedEvents() > 0) {
                    st.lastTicks = cur.ticks();
                }
                final long stop = fileOffset + cur.stopIndex();
                st.chunks.append(fileOffset, stop, baseTicks, cur.ticks(), openDepthAtStart);
                return stop;
            }
        }
    }

    private static String faultReason(final EventCursor.Fault fault, final long value, final long at) {
        return switch (fault) {
            case VARINT_TOO_LONG -> "varint too long at offset " + at;
            case METHOD_ID_LIMIT -> "method id " + value + " is outside the 2^22 format range";
            case EXCEPTION_ID_LIMIT -> "exception id " + value + " is outside the 2^22 format range";
            case TICKS_LIMIT -> "tick delta " + value + " pushes the clock past the format limit at offset " + at;
        };
    }

    private void closeOpenCalls(final ThreadState st, final SessionState s) {
        final long endTicks = st.lastTicks;
        final FrameStack stack = st.stack;
        while (stack.depth() > 0) {
            stack.pop(endTicks);
            onCall(st, s, stack.startNs(), stack.durNs(), stack.methodId(), stack.depth(), stack.selfNs(), true, -1);
        }
        s.endNs = endTicks * Vbtm.NANOS_PER_TICK;
    }

    private void onCall(final ThreadState st, final SessionState s, final long startNs, final long durNs, final int methodId,
            final int depth, final long selfNs, final boolean unclosed, final int exc) {
        data.totalCalls++;
        st.totalCalls++;
        s.callCount++;
        final long endNs = startNs + durNs;
        if (startNs < minNs) {
            minNs = startNs;
        }
        if (endNs > maxNs) {
            maxNs = endNs;
        }
        durationHistogram[DurationHistogram.bucketIndex(durNs)]++;
        if (!unclosed) {
            ensureAgg(methodId);
            data.callsByMethod[methodId]++;
        }
        if (durNs >= overviewThresholdNs || unclosed) {
            appendOverview(st, startNs, durNs, selfNs, methodId, depth, unclosed, exc);
        }
    }

    private void appendOverview(final ThreadState st, final long startNs, final long durNs, final long selfNs,
            final int methodId, final int depth, final boolean unclosed, final int exc) {
        st.overview.add(startNs, durNs, selfNs, methodId, depth, unclosed, exc);
        if (++overviewTotal > compactTrigger) {
            compact();
        }
    }

    private void compact() {
        final long newD = DurationHistogram.chooseThreshold(durationHistogram, data.totalCalls, overviewBudget);
        if (newD <= overviewThresholdNs) {
            compactTrigger *= 2;
            return;
        }
        overviewThresholdNs = newD;
        overviewTotal = dropBelow(newD);
    }

    private long dropBelow(final long d) {
        long kept = 0;
        for (final ThreadState st : states.values()) {
            kept += st.overview.dropShorterThan(d);
        }
        return kept;
    }

    private void finish() {
        final List<ThreadState> open = new ArrayList<>();
        for (final ThreadState st : states.values()) {
            if (st.session != null) {
                open.add(st);
            }
        }
        open.sort(Comparator.comparingInt(st -> st.session.seq));
        for (final ThreadState st : open) {
            closeOpenCalls(st, st.session);
            st.session = null;
        }

        final long finalD = DurationHistogram.chooseThreshold(durationHistogram, data.totalCalls, overviewBudget);
        if (finalD > overviewThresholdNs) {
            overviewThresholdNs = finalD;
            dropBelow(finalD);
        }
        data.overviewThresholdNs = overviewThresholdNs;

        final List<ThreadIndex> threads = new ArrayList<>();
        for (final ThreadState st : states.values()) {
            if (st.totalCalls > 0) {
                st.overview.sortByStart();
                threads.add(st.freeze());
            }
        }
        threads.sort(Comparator.comparingLong(m -> m.tid));
        data.threads = threads;

        if (data.totalCalls == 0) {
            data.minNs = 0;
            data.maxNs = 1;
        } else {
            data.minNs = minNs;
            data.maxNs = maxNs;
        }
    }

    private long varint() {
        long v = 0;
        int shift = 0;
        while (true) {
            if (pos >= size) {
                throw TruncatedException.INSTANCE;
            }
            final int b = buf.byteAt(pos++);
            v |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return v;
            }
            shift += 7;
            if (shift > 63) {
                throw new CorruptException(pos, "varint too long at offset " + pos);
            }
        }
    }

    private String string(final long recStart, final long len) {
        if (len < 0) {
            throw new CorruptException(recStart, "negative string length " + len + " at offset " + recStart);
        }
        if (len > size - pos) {
            throw TruncatedException.INSTANCE;
        }
        final byte[] b = new byte[(int) len];
        buf.copy(pos, b, (int) len);
        pos += len;
        return new String(b, StandardCharsets.UTF_8);
    }

    private void registerMethods(final long baseId, final long count, final String cls, final String[] sigs,
            final int n) {
        ensureMethodNames((int) (baseId + count));
        for (int k = 0; k < n; k++) {
            final int id = (int) baseId + k;
            if (data.methodNames[id] == null) {
                data.totalMethods++;
            }
            data.methodNames[id] = cls + "." + sigs[k];
        }
    }

    private void ensureMethodNames(final int n) {
        if (data.methodNames.length < n) {
            data.methodNames = Arrays.copyOf(data.methodNames, Math.max(n, data.methodNames.length * 2));
        }
    }

    private String gcLabel(final String s) {
        final String seen = gcLabels.putIfAbsent(s, s);
        return seen != null ? seen : s;
    }

    private void ensureExceptionNames(final int n) {
        if (data.exceptionNames.length < n) {
            data.exceptionNames = Arrays.copyOf(data.exceptionNames, Math.max(n, data.exceptionNames.length * 2));
        }
    }

    private void ensureAgg(final int methodId) {
        if (data.callsByMethod.length <= methodId) {
            final int cap = Math.max(methodId + 1, Math.max(1024, data.callsByMethod.length * 2));
            data.callsByMethod = Arrays.copyOf(data.callsByMethod, cap);
        }
    }

    private void corrupt(final long offset, final String reason) {
        if (data.corruptOffset < 0) {
            data.corruptOffset = offset;
            data.corruptReason = reason;
        }
    }

    private static final class TruncatedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("StaticAssignmentOfThrowable")
        private static final TruncatedException INSTANCE = new TruncatedException();

        private TruncatedException() {
            super(null, null, false, false);
        }
    }

    private static final class CorruptException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final long offset;

        private final String reason;

        private CorruptException(final long offset, final String reason) {
            super(null, null, false, false);
            this.offset = offset;
            this.reason = reason;
        }
    }
}
