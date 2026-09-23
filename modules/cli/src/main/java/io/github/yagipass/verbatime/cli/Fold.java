package io.github.yagipass.verbatime.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Fold {

    long count;

    long nested;

    long thrown;

    long ticks;

    long firstStartTicks = Long.MAX_VALUE;

    boolean byDepth;

    Map<Integer, Long> countByMethod;

    void add(final int methodId, final long startTicks, final long durTicks, final long subtreeCalls,
            final boolean threw) {
        count++;
        nested += subtreeCalls;
        ticks += durTicks;
        if (threw) {
            thrown++;
        }
        firstStartTicks = Math.min(firstStartTicks, startTicks);
        if (countByMethod == null) {
            countByMethod = new HashMap<>();
        }
        countByMethod.merge(methodId, 1L, Long::sum);
    }

    Fold copy() {
        final Fold c = new Fold();
        c.count = count;
        c.nested = nested;
        c.thrown = thrown;
        c.ticks = ticks;
        c.firstStartTicks = firstStartTicks;
        c.byDepth = byDepth;
        c.countByMethod = countByMethod;
        return c;
    }

    void reset() {
        count = 0;
        nested = 0;
        thrown = 0;
        ticks = 0;
        firstStartTicks = Long.MAX_VALUE;
        byDepth = false;
        countByMethod = null;
    }

    String methodsText(final Names names, final int max) {
        return methodsText(countByMethod, names, max);
    }

    static String methodsText(final Map<Integer, Long> countByMethod, final Names names, final int max) {
        final List<Map.Entry<Integer, Long>> e = new ArrayList<>(countByMethod.entrySet());
        e.sort((x, y) -> {
            final int c = Long.compare(y.getValue(), x.getValue());
            return c != 0 ? c : Integer.compare(x.getKey(), y.getKey());
        });
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e.size() && i < max; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names.displayName(e.get(i).getKey()));
            if (e.get(i).getValue() > 1) {
                sb.append('×').append(Formats.grouped(e.get(i).getValue()));
            }
        }
        if (e.size() > max) {
            sb.append(", +").append(e.size() - max).append(" more");
        }
        return sb.toString();
    }
}
