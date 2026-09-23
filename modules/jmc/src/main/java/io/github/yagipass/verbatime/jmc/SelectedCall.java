package io.github.yagipass.verbatime.jmc;

import java.util.ArrayList;
import java.util.List;

import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;

public record SelectedCall(long tid, long startNs, long durNs, long selfNs, int depth, int methodId,
        int exceptionId, boolean unclosed, List<Ancestor> ancestors, SubtreeAggregate subtree, String subtreeError) {

    static final String NOT_FOUND = "Selected call was not found in the recording";

    public record Ancestor(long startNs, long durNs, int depth, int methodId) {
    }

    public SelectedCall {
        ancestors = List.copyOf(ancestors);
    }

    public boolean thrown() {
        return exceptionId >= 0;
    }

    SelectedCall withSubtree(final SubtreeAggregate a) {
        if (!a.found()) {
            return withSubtreeError(NOT_FOUND);
        }
        return new SelectedCall(tid, startNs, durNs, selfNs, depth, methodId, exceptionId, unclosed, ancestors, a, null);
    }

    SelectedCall withSubtreeError(final String error) {
        return new SelectedCall(tid, startNs, durNs, selfNs, depth, methodId, exceptionId, unclosed, ancestors, null,
                error);
    }

    public long effectiveSelfNs() {
        return subtree != null ? subtree.selfNs(0) : selfNs;
    }

    public List<Ancestor> pathFromRoot() {
        final List<Ancestor> out = new ArrayList<>(ancestors.size() + 1);
        out.addAll(ancestors);
        out.add(new Ancestor(startNs, durNs, depth, methodId));
        return out;
    }

    static List<Ancestor> parseAncestors(final Object raw, final int selectedDepth) {
        if (!(raw instanceof final Object[] a)) {
            return List.of();
        }
        final int n = a.length / 3;
        final List<Ancestor> out = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            if (!(a[3 * j] instanceof final Number ts) || !(a[3 * j + 1] instanceof final Number dur)
                    || !(a[3 * j + 2] instanceof final Number nm)) {
                return List.of();
            }
            out.add(new Ancestor(ts.longValue(), dur.longValue(), selectedDepth - (n - j), (int) nm.doubleValue()));
        }
        return out;
    }
}
