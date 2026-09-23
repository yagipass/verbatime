package io.github.yagipass.verbatime.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class HotCommand {

    static final String HELP = """
            vbtm hot <file> [SESSION] [--by self|total|calls] [--limit N] [--json]

            Ranks methods by the time spent in them, over one session or over every session when SESSION is left out.
            Use it to find which methods to look at before reading a tree.

              --by KEY    self, the default: time in the method's own code, excluding the calls it makes
                          total: time from entry to exit, including callees. A recursive method counts only its
                          outermost call
                          calls: number of calls
              --limit N   rows to print, 30 by default

            self% is the share of the scope's time, the sum of the root calls' durations.
            """;

    private HotCommand() {
    }

    static int run(final List<String> argv, final PrintStream stdout) {
        final Args args = Args.parse("hot", argv, Set.of("by", "limit"), Set.of("json"));
        args.rejectPositionalsBeyond(2);
        final TraceFile file = TraceFile.open(args.positional(0, "the .vbtm file"));
        final Out out = new Out(stdout, args.has("json"));
        final Names names = new Names(file);
        final Scope scope = Scope.of(file, args.positionalOrNull(1));
        final String by = args.choice("by", "self", "self", "total", "calls");
        final int limit = args.positiveInt("limit", 30);

        final MethodTotals totals = new MethodTotals(Math.max(file.methodCount, 16));
        final SessionWalker walker = new SessionWalker(file);
        for (final TraceFile.Session s : scope.sessions) {
            walker.walk(s, totals);
        }

        final List<Integer> ids = new ArrayList<>();
        for (int id = 0; id < totals.calls.length; id++) {
            if (totals.calls[id] > 0) {
                ids.add(id);
            }
        }
        final long[] key = switch (by) {
            case "total" -> totals.totalTicks;
            case "calls" -> totals.calls;
            default -> totals.selfTicks;
        };
        ids.sort(Comparator.comparingLong((Integer id) -> -key[id]).thenComparingInt(id -> id));

        out.text("scope: " + scope.label + ", " + Formats.ms(totals.rootTicks) + " ms in root calls, "
                + Formats.grouped(totals.allCalls) + " calls, " + Formats.grouped(ids.size()) + " methods");
        out.text("sorted by " + by + ", showing " + Formats.grouped(Math.min(limit, ids.size())) + ". units: ms");
        out.text("");
        out.json(new Json("scope").put("scope", scope.label).ms("root_ms", totals.rootTicks)
                .put("calls", totals.allCalls).put("methods", ids.size()).put("sort", by));
        final Out.Table table = new Out.Table(">self_ms", ">self%", ">total_ms", ">calls", "method");
        final int shown = Math.min(limit, ids.size());
        final Legend legend = new Legend(names);
        for (int i = 0; i < shown; i++) {
            final int id = ids.get(i);
            table.add(Formats.ms(totals.selfTicks[id]), Formats.percent(totals.selfTicks[id], totals.rootTicks),
                    Formats.ms(totals.totalTicks[id]), Formats.grouped(totals.calls[id]), names.displayName(id));
            out.json(new Json("method").put("method", names.displayName(id)).put("full", names.fullName(id))
                    .ms("self_ms", totals.selfTicks[id]).ms("total_ms", totals.totalTicks[id])
                    .put("calls", totals.calls[id]));
            legend.add(id);
        }
        table.print(out);
        legend.print(out);
        out.more(ids.size() - shown, "methods", args.commandWith("limit", limit * 3L));
        out.status(file);
        return file.exitCode();
    }

    private static final class MethodTotals implements SessionWalker.Visitor {

        long[] calls;

        long[] selfTicks;

        long[] totalTicks;

        long rootTicks;

        long allCalls;

        private int[] openCalls;

        MethodTotals(final int capacity) {
            calls = new long[capacity];
            selfTicks = new long[capacity];
            totalTicks = new long[capacity];
            openCalls = new int[capacity];
        }

        @Override
        public void enter(final long ordinal, final int depth, final int methodId, final long startTicks) {
            if (methodId >= calls.length) {
                allocate(Math.max(methodId + 1, calls.length * 2));
            }
            openCalls[methodId]++;
        }

        @Override
        public void exit(final long ordinal, final int depth, final int methodId, final long startTicks,
                final long durTicks, final long self, final int exceptionId, final boolean unclosed) {
            allCalls++;
            calls[methodId]++;
            selfTicks[methodId] += self;
            if (--openCalls[methodId] == 0) {
                totalTicks[methodId] += durTicks;
            }
            if (depth == 0) {
                rootTicks += durTicks;
            }
        }

        private void allocate(final int capacity) {
            calls = Arrays.copyOf(calls, capacity);
            selfTicks = Arrays.copyOf(selfTicks, capacity);
            totalTicks = Arrays.copyOf(totalTicks, capacity);
            openCalls = Arrays.copyOf(openCalls, capacity);
        }
    }
}
