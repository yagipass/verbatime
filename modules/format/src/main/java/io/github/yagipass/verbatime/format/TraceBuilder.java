package io.github.yagipass.verbatime.format;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

public final class TraceBuilder {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public TraceBuilder(final long startEpochMs, final int utcOffsetSeconds) {
        out.writeBytes(RecordEncoder.header(startEpochMs, utcOffsetSeconds));
    }

    public TraceBuilder thread(final long tid, final String name) {
        out.writeBytes(RecordEncoder.thread(tid, name));
        return this;
    }

    public TraceBuilder exception(final long id, final String className) {
        out.writeBytes(RecordEncoder.exception(id, className));
        return this;
    }

    public TraceBuilder gc(final long startTicks, final long durTicks, final int action, final String collector,
            final String cause) {
        out.writeBytes(RecordEncoder.gc(startTicks, durTicks, action, collector, cause));
        return this;
    }

    public TraceBuilder clazz(final long baseId, final String className, final String... sigs) {
        out.writeBytes(RecordEncoder.clazz(baseId, className, List.of(sigs)));
        return this;
    }

    public TraceBuilder chunk(final long tid, final long baseTicks, final byte[] payload, final boolean sessionEnd) {
        final byte[] head = new byte[RecordEncoder.MAX_CHUNK_HEADER_BYTES];
        final int n = RecordEncoder.chunkHeader(head, 0, tid, baseTicks, payload.length, sessionEnd);
        out.write(head, 0, n);
        out.writeBytes(payload);
        return this;
    }

    public TraceBuilder end() {
        out.writeBytes(RecordEncoder.end());
        return this;
    }

    public TraceBuilder rawBytes(final int... bytes) {
        for (final int b : bytes) {
            out.write(b);
        }
        return this;
    }

    public byte[] bytes() {
        return out.toByteArray();
    }

    public static final class Payload {

        private byte[] buf = new byte[64];

        private int len;

        private long lastTicks;

        private boolean first = true;

        public Payload(final long baseTicks) {
            lastTicks = baseTicks;
        }

        public Payload enter(final long ticks, final int methodId) {
            if (methodId < 0) {
                throw new IllegalArgumentException("method id must be >= 0");
            }
            final long delta = advance(ticks);
            len = EventEncoder.enter(ensureRoom(), len, delta, methodId);
            return this;
        }

        public Payload exit(final long ticks) {
            final long delta = advance(ticks);
            len = EventEncoder.exit(ensureRoom(), len, delta);
            return this;
        }

        public Payload exitThrow(final long ticks, final int exceptionId) {
            if (exceptionId < 0) {
                throw new IllegalArgumentException("exception id must be >= 0");
            }
            final long delta = advance(ticks);
            len = EventEncoder.exitThrow(ensureRoom(), len, delta, exceptionId);
            return this;
        }

        public byte[] bytes() {
            return Arrays.copyOf(buf, len);
        }

        private long advance(final long ticks) {
            if (ticks < 0 || ticks > Vbtm.MAX_TICKS) {
                throw new IllegalArgumentException("ticks must be within 0..MAX_TICKS");
            }
            final long delta = first ? 0 : ticks - lastTicks;
            if (delta < 0) {
                throw new IllegalArgumentException("ticks must be non-decreasing");
            }
            lastTicks = ticks;
            first = false;
            return delta;
        }

        private byte[] ensureRoom() {
            if (buf.length - len < EventEncoder.MAX_BYTES) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            return buf;
        }
    }
}
