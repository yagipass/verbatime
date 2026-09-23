package io.github.yagipass.verbatime.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ThrowsCommand {

    static final String HELP = """
            vbtm throws <file> [SESSION] [--by class|thrower|catcher] [--sort total|calls] [--limit N] [--json]

            Lists the exceptions thrown in one session, or in every session when SESSION is left out: their class, the
            method that threw each and the method that caught it. Use it to see whether failed attempts, such as retries
            or lookups that throw when nothing is found, cost time, before reading a tree.

              --by KEY    class, thrower or catcher: one row per exception class, per throwing method or per catching
                          method. Without it, one row per distinct class, thrower and catcher
              --sort KEY  total, the default: time of the calls that threw. calls: number of throws
              --limit N   rows to print, 30 by default

            An exception counts once, not once per call it unwinds through. The recording has no catch event: the catcher
            is the first method the exception did not leave, or - when it left the session's root. calls is the number of
            throws, total_ms the time of the throwing calls, that is the failed attempts, and slowest the id of the longest
            of them, for tree --at. An exception whose class the recording does not name is printed as unknown.
            """;

    private ThrowsCommand() {
    }

    private record Key(int exceptionId, int throwerId, int catcherId) {

        static Key of(final String by, final int exceptionId, final int throwerId, final int catcherId) {
            if (by == null) {
                return new Key(exceptionId, throwerId, catcherId);
            }
            return switch (by) {
                case "class" -> new Key(exceptionId, -1, -1);
                case "thrower" -> new Key(-1, throwerId, -1);
                default -> new Key(-1, -1, catcherId);
            };
        }
    }

    private static final class Row {

        final Key key;

        long calls;

        long totalTicks;

        long slowestTicks;

        int slowestSession;

        long slowestOrdinal;

        Row(final Key key) {
            this.key = key;
        }

        void add(final int session, final long ordinal, final long durTicks) {
            calls++;
            totalTicks += durTicks;
            if (calls == 1 || durTicks > slowestTicks) {
                slowestTicks = durTicks;
                slowestSession = session;
                slowestOrdinal = ordinal;
            }
        }

        CallId slowest() {
            return new CallId(slowestSession, slowestOrdinal);
        }
    }

    private static final class ThrowRows implements SessionWalker.Visitor, Throws.Listener {

        final Throws thrown = new Throws(this);

        final Map<Key, Row> rows = new HashMap<>();

        final Set<Integer> exceptionIds = new HashSet<>();

        final String by;

        int session;

        long totalTicks;

        ThrowRows(final String by) {
            this.by = by;
        }

        @Override
        public void enter(final long ordinal, final int depth, final int methodId, final long startTicks) {
            thrown.enter(depth, methodId);
        }

        @Override
        public void exit(final long ordinal, final int depth, final int methodId, final long startTicks,
                final long durTicks, final long selfTicks, final int exceptionId, final boolean unclosed) {
            thrown.exit(ordinal, depth, methodId, durTicks, exceptionId);
        }

        @Override
        public void thrown(final int exceptionId, final int throwerId, final long throwerOrdinal,
                final long throwerDurTicks, final int catcherId) {
            exceptionIds.add(exceptionId);
            totalTicks += throwerDurTicks;
            rows.computeIfAbsent(Key.of(by, exceptionId, throwerId, catcherId), Row::new).add(session,
                    throwerOrdinal, throwerDurTicks);
        }
    }

    static int run(final List<String> argv, final PrintStream stdout) {
        final Args args = Args.parse("throws", argv, Set.of("by", "sort", "limit"), Set.of("json"));
        args.rejectPositionalsBeyond(2);
        final TraceFile file = TraceFile.open(args.positional(0, "the .vbtm file"));
        final Out out = new Out(stdout, args.has("json"));
        final Names names = new Names(file);
        final Scope scope = Scope.of(file, args.positionalOrNull(1));
        final String by = args.has("by") ? args.choice("by", "class", "class", "thrower", "catcher") : null;
        final String sort = args.choice("sort", "total", "total", "calls");
        final int limit = args.positiveInt("limit", 30);

        final ThrowRows collected = new ThrowRows(by);
        final SessionWalker walker = new SessionWalker(file);
        for (final TraceFile.Session s : scope.sessions) {
            collected.session = s.number;
            walker.walk(s, collected);
            collected.thrown.sessionEnded();
        }

        final List<Row> rows = new ArrayList<>(collected.rows.values());
        rows.sort(order(sort));

        out.text("scope: " + scope.label + ", " + Formats.plural(collected.thrown.count, "throw") + ", "
                + Formats.plural(collected.exceptionIds.size(), "exception class", "exception classes") + ", "
                + Formats.ms(collected.totalTicks) + " ms in the calls that threw");
        out.json(new Json("scope").put("scope", scope.label).put("throws", collected.thrown.count)
                .put("classes", collected.exceptionIds.size()).ms("total_ms", collected.totalTicks).put("by", by)
                .put("sort", sort));
        if (rows.isEmpty()) {
            out.text("no throws in " + scope.label);
            out.status(file);
            return file.exitCode();
        }
        final int shown = Math.min(limit, rows.size());
        final String keyText = by == null ? "class, thrower and catcher" : by;
        out.text("by " + keyText + ", sorted by " + sort + ", showing " + Formats.grouped(shown) + ". units: ms");
        out.text("");
        final Out.Table table = table(by);
        final Legend legend = new Legend(names);
        for (int i = 0; i < shown; i++) {
            final Row r = rows.get(i);
            final Key k = r.key;
            final String slowest = r.slowest().toString();
            final String thrower = k.throwerId() < 0 ? null : names.displayName(k.throwerId());
            final String catcher = k.catcherId() < 0 ? null : names.displayName(k.catcherId());
            final String exception = k.exceptionId() < 0 ? null : file.exceptionName(k.exceptionId());
            final List<String> cells = new ArrayList<>(List.of(Formats.grouped(r.calls), Formats.ms(r.totalTicks),
                    slowest));
            if (by == null || by.equals("thrower")) {
                cells.add(thrower);
            }
            if (by == null || by.equals("catcher")) {
                cells.add(catcher == null ? "-" : catcher);
            }
            if (by == null || by.equals("class")) {
                cells.add(exception);
            }
            table.add(cells.toArray(new String[0]));
            out.json(new Json("throw").put("calls", r.calls).ms("total_ms", r.totalTicks).put("slowest", slowest)
                    .ms("slowest_ms", r.slowestTicks).put("thrower", thrower).put("catcher", catcher)
                    .put("exception", exception));
            legend.add(k.throwerId());
            legend.add(k.catcherId());
        }
        table.print(out);
        legend.print(out);
        out.more(rows.size() - shown, "rows", args.commandWith("limit", limit * 3L));
        out.status(file);
        return file.exitCode();
    }

    private static Comparator<Row> order(final String sort) {
        final Comparator<Row> byIds = Comparator.comparingInt((Row r) -> r.key.exceptionId())
                .thenComparingInt(r -> r.key.throwerId()).thenComparingInt(r -> r.key.catcherId());
        if (sort.equals("calls")) {
            return Comparator.comparingLong((Row r) -> -r.calls).thenComparingLong(r -> -r.totalTicks)
                    .thenComparing(byIds);
        }
        return Comparator.comparingLong((Row r) -> -r.totalTicks).thenComparingLong(r -> -r.calls)
                .thenComparing(byIds);
    }

    private static Out.Table table(final String by) {
        if (by == null) {
            return new Out.Table(">calls", ">total_ms", ">slowest", "thrower", "catcher", "exception");
        }
        return switch (by) {
            case "class" -> new Out.Table(">calls", ">total_ms", ">slowest", "exception");
            case "thrower" -> new Out.Table(">calls", ">total_ms", ">slowest", "thrower");
            default -> new Out.Table(">calls", ">total_ms", ">slowest", "catcher");
        };
    }
}
