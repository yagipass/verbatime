package io.github.yagipass.verbatime.agent.probe;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.format.Vbtm;
import java.util.List;

public final class ChunkEncoderTest {

  private ChunkEncoderTest() {}

  public static void run() {
    int idA = MethodRegistry.reserveIds("test.enc.A", List.of("a()V"));
    long origin = 5_000_000_000L;
    long enterPacked = (long) idA << 2;
    long exitPacked = ((long) idA << 2) | Session.EXIT;
    int regSize = MethodRegistry.size();
    byte[] out = new byte[64];

    ChunkEncoder e1 = new ChunkEncoder(origin);
    long[] straight = {origin + 1_000, enterPacked, origin + 3_000, exitPacked};
    ChunkEncoder.Encoded a = e1.encode(straight, 4, 0, regSize, out, 0);
    Check.eq(
        1_000 / Vbtm.NANOS_PER_TICK, a.baseTicks(), "the chunk starts at the first event's tick");
    Check.eq(
        3_000 / Vbtm.NANOS_PER_TICK,
        a.lastTicks(),
        "the last tick is the last event's tick, so the next chunk cannot start earlier");
    Check.eq(0L, e1.clampedDeltas(), "monotonic timestamps are not clamped");

    ChunkEncoder.Encoded floored = e1.encode(straight, 4, 20, regSize, out, 0);
    Check.eq(
        20L,
        floored.baseTicks(),
        "a first event behind the floor is lifted to the floor, so chunk bases never go backwards");
    Check.eq(
        3_000 / Vbtm.NANOS_PER_TICK,
        floored.lastTicks(),
        "once the clock is past the floor the real timestamps resume");
    Check.eq(1L, e1.clampedDeltas(), "the lift is counted as a clamp so close() can report it");

    long[] backwards = {origin + 3_000, enterPacked, origin + 1_000, exitPacked};
    ChunkEncoder.Encoded b = e1.encode(backwards, 4, 0, regSize, out, 0);
    Check.eq(
        3_000 / Vbtm.NANOS_PER_TICK,
        b.lastTicks(),
        "an event behind its predecessor gets delta 0 and does not move the last tick back");
    Check.eq(2L, e1.clampedDeltas(), "the in-chunk clamp is counted too");
    Check.eq(
        a.endOffset(),
        b.endOffset(),
        "a clamped event is still written, so the call tree keeps its shape");

    long[] unwritten = {origin + 1_000, enterPacked, 0, exitPacked};
    ChunkEncoder.Encoded stopped =
        new ChunkEncoder(origin).encode(unwritten, 4, 0, regSize, out, 0);
    Check.eq(
        new ChunkEncoder(origin).encode(straight, 2, 0, regSize, out, 0).endOffset(),
        stopped.endOffset(),
        "a zero timestamp marks a slot the owner has not written yet, so encoding stops there");

    long[] unknownId = {origin + 1_000, enterPacked, origin + 2_000, (long) regSize << 2};
    ChunkEncoder.Encoded unknown =
        new ChunkEncoder(origin).encode(unknownId, 4, 0, regSize, out, 0);
    Check.eq(
        stopped.endOffset(),
        unknown.endOffset(),
        "an enter with an unregistered method id is treated as a half-written slot and stops encoding");
  }
}
