package io.github.yagipass.verbatime.agent.probe;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
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

    final long uptimeAtOriginMs;

    private final long startEpochMs;

    private final int utcOffsetSeconds;

    private final ChunkEncoder encoder;

    private final Map<Long, String> seenThreads = new HashMap<>();

    private byte[] scratch = new byte[0];

    private volatile boolean stopped;

    private volatile boolean failed;

    private volatile long committedBytes;

    private TraceFileWriter(final Path path, final FileOutputStream out) {
        this.path = path;
        this.out = out;
        this.uptimeAtOriginMs = ManagementFactory.getRuntimeMXBean().getUptime();
        this.originNanos = System.nanoTime();
        this.startEpochMs = System.currentTimeMillis();
        this.utcOffsetSeconds = ZoneId.systemDefault().getRules().getOffset(Instant.ofEpochMilli(startEpochMs))
                .getTotalSeconds();
        this.encoder = new ChunkEncoder(originNanos);
    }

    public static TraceFileWriter open(final Path path) throws IOException {
        final FileOutputStream out = new FileOutputStream(path.toFile());
        final TraceFileWriter w = new TraceFileWriter(path.toAbsolutePath(), out);
        out.write(RecordEncoder.header(w.startEpochMs, w.utcOffsetSeconds));
        w.committedBytes = Vbtm.HEADER_BYTES;
        return w;
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
    private void advanceCommitted(final long n) {
        committedBytes += n;
    }

    void writeClass(final int baseId, final String className, final List<String> sigs) {
        if (stopped) {
            return;
        }
        writeRecord(RecordEncoder.clazz(baseId, className, sigs));
    }

    void writeException(final int id, final String className) {
        if (stopped) {
            return;
        }
        writeRecord(RecordEncoder.exception(id, className));
    }

    void writeGc(final long startMs, final long durMs, final int action, final String collector, final String cause) {
        if (stopped) {
            return;
        }
        final long[] ticks = toTicks(startMs, durMs, uptimeAtOriginMs);
        if (ticks == null) {
            return;
        }
        writeRecord(RecordEncoder.gc(ticks[0], ticks[1], action, collector, cause));
    }

    private synchronized void writeRecord(final byte[] rec) {
        if (!stopped) {
            writeLocked(rec);
        }
    }

    private void writeLocked(final byte[] rec) {
        writeLocked(rec, 0, rec.length);
    }

    private void writeLocked(final byte[] b, final int off, final int len) {
        try {
            out.write(b, off, len);
            advanceCommitted(len);
        } catch (final IOException e) {
            stopOnFailure(e);
        }
    }

    static long[] toTicks(final long startMs, final long durMs, final long uptimeAtOriginMs) {
        final long endMs = startMs + Math.max(durMs, 0);
        if (endMs <= uptimeAtOriginMs) {
            return null;
        }
        final long s = Math.max(startMs - uptimeAtOriginMs, 0);
        final long e = endMs - uptimeAtOriginMs;
        return new long[] { s * Vbtm.TICKS_PER_MS, (e - s) * Vbtm.TICKS_PER_MS };
    }

    synchronized void appendChunk(final Session r, final boolean sessionEnd) {
        try {
            if (!r.truncatedByStop) {
                appendChunkLocked(r, sessionEnd);
            }
        } catch (final Throwable t) {
            stopOnFailure(t);
        } finally {
            r.pos = 0;
        }
    }

    synchronized void flushTruncated(final Session r) {
        try {
            r.truncatedByStop = true;
            appendChunkLocked(r, false);
        } catch (final Throwable t) {
            stopOnFailure(t);
        }
    }

    private void appendChunkLocked(final Session r, final boolean sessionEnd) {
        final int words = r.pos;
        if (stopped || (words == 0 && (r.firstChunkPending || !sessionEnd))) {
            return;
        }
        final String name = r.owner.getName();
        if (!name.equals(seenThreads.get(r.tid))) {
            seenThreads.put(r.tid, name);
            writeLocked(RecordEncoder.thread(r.tid, name));
            if (stopped) {
                return;
            }
        }
        ensureScratch((words / 2) * EventEncoder.MAX_BYTES + CHUNK_HEADER_ROOM);
        final byte[] b = scratch;
        final ChunkEncoder.Encoded e = encoder.encode(r.buf, words, r.lastTicks, MethodRegistry.size(), b, CHUNK_HEADER_ROOM);
        final int payloadLen = e.endOffset() - CHUNK_HEADER_ROOM;
        final byte[] head = new byte[CHUNK_HEADER_ROOM];
        final int h = RecordEncoder.chunkHeader(head, 0, r.tid, e.baseTicks(), payloadLen, sessionEnd);
        System.arraycopy(head, 0, b, CHUNK_HEADER_ROOM - h, h);
        writeLocked(b, CHUNK_HEADER_ROOM - h, h + payloadLen);
        r.firstChunkPending = false;
        if (e.lastTicks() >= 0) {
            r.lastTicks = e.lastTicks();
        }
    }

    public synchronized void close() {
        if (!stopped) {
            final long clamped = encoder.clampedDeltas();
            if (clamped > 0) {
                Log.warn("clamped " + Log.plural(clamped, "non-monotonic event timestamp"));
            }
            writeLocked(RecordEncoder.end());
            stopped = true;
        }
        try {
            out.close();
        } catch (final IOException e) {
            Log.warn("closing " + path + ": " + e);
        }
    }

    private void stopOnFailure(final Throwable e) {
        if (!stopped) {
            stopped = true;
            failed = true;
            Log.warn("cannot write trace output, tracing stopped: " + e);
        }
    }

    private void ensureScratch(final int needed) {
        if (scratch.length < needed) {
            scratch = new byte[needed];
        }
    }
}
