package io.github.yagipass.verbatime.jmc.views;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;

final class TopDownModel implements AggregateTreeView.AggregateTreeModel<TopDownModel.Row> {

    record Row(int node, int methodId, String name, long calls, long totalNs, long selfNs, int childCount) {
    }

    private final SubtreeAggregate agg;

    private final TraceSnapshot data;

    private final Row root;

    private final Map<Integer, int[]> sortedChildren = new HashMap<>();

    private TopDownModel(SubtreeAggregate agg, TraceSnapshot data) {
        this.agg = agg;
        this.data = data;
        this.root = rowFor(0);
    }

    static TopDownModel of(TraceSnapshot d, SubtreeAggregate a) {
        return new TopDownModel(a, d);
    }

    Row root() {
        return root;
    }

    @Override
    public SubtreeAggregate aggregate() {
        return agg;
    }

    @Override
    public int rootCount() {
        return 1;
    }

    @Override
    public Row root(int i) {
        return root;
    }

    @Override
    public boolean hasChildren(Row r) {
        return r.childCount() > 0;
    }

    @Override
    public int childCount(Row r) {
        return r.childCount();
    }

    @Override
    public Row child(Row r, int i) {
        return child(r.node(), i);
    }

    @Override
    public @Nullable Row parent(Row r) {
        int p = agg.parent(r.node());
        return p < 0 ? null : rowFor(p);
    }

    int size() {
        return agg.nodeCount();
    }

    boolean truncated() {
        return agg.truncated();
    }

    private Row rowFor(int node) {
        return new Row(node, agg.method(node), data.methodName(agg.method(node)), agg.calls(node), agg.totalNs(node),
                agg.selfNs(node), agg.childCount(node));
    }

    Row child(int node, int i) {
        return rowFor(sorted(node)[i]);
    }

    double pct(long ns) {
        long base = agg.totalNs(0);
        return base > 0 ? ns * 100.0 / base : 0;
    }

    private int[] sorted(int node) {
        return sortedChildren.computeIfAbsent(node, n -> {
            int count = agg.childCount(n);
            Integer[] boxed = new Integer[count];
            for (int i = 0; i < count; i++) {
                boxed[i] = agg.child(n, i);
            }
            Arrays.sort(boxed, (x, y) -> {
                int c = Long.compare(agg.totalNs(y), agg.totalNs(x));
                return c != 0 ? c : Integer.compare(agg.method(x), agg.method(y));
            });
            int[] out = new int[count];
            for (int i = 0; i < count; i++) {
                out[i] = boxed[i];
            }
            return out;
        });
    }
}
