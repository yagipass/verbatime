package io.github.yagipass.verbatime.agent.probe;

import io.github.yagipass.verbatime.format.EventEncoder;
import io.github.yagipass.verbatime.format.Vbtm;

final class ChunkEncoder {

    record Encoded(int endOffset, long baseTicks, long lastTicks) {
    }

    private final long originNanos;

    private long clampedDeltas;

    ChunkEncoder(final long originNanos) {
        this.originNanos = originNanos;
    }

    Encoded encode(final long[] buf, final int words, final long floorTicks, final int methodIdCount, final byte[] out,
            final int offset) {
        int p = offset;
        long baseTicks = floorTicks;
        long prevTicks = -1;
        for (int i = 0; i < words; i += 2) {
            final long nanos = buf[i];
            final long packed = buf[i + 1];
            if (nanos == 0) {
                break;
            }
            final int lowBits = (int) (packed & 3);
            final boolean enter = lowBits == 0;
            if (enter && (packed >>> 2) >= methodIdCount) {
                break;
            }
            final long rel = nanos - originNanos;
            long ticks = rel <= 0 ? 0 : rel / Vbtm.NANOS_PER_TICK;
            final long delta;
            if (prevTicks < 0) {
                if (ticks < floorTicks) {
                    ticks = floorTicks;
                    clampedDeltas++;
                }
                baseTicks = ticks;
                prevTicks = ticks;
                delta = 0;
            } else if (ticks < prevTicks) {
                delta = 0;
                clampedDeltas++;
            } else {
                delta = ticks - prevTicks;
                prevTicks = ticks;
            }
            if (enter) {
                p = EventEncoder.enter(out, p, delta, packed >>> 2);
            } else if ((lowBits & (int) Session.THROW) != 0) {
                p = EventEncoder.exitThrow(out, p, delta, packed >>> 32);
            } else {
                p = EventEncoder.exit(out, p, delta);
            }
        }
        return new Encoded(p, baseTicks, prevTicks);
    }

    long clampedDeltas() {
        return clampedDeltas;
    }
}
