package io.github.yagipass.verbatime.jmc.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;

final class BottomUpModel implements AggregateTreeView.AggregateTreeModel<BottomUpModel.Row> {

    enum SortKey {
        SELF, TOTAL, CALLS, METHOD
    }

    static final class Row {

        private final BottomUpModel model;

        private final int methodId;

        private final String name;

        private final long calls;

        private final long totalNs;

        private final long selfNs;

        private final Row parent;

        private final int[] sumNodes;

        private final int[] pathHeads;

        private Row[] children;

        private Row(final BottomUpModel model, final int methodId, final String name, final long calls, final long totalNs,
                final long selfNs, final Row parent, final int[] sumNodes, final int[] pathHeads) {
            this.model = model;
            this.methodId = methodId;
            this.name = name;
            this.calls = calls;
            this.totalNs = totalNs;
            this.selfNs = selfNs;
            this.parent = parent;
            this.sumNodes = sumNodes;
            this.pathHeads = pathHeads;
        }

        int methodId() {
            return methodId;
        }

        String name() {
            return name;
        }

        long calls() {
            return calls;
        }

        long totalNs() {
            return totalNs;
        }

        long selfNs() {
            return selfNs;
        }

        private Row parent() {
            return parent;
        }

        boolean hasChildren() {
            for (final int node : pathHeads) {
                if (node != 0) {
                    return true;
                }
            }
            return false;
        }

        int childCount() {
            return children().length;
        }

        Row child(final int i) {
            return children()[i];
        }

        private Row[] children() {
            if (children == null) {
                children = model.callersOf(this);
            }
            return children;
        }

        private void resortCachedDescendants() {
            if (children != null) {
                model.sortRows(children);
                for (final Row c : children) {
                    c.resortCachedDescendants();
                }
            }
        }
    }

    private final List<Row> rows;

    private final SubtreeAggregate agg;

    private final TraceSnapshot data;

    private final long rootTotalNs;

    private final boolean truncated;

    private SortKey sortKey;

    private boolean descending;

    private BottomUpModel(final List<Row> rows, final SubtreeAggregate agg, final TraceSnapshot data, final long rootTotalNs,
            final boolean truncated, final SortKey sortKey, final boolean descending) {
        this.rows = rows;
        this.agg = agg;
        this.data = data;
        this.rootTotalNs = rootTotalNs;
        this.truncated = truncated;
        this.sortKey = sortKey;
        this.descending = descending;
    }

    static BottomUpModel of(final TraceSnapshot d, final SubtreeAggregate a) {
        return of(d, a, SortKey.SELF, true);
    }

    static BottomUpModel of(final TraceSnapshot d, final SubtreeAggregate a, final SortKey sortKey,
            final boolean descending) {
        final List<Row> rows = new ArrayList<>();
        final BottomUpModel m = new BottomUpModel(rows, a, d, a.totalNs(0), a.truncated(), sortKey, descending);
        for (int methodId = 0; methodId < a.methodIdLimit(); methodId++) {
            final int count = a.methodNodeCount(methodId);
            if (count == 0) {
                continue;
            }
            final int[] nodes = new int[count];
            for (int i = 0; i < count; i++) {
                nodes[i] = a.methodNode(methodId, i);
            }
            rows.add(new Row(m, methodId, d.methodName(methodId), a.methodCalls(methodId), a.methodTotalNs(methodId), a.methodSelfNs(methodId),
                    null, nodes, nodes));
        }
        m.sort();
        return m;
    }

    private Row[] callersOf(final Row r) {
        final Map<Integer, List<Integer>> byCaller = new HashMap<>();
        for (int i = 0; i < r.pathHeads.length; i++) {
            final int node = r.pathHeads[i];
            if (node == 0) {
                continue;
            }
            final int callerNode = agg.parent(node);
            final int callerMethod = agg.method(callerNode);
            byCaller.computeIfAbsent(callerMethod, k -> new ArrayList<>()).add(i);
        }
        final List<Row> out = new ArrayList<>(byCaller.size());
        for (final Map.Entry<Integer, List<Integer>> e : byCaller.entrySet()) {
            final List<Integer> idx = e.getValue();
            final int[] childX = new int[idx.size()];
            final int[] childTop = new int[idx.size()];
            long calls = 0;
            long total = 0;
            long self = 0;
            for (int k = 0; k < idx.size(); k++) {
                final int i = idx.get(k);
                childX[k] = r.sumNodes[i];
                childTop[k] = agg.parent(r.pathHeads[i]);
                calls += agg.calls(childX[k]);
                total += agg.totalNs(childX[k]);
                self += agg.selfNs(childX[k]);
            }
            out.add(new Row(this, e.getKey(), data.methodName(e.getKey()), calls, total, self, r, childX, childTop));
        }
        final Row[] arr = out.toArray(new Row[0]);
        sortRows(arr);
        return arr;
    }

    double pct(final long ns) {
        return rootTotalNs > 0 ? ns * 100.0 / rootTotalNs : 0;
    }

    long rootTotalNs() {
        return rootTotalNs;
    }

    String rootName() {
        return data.methodName(agg.method(0));
    }

    @Override
    public SubtreeAggregate aggregate() {
        return agg;
    }

    boolean truncated() {
        return truncated;
    }

    List<Row> rows() {
        return List.copyOf(rows);
    }

    int size() {
        return rows.size();
    }

    Row row(final int i) {
        return rows.get(i);
    }

    SortKey sortKey() {
        return sortKey;
    }

    boolean descending() {
        return descending;
    }

    void toggleSort(final SortKey c) {
        if (c == sortKey) {
            descending = !descending;
        } else {
            sortKey = c;
            descending = true;
        }
        sort();
    }

    private void sort() {
        final Row[] top = rows.toArray(new Row[0]);
        sortRows(top);
        rows.clear();
        for (final Row r : top) {
            rows.add(r);
            r.resortCachedDescendants();
        }
    }

    private void sortRows(final Row[] arr) {
        Comparator<Row> cmp = switch (sortKey) {
            case SELF -> Comparator.comparingLong(Row::selfNs);
            case TOTAL -> Comparator.comparingLong(Row::totalNs);
            case CALLS -> Comparator.comparingLong(Row::calls);
            case METHOD -> Comparator.comparing(Row::name);
        };
        if (descending) {
            cmp = cmp.reversed();
        }
        final Comparator<Row> stable = cmp.thenComparingInt(Row::methodId);
        Arrays.sort(arr, stable);
    }

    @Override
    public int rootCount() {
        return size();
    }

    @Override
    public Row root(final int i) {
        return row(i);
    }

    @Override
    public boolean hasChildren(final Row r) {
        return r.hasChildren();
    }

    @Override
    public int childCount(final Row r) {
        return r.childCount();
    }

    @Override
    public Row child(final Row r, final int i) {
        return r.child(i);
    }

    @Override
    public Row parent(final Row r) {
        return r.parent();
    }
}
