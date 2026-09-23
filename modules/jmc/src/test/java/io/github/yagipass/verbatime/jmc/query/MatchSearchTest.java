package io.github.yagipass.verbatime.jmc.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.BitSet;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.jmc.index.RandomTraces;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder.Call;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

final class MatchSearchTest {

    @Test
    void matchNavigationMatchesBruteForce() throws IOException {
        for (long seed = 1; seed <= 6; seed++) {
            final byte[] bytes = RandomTraces.random(seed);
            final ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
            final Random rng = new Random(seed * 41);
            for (final int budget : new int[] { 1 << 30, 4000, 48 }) {
                final TraceSnapshot data = TestTraces.index(bytes, budget);
                for (int trial = 0; trial < 12; trial++) {
                    final BitSet set = new BitSet();
                    if (trial % 3 == 0) {
                        final Call f = ref.calls.get(rng.nextInt(ref.calls.size()));
                        set.set(f.methodId());
                    } else {
                        for (final Call f : ref.calls) {
                            if (rng.nextInt(8) == 0) {
                                set.set(f.methodId());
                            }
                        }
                    }
                    final long span = Math.max(data.maxNs - data.minNs, 1);
                    final long pos = data.minNs + (long) (rng.nextDouble() * span);
                    final String ctx = "seed " + seed + " budget " + budget + " pos " + pos;

                    final long wantNext = ref.calls.stream().filter(f -> set.get(f.methodId()) && f.startNs() > pos)
                            .mapToLong(Call::startNs).min().orElse(Long.MIN_VALUE);
                    final MatchSearch.Match next = MatchSearch.nextMatch(data, set, pos);
                    if (wantNext == Long.MIN_VALUE) {
                        assertNull(next, ctx + " next");
                    } else {
                        assertEquals(wantNext, next.startNs, ctx + " next start");
                        assertTrue(set.get(next.methodId), ctx + " next method");
                        assertTrue(frameExists(ref, next), ctx + " next frame " + next.startNs);
                    }

                    final long wantPrev = ref.calls.stream().filter(f -> set.get(f.methodId()) && f.startNs() < pos)
                            .mapToLong(Call::startNs).max().orElse(Long.MIN_VALUE);
                    final MatchSearch.Match prev = MatchSearch.prevMatch(data, set, pos);
                    if (wantPrev == Long.MIN_VALUE) {
                        assertNull(prev, ctx + " prev");
                    } else {
                        assertEquals(wantPrev, prev.startNs, ctx + " prev start");
                        assertTrue(set.get(prev.methodId), ctx + " prev method");
                        assertTrue(frameExists(ref, prev), ctx + " prev frame " + prev.startNs);
                    }
                }
            }
        }
    }

    private static boolean frameExists(final ReferenceDecoder.Result ref, final MatchSearch.Match m) {
        for (final Call f : ref.calls) {
            if (f.tid() == m.tid && f.startNs() == m.startNs && f.durNs() == m.durNs && f.depth() == m.depth
                    && f.methodId() == m.methodId) {
                return true;
            }
        }
        return false;
    }

}
