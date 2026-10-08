package io.github.yagipass.verbatime.agent.probe;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.EventEncoder;
import io.github.yagipass.verbatime.format.Vbtm;

final class ChunkEncoder {

  record Encoded(int endOffset, long baseTicks, long lastTicks) {}

  private final long originNanos;

  private long clampedDeltas;

  ChunkEncoder(long originNanos) {
    this.originNanos = originNanos;
  }

  Encoded encode(
      long[] buf, int words, long floorTicks, int methodIdCount, byte[] out, int offset) {
    @Var int p = offset;
    @Var long baseTicks = floorTicks;
    @Var long prevTicks = -1;
    for (int i = 0; i < words; i += 2) {
      long nanos = buf[i];
      long packed = buf[i + 1];
      if (nanos == 0) {
        break;
      }
      int lowBits = (int) (packed & 3);
      boolean enter = lowBits == 0;
      if (enter && (packed >>> 2) >= methodIdCount) {
        break;
      }
      long rel = nanos - originNanos;
      @Var long ticks = rel <= 0 ? 0 : rel / Vbtm.NANOS_PER_TICK;
      long delta;
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
