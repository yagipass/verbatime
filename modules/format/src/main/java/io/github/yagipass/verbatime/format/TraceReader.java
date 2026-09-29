package io.github.yagipass.verbatime.format;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.google.errorprone.annotations.Var;

public final class TraceReader {

    public enum Outcome {
        CLEAN, TRUNCATED
    }

    public interface Visitor {

        default void anchor(long startEpochMs, int utcOffsetSeconds) {
        }

        default void thread(long tid, String name) {
        }

        default void clazz(long baseId, String className, String[] sigs) {
        }

        default void exception(long id, String className) {
        }

        default void gc(long startTicks, long durTicks, int action, String collector,
                String cause) {
        }

        default void chunk(long tid, long baseTicks, byte[] bytes, int off, int len,
                boolean sessionEnd, boolean truncated) {
        }

        default void end() {
        }
    }

    private final byte[] data;

    private int pos;

    private TraceReader(byte[] data) {
        this.data = data;
    }

    public static Outcome read(byte[] data, Visitor v) {
        return new TraceReader(data).readAll(v);
    }

    private Outcome readAll(Visitor v) {
        if (!Vbtm.hasMagic(data, 0, data.length)) {
            throw new CorruptTraceException(0, data.length < Vbtm.MAGIC_BYTES ? "file shorter than the magic" : "bad magic");
        }
        if (data.length <= Vbtm.VERSION_OFFSET) {
            return Outcome.TRUNCATED;
        }
        int version = data[Vbtm.VERSION_OFFSET] & 0xFF;
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
        ByteBuffer anchor = ByteBuffer.wrap(data, pos + 1, Vbtm.ANCHOR_BYTES - 1);
        long startEpochMs = anchor.getLong();
        int utcOffsetSeconds = anchor.getInt();
        if (utcOffsetSeconds < -Vbtm.MAX_UTC_OFFSET_SECONDS || utcOffsetSeconds > Vbtm.MAX_UTC_OFFSET_SECONDS) {
            throw new CorruptTraceException(pos, "UTC offset " + utcOffsetSeconds + " s out of range in the anchor record");
        }
        v.anchor(startEpochMs, utcOffsetSeconds);
        pos = Vbtm.HEADER_BYTES;
        @Var boolean endSeen = false;
        while (pos < data.length) {
            int recStart = pos;
            if (endSeen) {
                throw new CorruptTraceException(recStart, (data.length - recStart) + " bytes after the END record");
            }
            int type = data[pos++] & 0xFF;
            try {
                switch (type) {
                    case Vbtm.RECORD_THREAD -> {
                        long tid = varint();
                        v.thread(tid, string(recStart));
                    }
                    case Vbtm.RECORD_CHUNK, Vbtm.RECORD_CHUNK_END -> {
                        long tid = varint();
                        long baseTicks = varint();
                        long payloadLen = varint();
                        if (baseTicks < 0 || baseTicks > Vbtm.MAX_TICKS) {
                            throw new CorruptTraceException(recStart, "chunk base ticks out of range");
                        }
                        if (payloadLen < 0 || payloadLen > Vbtm.MAX_CHUNK_PAYLOAD_BYTES) {
                            throw new CorruptTraceException(recStart, "implausible chunk payload length " + payloadLen);
                        }
                        int off = pos;
                        long payloadEnd = off + payloadLen;
                        if (payloadEnd > data.length) {
                            v.chunk(tid, baseTicks, data, off, data.length - off, type == Vbtm.RECORD_CHUNK_END, true);
                            return Outcome.TRUNCATED;
                        }
                        v.chunk(tid, baseTicks, data, off, (int) payloadLen, type == Vbtm.RECORD_CHUNK_END, false);
                        pos = (int) payloadEnd;
                    }
                    case Vbtm.RECORD_CLASS -> {
                        long baseId = varint();
                        long count = varint();
                        if (baseId < 0 || count < 0 || baseId > Vbtm.METHOD_ID_LIMIT
                                || count > Vbtm.METHOD_ID_LIMIT - baseId) {
                            throw new CorruptTraceException(recStart, "method ids exceed the 2^22 format limit");
                        }
                        String cls = string(recStart);
                        if (count > data.length - pos) {
                            throw new Truncated();
                        }
                        String[] sigs = new String[(int) count];
                        for (int k = 0; k < sigs.length; k++) {
                            sigs[k] = string(recStart);
                        }
                        v.clazz(baseId, cls, sigs);
                    }
                    case Vbtm.RECORD_EXCEPTION -> {
                        long id = varint();
                        if (id <= 0 || id >= Vbtm.EXCEPTION_ID_LIMIT) {
                            throw new CorruptTraceException(recStart, "exception id " + id + " outside 1.." + (Vbtm.EXCEPTION_ID_LIMIT - 1));
                        }
                        v.exception(id, string(recStart));
                    }
                    case Vbtm.RECORD_GC -> {
                        long start = varint();
                        long dur = varint();
                        long action = varint();
                        if (start < 0 || start > Vbtm.MAX_TICKS || dur < 0 || dur > Vbtm.MAX_TICKS - start) {
                            throw new CorruptTraceException(recStart, "GC pause ticks out of range");
                        }
                        if (action < 0 || action > Vbtm.GC_ACTION_MAJOR) {
                            throw new CorruptTraceException(recStart, "unknown GC action " + action);
                        }
                        String collector = gcLabel(recStart, "GC collector name");
                        String cause = gcLabel(recStart, "GC cause");
                        v.gc(start, dur, (int) action, collector, cause);
                    }
                    case Vbtm.RECORD_END -> {
                        endSeen = true;
                        v.end();
                    }
                    default -> throw new CorruptTraceException(recStart, "unknown record type " + type);
                }
            } catch (Truncated t) {
                return Outcome.TRUNCATED;
            }
        }
        return endSeen ? Outcome.CLEAN : Outcome.TRUNCATED;
    }

    private String gcLabel(int recStart, String what) {
        long len = varint();
        if (len > Vbtm.MAX_GC_LABEL_BYTES) {
            throw new CorruptTraceException(recStart, what + " of " + len + " bytes");
        }
        return string(recStart, len);
    }

    private long varint() {
        @Var long r = 0;
        @Var int shift = 0;
        while (true) {
            if (pos >= data.length) {
                throw new Truncated();
            }
            int b = data[pos++] & 0xFF;
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

    private String string(int recStart) {
        return string(recStart, varint());
    }

    private String string(int recStart, long len) {
        if (len < 0) {
            throw new CorruptTraceException(recStart, "negative string length " + len);
        }
        if (len > data.length - pos) {
            throw new Truncated();
        }
        String s = new String(data, pos, (int) len, StandardCharsets.UTF_8);
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
