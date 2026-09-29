package io.github.yagipass.verbatime.jmc.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.errorprone.annotations.Var;

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

        private Row(BottomUpModel model, int methodId, String name, long calls, long totalNs,
                long selfNs, Row parent, int[] sumNodes, int[] pathHeads) {
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
            for (int node : pathHeads) {
                if (node != 0) {
                    return true;
                }
            }
            return false;
        }

        int childCount() {
            return children().length;
        }

        Row child(int i) {
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
                for (Row c : children) {
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

    private BottomUpModel(List<Row> rows, SubtreeAggregate agg, TraceSnapshot data, long rootTotalNs,
            boolean truncated, SortKey sortKey, boolean descending) {
        this.rows = rows;
        this.agg = agg;
        this.data = data;
        this.rootTotalNs = rootTotalNs;
        this.truncated = truncated;
        this.sortKey = sortKey;
        this.descending = descending;
    }

    static BottomUpModel of(TraceSnapshot d, SubtreeAggregate a) {
        return of(d, a, SortKey.SELF, true);
    }

    static BottomUpModel of(TraceSnapshot d, SubtreeAggregate a, SortKey sortKey,
            boolean descending) {
        List<Row> rows = new ArrayList<>();
        BottomUpModel m = new BottomUpModel(rows, a, d, a.totalNs(0), a.truncated(), sortKey, descending);
        for (int methodId = 0; methodId < a.methodIdLimit(); methodId++) {
            int count = a.methodNodeCount(methodId);
            if (count == 0) {
                continue;
            }
            int[] nodes = new int[count];
            for (int i = 0; i < count; i++) {
                nodes[i] = a.methodNode(methodId, i);
            }
            rows.add(new Row(m, methodId, d.methodName(methodId), a.methodCalls(methodId), a.methodTotalNs(methodId), a.methodSelfNs(methodId),
                    null, nodes, nodes));
        }
        m.sort();
        return m;
    }

    private Row[] callersOf(Row r) {
        Map<Integer, List<Integer>> byCaller = new HashMap<>();
        for (int i = 0; i < r.pathHeads.length; i++) {
            int node = r.pathHeads[i];
            if (node == 0) {
                continue;
            }
            int callerNode = agg.parent(node);
            int callerMethod = agg.method(callerNode);
            byCaller.computeIfAbsent(callerMethod, k -> new ArrayList<>()).add(i);
        }
        List<Row> out = new ArrayList<>(byCaller.size());
        for (Map.Entry<Integer, List<Integer>> e : byCaller.entrySet()) {
            List<Integer> idx = e.getValue();
            int[] childX = new int[idx.size()];
            int[] childTop = new int[idx.size()];
            @Var long calls = 0;
            @Var long total = 0;
            @Var long self = 0;
            for (int k = 0; k < idx.size(); k++) {
                int i = idx.get(k);
                childX[k] = r.sumNodes[i];
                childTop[k] = agg.parent(r.pathHeads[i]);
                calls += agg.calls(childX[k]);
                total += agg.totalNs(childX[k]);
                self += agg.selfNs(childX[k]);
            }
            out.add(new Row(this, e.getKey(), data.methodName(e.getKey()), calls, total, self, r, childX, childTop));
        }
        Row[] arr = out.toArray(new Row[0]);
        sortRows(arr);
        return arr;
    }

    double pct(long ns) {
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

    Row row(int i) {
        return rows.get(i);
    }

    SortKey sortKey() {
        return sortKey;
    }

    boolean descending() {
        return descending;
    }

    void toggleSort(SortKey c) {
        if (c == sortKey) {
            descending = !descending;
        } else {
            sortKey = c;
            descending = true;
        }
        sort();
    }

    private void sort() {
        Row[] top = rows.toArray(new Row[0]);
        sortRows(top);
        rows.clear();
        for (Row r : top) {
            rows.add(r);
            r.resortCachedDescendants();
        }
    }

    private void sortRows(Row[] arr) {
        @Var Comparator<Row> cmp = switch (sortKey) {
            case SELF -> Comparator.comparingLong(Row::selfNs);
            case TOTAL -> Comparator.comparingLong(Row::totalNs);
            case CALLS -> Comparator.comparingLong(Row::calls);
            case METHOD -> Comparator.comparing(Row::name);
        };
        if (descending) {
            cmp = cmp.reversed();
        }
        Comparator<Row> stable = cmp.thenComparingInt(Row::methodId);
        Arrays.sort(arr, stable);
    }

    @Override
    public int rootCount() {
        return size();
    }

    @Override
    public Row root(int i) {
        return row(i);
    }

    @Override
    public boolean hasChildren(Row r) {
        return r.hasChildren();
    }

    @Override
    public int childCount(Row r) {
        return r.childCount();
    }

    @Override
    public Row child(Row r, int i) {
        return r.child(i);
    }

    @Override
    public Row parent(Row r) {
        return r.parent();
    }
}
