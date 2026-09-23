package io.github.yagipass.verbatime.cli;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class MergedTree {

    private final TreeCommand tree;

    private final Out out;

    MergedTree(final TreeCommand tree) {
        this.tree = tree;
        this.out = tree.out;
    }

    void print() {
        final Paths paths = new Paths(tree.walker, tree.at, tree.maxDepth, tree.session.number);
        tree.walk(paths);
        final PathTrie trie = paths.trie;
        long totalCalls = 0;
        for (int n = 1; n < trie.size; n++) {
            totalCalls += trie.calls[n];
        }
        tree.header(paths, totalCalls);
        final long floor = tree.autoFloor() ? chooseFloor(trie) : tree.floorTicks();
        out.text("merged: calls of the same method under the same path are one line. floor: "
                + tree.floorText(floor, "none, every path is printed") + "  depth: "
                + (tree.maxDepth == Integer.MAX_VALUE ? "all" : String.valueOf(tree.maxDepth)));
        if (trie.dropped > 0) {
            out.text("# " + Formats.grouped(trie.dropped) + " calls on paths beyond the "
                    + Formats.grouped(PathTrie.MAX_NODES) + "-path limit are left out. Narrow with --at or --depth");
        }
        out.json(tree.optionsJson(floor, true).put("dropped_calls", trie.dropped));
        tree.exceptionsLegend(paths.thrown.countByException);
        out.text("line: total calls self depth method [!K thrown] [slowest call id], in ms");
        out.text("");
        final int[][] children = trie.childrenByTotal();
        final Legend legend = new Legend(tree.names);
        final int baseDepth = paths.baseDepth();
        long shown = 0;
        long hidden = 0;
        final NodeStack stack = new NodeStack();
        stack.push(0);
        while (!stack.isEmpty()) {
            final int node = stack.node();
            if (stack.state() < 0) {
                stack.setState(0);
                if (node == 0) {
                    continue;
                }
                if (shown >= tree.limit) {
                    hidden++;
                } else {
                    shown++;
                    printPath(trie, node, baseDepth);
                    legend.add(trie.methodId[node]);
                }
                continue;
            }
            final int[] kids = children[node];
            final int next = stack.state();
            if (next < kids.length && trie.totalTicks[kids[next]] >= floor) {
                stack.setState(next + 1);
                stack.push(kids[next]);
                continue;
            }
            if (next < kids.length && node != 0) {
                if (shown >= tree.limit) {
                    hidden++;
                } else {
                    shown++;
                    printFold(trie, node, baseDepth, Arrays.copyOfRange(kids, next, kids.length), floor);
                }
            }
            stack.pop();
        }
        legend.print(out);
        out.more(hidden, "lines", tree.args.commandWith("limit", tree.limit * 2L));
    }

    private long chooseFloor(final PathTrie trie) {
        final FloorHeap heap = new FloorHeap(Math.max(tree.limit * 2 / 3, 1));
        for (int n = 1; n < trie.size; n++) {
            heap.offer(trie.totalTicks[n]);
        }
        return heap.floor();
    }

    private void printPath(final PathTrie trie, final int node, final int baseDepth) {
        final int depth = trie.depth[node] + baseDepth;
        final String method = tree.names.displayName(trie.methodId[node]);
        final StringBuilder sb = new StringBuilder();
        sb.append(Formats.ms(trie.totalTicks[node])).append(' ').append(Formats.grouped(trie.calls[node])).append(' ')
                .append(Formats.ms(trie.selfTicks[node])).append(' ').append(depth).append(' ').append(method);
        if (trie.thrown[node] > 0) {
            sb.append(" [!").append(Formats.grouped(trie.thrown[node])).append(" thrown]");
        }
        final String slowest = trie.slowest(node).toString();
        if (trie.calls[node] > 1) {
            sb.append(" [slowest ").append(slowest).append(' ').append(Formats.ms(trie.slowestTicks[node])).append(']');
        } else {
            sb.append(" [").append(slowest).append(']');
        }
        out.text(sb.toString());
        out.json(new Json("path").ms("total_ms", trie.totalTicks[node]).put("calls", trie.calls[node])
                .ms("self_ms", trie.selfTicks[node]).put("depth", depth).put("method", method)
                .put("thrown", trie.thrown[node]).put("slowest", slowest).ms("slowest_ms", trie.slowestTicks[node]));
    }

    private void printFold(final PathTrie trie, final int parent, final int baseDepth, final int[] hidden,
            final long floor) {
        final Map<Integer, Long> countByMethod = new HashMap<>();
        long ticks = 0;
        long calls = 0;
        for (final int n : hidden) {
            countByMethod.merge(trie.methodId[n], trie.calls[n], Long::sum);
            ticks += trie.totalTicks[n];
            calls += trie.calls[n];
        }
        final int depth = trie.depth[parent] + baseDepth + 1;
        final boolean byDepth = trie.depth[parent] >= tree.maxDepth;
        out.text(Formats.ms(ticks) + " " + Formats.grouped(calls) + " - " + depth + " ·"
                + Formats.plural(hidden.length, "path") + " under " + tree.names.displayName(trie.methodId[parent])
                + " " + (byDepth ? "deeper than --depth" : "< " + Formats.duration(floor)) + ": "
                + Fold.methodsText(countByMethod, tree.names, 5));
        out.json(new Json("folded").ms("total_ms", ticks).put("calls", calls).put("depth", depth)
                .put("paths", hidden.length).put("reason", byDepth ? "depth" : "floor")
                .put("methods", Fold.methodsText(countByMethod, tree.names, 20)));
    }

    private static final class Paths extends SubtreeVisitor {

        final PathTrie trie = new PathTrie();

        private final int maxDepth;

        private final int session;

        private int[] nodes = new int[64];

        Paths(final SessionWalker walker, final long at, final int maxDepth, final int session) {
            super(walker, at);
            this.maxDepth = maxDepth;
            this.session = session;
        }

        @Override
        void onEnter(final long ordinal, final int level, final int depth, final int methodId,
                final long startTicks) {
            if (level == nodes.length) {
                nodes = Arrays.copyOf(nodes, level * 2);
            }
            final int parent = level == 0 ? 0 : nodes[level - 1];
            nodes[level] = parent < 0 || level > maxDepth ? -1 : trie.child(parent, methodId, level);
        }

        @Override
        void onExit(final long ordinal, final int level, final int depth, final int methodId, final long startTicks,
                final long durTicks, final long selfTicks, final int exceptionId, final boolean unclosed) {
            final int node = nodes[level];
            if (node >= 0) {
                trie.record(node, session, ordinal, durTicks, selfTicks, exceptionId != SessionWalker.NO_EXCEPTION);
            }
        }
    }
}
