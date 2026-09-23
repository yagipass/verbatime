package io.github.yagipass.verbatime.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

final class FindCommand {

    static final String HELP = """
            vbtm find <file> PATTERN [--session SESSION] [--min DUR] [--thrown] [--sort dur|start] [--limit N] [--all]
              [--json]

            Lists the calls of a method, to get the ids of the slow ones for tree --at and to compare one call with
            the others.

              --session S  search one session instead of all of them
              --min DUR    only calls at least this long, such as 5ms
              --thrown     only calls that ended by throwing
              --sort KEY   dur, the default, puts the longest first. start follows session order, then time
              --limit N    rows to print, 50 by default
              --all        accept a PATTERN that matches several methods

            Columns: id, start, dur, self, depth, method, caller and flags. start is ms from the start of the call's
            session, caller is the method that made the call, and flags are !Exception when the call ended by
            throwing and ~ when it was still open when the recording ended.
            """;

    private FindCommand() {
    }

    private record Match(int session, long ordinal, long startTicks, long durTicks, long selfTicks, int depth,
            int methodId, int callerId, int exceptionId, boolean unclosed) {

        CallId id() {
            return new CallId(session, ordinal);
        }
    }

    private static final class Matches implements SessionWalker.Visitor {

        private final PriorityQueue<Match> kept;

        long count;

        long totalTicks;

        private final MethodPattern pattern;

        private final SessionWalker walker;

        private final long minTicks;

        private final boolean thrownOnly;

        private final int limit;

        private final Comparator<Match> order;

        private final CallStack stack = new CallStack();

        private int session;

        private int openMatches;

        Matches(final MethodPattern pattern, final SessionWalker walker, final long minTicks,
                final boolean thrownOnly, final int limit, final Comparator<Match> order) {
            this.pattern = pattern;
            this.walker = walker;
            this.minTicks = minTicks;
            this.thrownOnly = thrownOnly;
            this.limit = limit;
            this.order = order;
            this.kept = new PriorityQueue<>(order.reversed());
        }

        void walk(final TraceFile.Session s) {
            session = s.number;
            walker.walk(s, this);
        }

        List<Match> rows() {
            final List<Match> rows = new ArrayList<>(kept);
            rows.sort(order);
            return rows;
        }

        @Override
        public void enter(final long ordinal, final int depth, final int methodId, final long startTicks) {
            stack.push(depth, methodId);
            if (pattern.matches(methodId)) {
                openMatches++;
            }
        }

        @Override
        public void exit(final long ordinal, final int depth, final int methodId, final long startTicks,
                final long durTicks, final long selfTicks, final int exceptionId, final boolean unclosed) {
            if (!pattern.matches(methodId)) {
                return;
            }
            openMatches--;
            if (durTicks < minTicks || (thrownOnly && exceptionId == SessionWalker.NO_EXCEPTION)) {
                return;
            }
            count++;
            if (openMatches == 0) {
                totalTicks += durTicks;
            }
            final Match m = new Match(session, ordinal, startTicks - walker.startTicks, durTicks, selfTicks, depth,
                    methodId, stack.callerOf(depth), exceptionId, unclosed);
            if (kept.size() < limit) {
                kept.add(m);
            } else if (order.compare(m, kept.peek()) < 0) {
                kept.poll();
                kept.add(m);
            }
        }
    }

    static int run(final List<String> argv, final PrintStream stdout) {
        final Args args = Args.parse("find", argv, Set.of("session", "min", "sort", "limit"),
                Set.of("json", "all", "thrown"));
        args.rejectPositionalsBeyond(2);
        final TraceFile file = TraceFile.open(args.positional(0, "the .vbtm file"));
        final Out out = new Out(stdout, args.has("json"));
        final Names names = new Names(file);
        final MethodPattern pattern = MethodPattern.resolve(file, names, args.positional(1, "the method PATTERN"),
                args.has("all"));
        final Scope scope = Scope.of(file, args.value("session"));
        final long minTicks = args.ticks("min", 0);
        final String sort = args.choice("sort", "dur", "dur", "start");
        final int limit = args.positiveInt("limit", 50);
        final boolean thrownOnly = args.has("thrown");

        final Comparator<Match> order = sort.equals("dur")
                ? Comparator.comparingLong((Match m) -> -m.durTicks()).thenComparingInt(Match::session)
                        .thenComparingLong(Match::ordinal)
                : Comparator.comparingInt(Match::session).thenComparingLong(Match::ordinal);
        final Matches matches = new Matches(pattern, new SessionWalker(file), minTicks, thrownOnly, limit, order);
        for (final TraceFile.Session s : scope.sessions) {
            matches.walk(s);
        }
        final List<Match> rows = matches.rows();

        out.text("pattern: " + pattern.text + " -> " + pattern.fullNames(names) + "  scope: " + scope.label);
        out.text("matches: " + Formats.grouped(matches.count) + " calls"
                + (minTicks > 0 ? " >= " + Formats.duration(minTicks) : "") + (thrownOnly ? " that threw" : "") + ", "
                + Formats.ms(matches.totalTicks)
                + " ms in total, where a call inside another call of the same method is not added again");
        out.text("sorted by " + sort + ", showing " + Formats.grouped(rows.size())
                + ". start is ms from the session start");
        out.text("");
        out.json(new Json("find").put("pattern", pattern.text).put("scope", scope.label).put("calls", matches.count)
                .ms("total_ms", matches.totalTicks).put("sort", sort).put("thrown", thrownOnly));
        final Out.Table table = new Out.Table(">id", ">start", ">dur", ">self", ">depth", "method", "caller", "flags");
        final Legend legend = new Legend(names);
        for (final Match m : rows) {
            final String id = m.id().toString();
            final String caller = m.callerId() < 0 ? null : names.displayName(m.callerId());
            final String exception = m.exceptionId() == SessionWalker.NO_EXCEPTION ? null
                    : file.exceptionName(m.exceptionId());
            final String flags = (exception == null ? "" : m.exceptionId() > 0 ? "!" + Names.simpleClass(exception)
                    : "!")
                    + (m.unclosed() ? " ~" : "");
            table.add(id, Formats.ms(m.startTicks()), Formats.ms(m.durTicks()), Formats.ms(m.selfTicks()),
                    String.valueOf(m.depth()), names.displayName(m.methodId()), caller == null ? "-" : caller,
                    flags.trim());
            out.json(new Json("call").put("id", id).ms("start_ms", m.startTicks()).ms("dur_ms", m.durTicks())
                    .ms("self_ms", m.selfTicks()).put("depth", m.depth()).put("method", names.displayName(m.methodId()))
                    .put("caller", caller).put("exception", exception).put("unclosed", m.unclosed()));
            legend.add(m.methodId());
            legend.add(m.callerId());
        }
        table.print(out);
        legend.print(out);
        out.more(matches.count - rows.size(), "calls", args.commandWith("limit", limit * 3L));
        out.status(file);
        return file.exitCode();
    }
}
