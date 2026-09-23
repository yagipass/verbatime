package io.github.yagipass.verbatime.format;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class TraceReader {

    public enum Outcome {
        CLEAN, TRUNCATED
    }

    public interface Visitor {

        default void anchor(final long startEpochMs, final int utcOffsetSeconds) {
        }

        default void thread(final long tid, final String name) {
        }

        default void clazz(final long baseId, final String className, final String[] sigs) {
        }

        default void exception(final long id, final String className) {
        }

        default void gc(final long startTicks, final long durTicks, final int action, final String collector,
                final String cause) {
        }

        default void chunk(final long tid, final long baseTicks, final byte[] bytes, final int off, final int len,
                final boolean sessionEnd, final boolean truncated) {
        }

        default void end() {
        }
    }

    private final byte[] data;

    private int pos;

    private TraceReader(final byte[] data) {
        this.data = data;
    }

    public static Outcome read(final byte[] data, final Visitor v) {
        return new TraceReader(data).readAll(v);
    }

    private Outcome readAll(final Visitor v) {
        if (!Vbtm.hasMagic(data, 0, data.length)) {
            throw new CorruptTraceException(0, data.length < Vbtm.MAGIC_BYTES ? "file shorter than the magic" : "bad magic");
        }
        if (data.length <= Vbtm.VERSION_OFFSET) {
            return Outcome.TRUNCATED;
        }
        final int version = data[Vbtm.VERSION_OFFSET] & 0xFF;
        if (version != Vbtm.VERSION) {
            throw new CorruptTraceException(Vbtm.VERSION_OFFSET,
                    "format version " + version + " is not supported, this reader reads version " + Vbtm.VERSION);
        }
        pos = Vbtm.ANCHOR_OFFSET;
        if (pos >= data.length) {
            return Outcome.TRUNCATED;
        }
        if ((data[pos] & 0xFF) != Vbtm.RECORD_ANCHOR) {
            throw new CorruptTraceException(pos, "the anchor record must follow the version byte");
        }
        if (data.length < Vbtm.HEADER_BYTES) {
            return Outcome.TRUNCATED;
        }
        final ByteBuffer anchor = ByteBuffer.wrap(data, pos + 1, Vbtm.ANCHOR_BYTES - 1);
        final long startEpochMs = anchor.getLong();
        final int utcOffsetSeconds = anchor.getInt();
        if (utcOffsetSeconds < -Vbtm.MAX_UTC_OFFSET_SECONDS || utcOffsetSeconds > Vbtm.MAX_UTC_OFFSET_SECONDS) {
            throw new CorruptTraceException(pos, "UTC offset " + utcOffsetSeconds + " s out of range in the anchor record");
        }
        v.anchor(startEpochMs, utcOffsetSeconds);
        pos = Vbtm.HEADER_BYTES;
        boolean endSeen = false;
        while (pos < data.length) {
            final int recStart = pos;
            if (endSeen) {
                throw new CorruptTraceException(recStart, (data.length - recStart) + " bytes after the END record");
            }
            final int type = data[pos++] & 0xFF;
            try {
                switch (type) {
                    case Vbtm.RECORD_THREAD -> {
                        final long tid = varint();
                        v.thread(tid, string(recStart));
                    }
                    case Vbtm.RECORD_CHUNK, Vbtm.RECORD_CHUNK_END -> {
                        final long tid = varint();
                        final long baseTicks = varint();
                        final long payloadLen = varint();
                        if (baseTicks < 0 || baseTicks > Vbtm.MAX_TICKS) {
                            throw new CorruptTraceException(recStart, "chunk base ticks out of range");
                        }
                        if (payloadLen < 0 || payloadLen > Vbtm.MAX_CHUNK_PAYLOAD_BYTES) {
                            throw new CorruptTraceException(recStart, "implausible chunk payload length " + payloadLen);
                        }
                        final int off = pos;
                        final long payloadEnd = off + payloadLen;
                        if (payloadEnd > data.length) {
                            v.chunk(tid, baseTicks, data, off, data.length - off, type == Vbtm.RECORD_CHUNK_END, true);
                            return Outcome.TRUNCATED;
                        }
                        v.chunk(tid, baseTicks, data, off, (int) payloadLen, type == Vbtm.RECORD_CHUNK_END, false);
                        pos = (int) payloadEnd;
                    }
                    case Vbtm.RECORD_CLASS -> {
                        final long baseId = varint();
                        final long count = varint();
                        if (baseId < 0 || count < 0 || baseId > Vbtm.METHOD_ID_LIMIT
                                || count > Vbtm.METHOD_ID_LIMIT - baseId) {
                            throw new CorruptTraceException(recStart, "method ids exceed the 2^22 format limit");
                        }
                        final String cls = string(recStart);
                        if (count > data.length - pos) {
                            throw new Truncated();
                        }
                        final String[] sigs = new String[(int) count];
                        for (int k = 0; k < sigs.length; k++) {
                            sigs[k] = string(recStart);
                        }
                        v.clazz(baseId, cls, sigs);
                    }
                    case Vbtm.RECORD_EXCEPTION -> {
                        final long id = varint();
                        if (id <= 0 || id >= Vbtm.EXCEPTION_ID_LIMIT) {
                            throw new CorruptTraceException(recStart, "exception id " + id + " outside 1.." + (Vbtm.EXCEPTION_ID_LIMIT - 1));
                        }
                        v.exception(id, string(recStart));
                    }
                    case Vbtm.RECORD_GC -> {
                        final long start = varint();
                        final long dur = varint();
                        final long action = varint();
                        if (start < 0 || start > Vbtm.MAX_TICKS || dur < 0 || dur > Vbtm.MAX_TICKS - start) {
                            throw new CorruptTraceException(recStart, "GC pause ticks out of range");
                        }
                        if (action < 0 || action > Vbtm.GC_ACTION_MAJOR) {
                            throw new CorruptTraceException(recStart, "unknown GC action " + action);
                        }
                        final String collector = gcLabel(recStart, "GC collector name");
                        final String cause = gcLabel(recStart, "GC cause");
                        v.gc(start, dur, (int) action, collector, cause);
                    }
                    case Vbtm.RECORD_END -> {
                        endSeen = true;
                        v.end();
                    }
                    default -> throw new CorruptTraceException(recStart, "unknown record type " + type);
                }
            } catch (final Truncated t) {
                return Outcome.TRUNCATED;
            }
        }
        return endSeen ? Outcome.CLEAN : Outcome.TRUNCATED;
    }

    private String gcLabel(final int recStart, final String what) {
        final long len = varint();
        if (len > Vbtm.MAX_GC_LABEL_BYTES) {
            throw new CorruptTraceException(recStart, what + " of " + len + " bytes");
        }
        return string(recStart, len);
    }

    private long varint() {
        long r = 0;
        int shift = 0;
        while (true) {
            if (pos >= data.length) {
                throw new Truncated();
            }
            final int b = data[pos++] & 0xFF;
            r |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return r;
            }
            shift += 7;
            if (shift > 63) {
                throw new CorruptTraceException(pos, "varint too long");
            }
        }
    }

    private String string(final int recStart) {
        return string(recStart, varint());
    }

    private String string(final int recStart, final long len) {
        if (len < 0) {
            throw new CorruptTraceException(recStart, "negative string length " + len);
        }
        if (len > data.length - pos) {
            throw new Truncated();
        }
        final String s = new String(data, pos, (int) len, StandardCharsets.UTF_8);
        pos += (int) len;
        return s;
    }

    private static final class Truncated extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private Truncated() {
            super(null, null, false, false);
        }
    }
}
