package io.github.yagipass.verbatime.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class CallTree {

    private final TreeCommand tree;

    private final Out out;

    CallTree(final TreeCommand tree) {
        this.tree = tree;
        this.out = tree.out;
    }

    void print() {
        final long floor = tree.autoFloor() ? chooseFloor() : tree.floorTicks();
        final Lines lines = new Lines(tree.walker, tree.at, floor, tree.maxDepth, tree.limit);
        tree.walk(lines);
        tree.header(lines, lines.subtreeCalls);
        out.text("floor: " + tree.floorText(floor, "none, every call is printed") + "  depth: "
                + (tree.maxDepth == Integer.MAX_VALUE ? "all" : tree.maxDepth + " below the first line"));
        out.json(tree.optionsJson(floor, false));
        tree.exceptionsLegend(lines.thrown.countByException);
        tree.gcLegend(lines.startTicks, lines.durTicks);
        out.text("");
        final long sessionStart = tree.walker.startTicks;
        final Legend legend = new Legend(tree.names);
        Line largestFold = null;
        for (final Line l : lines.lines) {
            if (l.fold == null) {
                printCall(l, sessionStart);
                legend.add(l.methodId);
            } else {
                printFold(l, sessionStart);
                if (largestFold == null || l.fold.ticks > largestFold.fold.ticks) {
                    largestFold = l;
                }
            }
        }
        legend.print(out);
        out.more(lines.more, "lines", tree.args.commandWith("limit", tree.limit * 2L));
        if (largestFold != null) {
            printLargestFold(largestFold, lines.rootOrdinal, floor);
        }
    }

    private long chooseFloor() {
        final FloorHeap heap = new FloorHeap(Math.max(tree.limit * 2 / 3, 1));
        tree.walk(new SubtreeVisitor(tree.walker, tree.at) {

            @Override
            void onEnter(final long ordinal, final int level, final int depth, final int methodId,
                    final long startTicks) {
            }

            @Override
            void onExit(final long ordinal, final int level, final int depth, final int methodId,
                    final long startTicks, final long durTicks, final long selfTicks, final int exceptionId,
                    final boolean unclosed) {
                if (level <= tree.maxDepth) {
                    heap.offer(durTicks);
                }
            }
        });
        return heap.floor();
    }

    private void printCall(final Line l, final long sessionStart) {
        final String id = tree.callId(l.ordinal);
        final StringBuilder sb = new StringBuilder();
        sb.append(id).append(' ').append(Formats.ms(l.startTicks - sessionStart)).append(' ')
                .append(Formats.ms(l.durTicks)).append(' ').append(l.depth).append(' ')
                .append(tree.names.displayName(l.methodId));
        if (l.children > 0) {
            sb.append(" self ").append(Formats.ms(l.selfTicks));
        }
        if (l.exceptionId != SessionWalker.NO_EXCEPTION) {
            sb.append(" !");
            if (l.exceptionId > 0) {
                sb.append('e').append(l.exceptionId);
            }
        }
        if (l.unclosed) {
            sb.append(" ~");
        }
        out.text(sb.toString());
        out.json(new Json("call").put("id", id).ms("start_ms", l.startTicks - sessionStart)
                .ms("dur_ms", l.durTicks).put("depth", l.depth).put("method", tree.names.displayName(l.methodId))
                .ms("self_ms", l.selfTicks).put("children", l.children)
                .put("exception", l.exceptionId == SessionWalker.NO_EXCEPTION ? null
                        : tree.file.exceptionName(l.exceptionId))
                .put("unclosed", l.unclosed));
    }

    private void printFold(final Line l, final long sessionStart) {
        final Fold fold = l.fold;
        final String parent = tree.callId(l.ordinal);
        final StringBuilder sb = new StringBuilder();
        sb.append("- ").append(Formats.ms(fold.firstStartTicks - sessionStart)).append(' ')
                .append(Formats.ms(fold.ticks)).append(' ').append(l.depth).append(" ·")
                .append(Formats.plural(fold.count, "call")).append(" of ").append(parent).append(' ')
                .append(fold.byDepth ? "deeper than --depth" : "< " + Formats.duration(l.floorTicks));
        if (fold.nested > fold.count) {
            sb.append(", ").append(Formats.grouped(fold.nested)).append(" incl. nested");
        }
        if (fold.thrown > 0) {
            sb.append(" [!").append(Formats.grouped(fold.thrown)).append(']');
        }
        sb.append(": ").append(fold.methodsText(tree.names, 5));
        out.text(sb.toString());
        out.json(new Json("folded").put("parent", parent).ms("start_ms", fold.firstStartTicks - sessionStart)
                .ms("dur_ms", fold.ticks).put("depth", l.depth).put("calls", fold.count).put("nested", fold.nested)
                .put("thrown", fold.thrown).put("reason", fold.byDepth ? "depth" : "floor")
                .put("methods", fold.methodsText(tree.names, 20)));
    }

    private void printLargestFold(final Line l, final long rootOrdinal, final long floor) {
        final String parent = tree.callId(l.ordinal);
        final String next;
        if (l.ordinal != rootOrdinal) {
            next = tree.args.commandWith("at", parent, "floor", null);
        } else if (l.fold.byDepth) {
            next = tree.args.commandWith("depth", tree.maxDepth + 2L);
        } else {
            next = tree.args.commandWith("floor", Formats.duration(Formats.roundUpTicks(Math.max(floor / 4, 1))));
        }
        out.text("# largest fold: " + Formats.ms(l.fold.ticks) + " ms in " + Formats.grouped(l.fold.count)
                + " calls under " + parent + ". next: " + next);
        out.json(new Json("hint").put("fold_under", parent).ms("fold_ms", l.fold.ticks).put("next", next));
    }

    private static final class Line {

        final long ordinal;

        final int depth;

        final int methodId;

        final long startTicks;

        long durTicks;

        long selfTicks;

        int children;

        int exceptionId = SessionWalker.NO_EXCEPTION;

        boolean unclosed;

        Fold fold;

        long floorTicks;

        Line(final long ordinal, final int depth, final int methodId, final long startTicks) {
            this.ordinal = ordinal;
            this.depth = depth;
            this.methodId = methodId;
            this.startTicks = startTicks;
        }
    }

    private static final class Lines extends SubtreeVisitor {

        final List<Line> lines = new ArrayList<>();

        long more;

        long subtreeCalls;

        long rootOrdinal = -1;

        private final long floorTicks;

        private final int maxDepth;

        private final int limit;

        private boolean recording = true;

        private long closed;

        private int[] lineIndex = new int[64];

        private long[] subtree = new long[64];

        private int[] children = new int[64];

        private Fold[] folds = new Fold[64];

        Lines(final SessionWalker walker, final long at, final long floorTicks, final int maxDepth, final int limit) {
            super(walker, at);
            this.floorTicks = floorTicks;
            this.maxDepth = maxDepth;
            this.limit = limit;
        }

        @Override
        void onEnter(final long ordinal, final int level, final int depth, final int methodId,
                final long startTicks) {
            if (level == lineIndex.length) {
                allocate(level * 2);
            }
            if (level > 0) {
                children[level - 1]++;
            } else if (rootOrdinal < 0) {
                rootOrdinal = ordinal;
            }
            lineIndex[level] = -1;
            subtree[level] = 0;
            children[level] = 0;
            if (folds[level] == null) {
                folds[level] = new Fold();
            } else {
                folds[level].reset();
            }
            if (level <= maxDepth && recording) {
                lineIndex[level] = lines.size();
                lines.add(new Line(ordinal, depth, methodId, startTicks));
            }
        }

        @Override
        void onExit(final long ordinal, final int level, final int depth, final int methodId, final long startTicks,
                final long durTicks, final long selfTicks, final int exceptionId, final boolean unclosed) {
            subtree[level]++;
            if (level > 0) {
                subtree[level - 1] += subtree[level];
            } else {
                subtreeCalls += subtree[level];
            }
            final int index = lineIndex[level];
            final boolean shown = level == 0 || (level <= maxDepth && durTicks >= floorTicks);
            if (!shown) {
                if (index >= 0) {
                    lines.subList(index, lines.size()).clear();
                }
                if (level > 0 && level - 1 <= maxDepth) {
                    final Fold parent = folds[level - 1];
                    parent.add(methodId, startTicks, durTicks, subtree[level],
                            exceptionId != SessionWalker.NO_EXCEPTION);
                    parent.byDepth = level > maxDepth;
                }
                return;
            }
            final Fold fold = folds[level];
            if (index < 0) {
                more += fold.count > 0 ? 2 : 1;
                return;
            }
            final Line l = lines.get(index);
            l.durTicks = durTicks;
            l.selfTicks = selfTicks;
            l.children = children[level];
            l.exceptionId = exceptionId;
            l.unclosed = unclosed;
            closed++;
            if (fold.count > 0) {
                if (recording) {
                    final Line folded = new Line(ordinal, depth + 1, -1, fold.firstStartTicks);
                    folded.fold = fold.copy();
                    folded.floorTicks = floorTicks;
                    lines.add(folded);
                    closed++;
                } else {
                    more++;
                }
            }
            if (closed >= limit) {
                recording = false;
            }
        }

        private void allocate(final int capacity) {
            lineIndex = Arrays.copyOf(lineIndex, capacity);
            subtree = Arrays.copyOf(subtree, capacity);
            children = Arrays.copyOf(children, capacity);
            folds = Arrays.copyOf(folds, capacity);
        }
    }
}
