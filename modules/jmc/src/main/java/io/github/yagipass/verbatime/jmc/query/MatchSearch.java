package io.github.yagipass.verbatime.jmc.query;

import java.util.BitSet;

import io.github.yagipass.verbatime.jmc.index.ChunkWalker;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

public final class MatchSearch {

    private MatchSearch() {
    }

    public static final class Match {

        public final long tid;

        public final long startNs;

        public final long durNs;

        public final int depth;

        public final int methodId;

        private Match(final long tid, final long startNs, final long durNs, final int depth, final int methodId) {
            this.tid = tid;
            this.startNs = startNs;
            this.durNs = durNs;
            this.depth = depth;
            this.methodId = methodId;
        }
    }

    public static Match nextMatch(final TraceSnapshot data, final BitSet methods, final long afterNs) {
        Match best = null;
        for (final ThreadIndex m : data.threads) {
            Match ovm = null;
            for (int i = 0; i < m.overview.count; i++) {
                final long s = m.overview.startNs[i];
                if (best != null && s >= best.startNs) {
                    break;
                }
                if (s > afterNs && methods.get(m.overview.methodId[i])) {
                    ovm = new Match(m.tid, s, m.overview.durNs[i], m.overview.depth[i], m.overview.methodId[i]);
                    break;
                }
            }
            final Match fine = fineNext(data, m, methods, afterNs,
                    ovm != null ? ovm.startNs : (best != null ? best.startNs : Long.MAX_VALUE));
            final Match cand = fine != null ? fine : ovm;
            if (cand != null && (best == null || cand.startNs < best.startNs)) {
                best = cand;
            }
        }
        return best;
    }

    public static Match prevMatch(final TraceSnapshot data, final BitSet methods, final long beforeNs) {
        Match best = null;
        for (final ThreadIndex m : data.threads) {
            Match ovm = null;
            for (int i = m.overview.count - 1; i >= 0; i--) {
                final long s = m.overview.startNs[i];
                if (best != null && s <= best.startNs) {
                    break;
                }
                if (s < beforeNs && methods.get(m.overview.methodId[i])) {
                    ovm = new Match(m.tid, s, m.overview.durNs[i], m.overview.depth[i], m.overview.methodId[i]);
                    break;
                }
            }
            final Match fine = finePrev(data, m, methods, beforeNs,
                    ovm != null ? ovm.startNs : (best != null ? best.startNs : Long.MIN_VALUE));
            final Match cand = fine != null ? fine : ovm;
            if (cand != null && (best == null || cand.startNs > best.startNs)) {
                best = cand;
            }
        }
        return best;
    }

    private static Match fineNext(final TraceSnapshot data, final ThreadIndex m, final BitSet methods,
            final long afterNs, final long upperBoundNs) {
        final long d = data.overviewThresholdNs;
        if (d <= 0) {
            return null;
        }
        final Match[] out = { null };
        final long[] candStart = { Long.MAX_VALUE };
        final int[] candSp = { -1 };
        ChunkWalker.walkRange(data, m, afterNs, ChunkWalker.saturatingAdd(upperBoundNs, d), new ChunkWalker.Visitor() {
            @Override
            public boolean enter(final long startNs, final int methodId, final int sessionDepth, final int sp) {
                if (candSp[0] < 0 && startNs > afterNs && startNs < upperBoundNs && methods.get(methodId)) {
                    candStart[0] = startNs;
                    candSp[0] = sp;
                }
                return candSp[0] < 0 || startNs <= candStart[0] + d;
            }

            @Override
            public boolean exit(final long startNs, final long durNs, final long childNs, final int methodId,
                    final int sessionDepth, final int sp, final int exc) {
                if (sp == candSp[0]) {
                    if (startNs == candStart[0] && durNs < d) {
                        out[0] = new Match(m.tid, startNs, durNs, sessionDepth, methodId);
                        return false;
                    }
                    candSp[0] = -1;
                    candStart[0] = Long.MAX_VALUE;
                }
                return true;
            }

            @Override
            public void sessionEnd() {
                candSp[0] = -1;
                candStart[0] = Long.MAX_VALUE;
            }
        });
        return out[0];
    }

    private static Match finePrev(final TraceSnapshot data, final ThreadIndex m, final BitSet methods,
            final long beforeNs, final long lowerBoundNs) {
        final long d = data.overviewThresholdNs;
        if (d <= 0) {
            return null;
        }
        final Match[] out = { null };
        ChunkWalker.walkRange(data, m, lowerBoundNs, ChunkWalker.saturatingAdd(beforeNs, d),
                (startNs, durNs, childNs, methodId, sessionDepth, sp, exc) -> {
                    if (durNs < d && startNs < beforeNs && startNs > lowerBoundNs && methods.get(methodId)
                            && (out[0] == null || startNs > out[0].startNs)) {
                        out[0] = new Match(m.tid, startNs, durNs, sessionDepth, methodId);
                    }
                    return true;
                });
        return out[0];
    }
}
