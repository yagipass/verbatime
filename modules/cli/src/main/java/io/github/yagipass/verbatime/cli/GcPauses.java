package io.github.yagipass.verbatime.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GcPauses {

    private final List<TraceFile.GcPause> byStart;

    private final long longestTicks;

    GcPauses(final TraceFile file) {
        byStart = new ArrayList<>(file.gcPauses);
        byStart.sort(Comparator.comparingLong(TraceFile.GcPause::startTicks));
        long longest = 0;
        for (final TraceFile.GcPause g : byStart) {
            longest = Math.max(longest, g.durTicks());
        }
        longestTicks = longest;
    }

    int count() {
        return byStart.size();
    }

    long totalTicks() {
        long t = 0;
        for (final TraceFile.GcPause g : byStart) {
            t += g.durTicks();
        }
        return t;
    }

    List<TraceFile.GcPause> overlapping(final long startTicks, final long endTicks) {
        final List<TraceFile.GcPause> r = new ArrayList<>();
        for (int i = firstCandidate(startTicks); i < byStart.size(); i++) {
            final TraceFile.GcPause g = byStart.get(i);
            if (g.startTicks() >= endTicks) {
                break;
            }
            if (g.endTicks() > startTicks) {
                r.add(g);
            }
        }
        return r;
    }

    long overlapTicks(final long startTicks, final long endTicks) {
        long t = 0;
        for (final TraceFile.GcPause g : overlapping(startTicks, endTicks)) {
            t += Math.min(endTicks, g.endTicks()) - Math.max(startTicks, g.startTicks());
        }
        return t;
    }

    private int firstCandidate(final long startTicks) {
        final long from = startTicks - longestTicks;
        int lo = 0;
        int hi = byStart.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (byStart.get(mid).startTicks() < from) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
