package io.github.yagipass.verbatime.agent.probe;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.format.EventEncoder;
import io.github.yagipass.verbatime.format.RecordEncoder;
import io.github.yagipass.verbatime.format.Vbtm;

public final class TraceFileWriter {

    private static final int CHUNK_HEADER_ROOM = RecordEncoder.MAX_CHUNK_HEADER_BYTES;

    private final Path path;

    private final FileOutputStream out;

    final long originNanos;

    final long gcClockAtOriginMs;

    private final long startEpochMs;

    private final int utcOffsetSeconds;

    private final ChunkEncoder encoder;

    private final Map<Long, String> seenThreads = new HashMap<>();

    private byte[] scratch = new byte[0];

    private volatile boolean stopped;

    private volatile boolean failed;

    private volatile long committedBytes;

    private TraceFileWriter(Path path, FileOutputStream out, long vmInitUptimeMs) {
        this.path = path;
        this.out = out;
        this.gcClockAtOriginMs = ManagementFactory.getRuntimeMXBean().getUptime() - vmInitUptimeMs;
        this.originNanos = System.nanoTime();
        this.startEpochMs = System.currentTimeMillis();
        this.utcOffsetSeconds = ZoneId.systemDefault().getRules().getOffset(Instant.ofEpochMilli(startEpochMs))
                .getTotalSeconds();
        this.encoder = new ChunkEncoder(originNanos);
    }

    public static TraceFileWriter open(Path path) throws IOException {
        return open(path, vmInitUptimeMs());
    }

    public static TraceFileWriter open(Path path, long vmInitUptimeMs) throws IOException {
        FileOutputStream out = new FileOutputStream(path.toFile());
        TraceFileWriter w = new TraceFileWriter(path.toAbsolutePath(), out, vmInitUptimeMs);
        out.write(RecordEncoder.header(w.startEpochMs, w.utcOffsetSeconds));
        w.committedBytes = Vbtm.HEADER_BYTES;
        return w;
    }

    public static long vmInitUptimeMs() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        return rt.getUptime() - (System.currentTimeMillis() - rt.getStartTime());
    }

    public Path path() {
        return path;
    }

    public long startEpochMs() {
        return startEpochMs;
    }

    boolean isStopped() {
        return stopped;
    }

    public boolean hasFailed() {
        return failed;
    }

    public long committedBytes() {
        return committedBytes;
    }

    @SuppressWarnings("NonAtomicVolatileUpdate")
    private void advanceCommitted(long n) {
        committedBytes += n;
    }

    void writeClass(int baseId, String className, List<String> sigs) {
        if (stopped) {
            return;
        }
        writeRecord(RecordEncoder.clazz(baseId, className, sigs));
    }

    void writeException(int id, String className) {
        if (stopped) {
            return;
        }
        writeRecord(RecordEncoder.exception(id, className));
    }

    void writeGc(long startMs, long durMs, int action, String collector, String cause) {
        if (stopped) {
            return;
        }
        long[] ticks = toTicks(startMs, durMs, gcClockAtOriginMs);
        if (ticks == null) {
            return;
        }
        writeRecord(RecordEncoder.gc(ticks[0], ticks[1], action, collector, cause));
    }

    private synchronized void writeRecord(byte[] rec) {
        if (!stopped) {
            writeLocked(rec);
        }
    }

    private void writeLocked(byte[] rec) {
        writeLocked(rec, 0, rec.length);
    }

    private void writeLocked(byte[] b, int off, int len) {
        try {
            out.write(b, off, len);
            advanceCommitted(len);
        } catch (IOException e) {
            stopOnFailure(e);
        }
    }

    static long[] toTicks(long startMs, long durMs, long gcClockAtOriginMs) {
        long endMs = startMs + Math.max(durMs, 0);
        if (endMs <= gcClockAtOriginMs) {
            return null;
        }
        long s = Math.max(startMs - gcClockAtOriginMs, 0);
        long e = endMs - gcClockAtOriginMs;
        return new long[] { s * Vbtm.TICKS_PER_MS, (e - s) * Vbtm.TICKS_PER_MS };
    }

    synchronized void appendChunk(Session r, boolean sessionEnd) {
        try {
            if (!r.truncatedByStop) {
                appendChunkLocked(r, sessionEnd);
            }
        } catch (Throwable t) {
            stopOnFailure(t);
        } finally {
            r.pos = 0;
        }
    }

    synchronized void flushTruncated(Session r) {
        try {
            r.truncatedByStop = true;
            appendChunkLocked(r, false);
        } catch (Throwable t) {
            stopOnFailure(t);
        }
    }

    private void appendChunkLocked(Session r, boolean sessionEnd) {
        int words = r.pos;
        if (stopped || (words == 0 && (r.firstChunkPending || !sessionEnd))) {
            return;
        }
        String name = r.owner.getName();
        if (!name.equals(seenThreads.get(r.tid))) {
            seenThreads.put(r.tid, name);
            writeLocked(RecordEncoder.thread(r.tid, name));
            if (stopped) {
                return;
            }
        }
        ensureScratch((words / 2) * EventEncoder.MAX_BYTES + CHUNK_HEADER_ROOM);
        byte[] b = scratch;
        ChunkEncoder.Encoded e = encoder.encode(r.buf, words, r.lastTicks, MethodRegistry.size(), b, CHUNK_HEADER_ROOM);
        int payloadLen = e.endOffset() - CHUNK_HEADER_ROOM;
        byte[] head = new byte[CHUNK_HEADER_ROOM];
        int h = RecordEncoder.chunkHeader(head, 0, r.tid, e.baseTicks(), payloadLen, sessionEnd);
        System.arraycopy(head, 0, b, CHUNK_HEADER_ROOM - h, h);
        writeLocked(b, CHUNK_HEADER_ROOM - h, h + payloadLen);
        r.firstChunkPending = false;
        if (e.lastTicks() >= 0) {
            r.lastTicks = e.lastTicks();
        }
    }

    public synchronized void close() {
        if (!stopped) {
            long clamped = encoder.clampedDeltas();
            if (clamped > 0) {
                Log.warn("clamped " + Log.plural(clamped, "non-monotonic event timestamp"));
            }
            writeLocked(RecordEncoder.end());
            stopped = true;
        }
        try {
            out.close();
        } catch (IOException e) {
            Log.warn("closing " + path + ": " + e);
        }
    }

    private void stopOnFailure(Throwable e) {
        if (!stopped) {
            stopped = true;
            failed = true;
            Log.warn("cannot write trace output, tracing stopped: " + e);
        }
    }

    private void ensureScratch(int needed) {
        if (scratch.length < needed) {
            scratch = new byte[needed];
        }
    }
}
