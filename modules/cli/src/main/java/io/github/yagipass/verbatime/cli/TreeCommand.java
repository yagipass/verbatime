package io.github.yagipass.verbatime.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TreeCommand {

    static final String HELP = """
            vbtm tree <file> SESSION [--at CALL] [--depth N] [--floor DUR] [--merge] [--limit N] [--json]
            vbtm tree <file> --at CALL [...]

            Prints the call tree of a session, one call per line in the order the calls started.

              --at CALL    print only the subtree of that call, such as 7.57, and the path from the root to it
              --depth N    print N levels below the root of the printed tree
              --floor DUR  hide calls shorter than DUR, such as 500us, 1ms or 2s. Without it, the floor is chosen so
                           that about --limit lines are printed, keeping the longest calls
              --merge      merge calls of the same method under the same path into one line, with calls, total and
                           self summed. Use it for loops and N+1 patterns, where one line per call is too long
              --limit N    lines to print, 200 by default

            Line: id start dur depth method [self S] [!eN] [~]
              id     the call id to pass to --at, find and callers. It stays the same between runs
              start  ms from the start of the session
              self   time in the method's own code, printed when the call made calls
              !eN    the call ended by throwing exception eN. The header lists each eN with its class and ×K, how many
                     times it was thrown in the printed scope, counting an exception that unwinds through several
                     calls once. A bare ! means the class is unknown
              ~      the call was still open when the recording ended
            A line starting with - folds the calls made by the call it names that are not printed: when the first
            started, how long they took in total, how many calls they made in turn, and which methods, most frequent
            first. It follows the printed calls of that call.
            """;

    static final int DEFAULT_LIMIT = 200;

    static final int GC_PAUSES_SHOWN = 5;

    final TraceFile file;

    final Names names;

    final TraceFile.Session session;

    final long at;

    final int maxDepth;

    final int limit;

    final Args args;

    final Out out;

    final SessionWalker walker;

    private TreeCommand(final TraceFile file, final Names names, final TraceFile.Session session, final long at,
            final int maxDepth, final int limit, final Args args, final Out out) {
        this.file = file;
        this.names = names;
        this.session = session;
        this.at = at;
        this.maxDepth = maxDepth;
        this.limit = limit;
        this.args = args;
        this.out = out;
        this.walker = new SessionWalker(file);
    }

    static int run(final List<String> argv, final PrintStream stdout) {
        final Args args = Args.parse("tree", argv, Set.of("at", "depth", "floor", "limit"), Set.of("json", "merge"));
        args.rejectPositionalsBeyond(2);
        final TraceFile file = TraceFile.open(args.positional(0, "the .vbtm file"));
        final Out out = new Out(stdout, args.has("json"));
        final Names names = new Names(file);
        final TraceFile.Session session;
        long at = -1;
        if (args.has("at")) {
            final CallId id = CallId.parse(args.value("at"));
            session = file.session(id.session());
            at = id.ordinal();
            final String sessionRef = args.positionalOrNull(1);
            if (sessionRef != null && file.session(sessionRef) != session) {
                throw CliException.usage("--at " + id + " is not in session " + sessionRef,
                        "the number before the dot of a call id is its session");
            }
        } else {
            session = file.session(args.positional(1, "the session id or --at CALL"));
        }
        final int maxDepth = args.nonNegativeInt("depth", Integer.MAX_VALUE);
        final int limit = args.positiveInt("limit", DEFAULT_LIMIT);
        final TreeCommand tree = new TreeCommand(file, names, session, at, maxDepth, limit, args, out);
        if (args.has("merge")) {
            new MergedTree(tree).print();
        } else {
            new CallTree(tree).print();
        }
        out.status(file);
        return file.exitCode();
    }

    void walk(final SubtreeVisitor visitor) {
        walker.walk(session, visitor);
        if (!visitor.found) {
            throw CliException.usage("no call " + callId(at) + " in session " + session.number,
                    "vbtm tree " + Args.shellQuote(file.path.toString()) + " " + session.number);
        }
        if (at < 0) {
            visitor.startTicks = walker.startTicks;
            visitor.durTicks = walker.endTicks - walker.startTicks;
        }
    }

    String callId(final long ordinal) {
        return new CallId(session.number, ordinal).toString();
    }

    boolean autoFloor() {
        return !args.has("floor");
    }

    long floorTicks() {
        return args.ticks("floor", 0);
    }

    String floorText(final long floorTicks, final String none) {
        if (floorTicks == 0) {
            return none;
        }
        return Formats.duration(floorTicks) + (autoFloor() ? ", chosen to fit --limit " + limit : "");
    }

    Json optionsJson(final long floorTicks, final boolean merge) {
        final Json json = new Json("options");
        if (merge) {
            json.put("merge", true);
        }
        return json.put("floor", Formats.duration(floorTicks)).put("floor_auto", autoFloor())
                .put("depth", maxDepth == Integer.MAX_VALUE ? -1 : maxDepth).put("limit", limit);
    }

    void header(final SubtreeVisitor scope, final long calls) {
        final String thread = file.threadName(session.tid);
        if (at < 0) {
            out.text("session " + session.number + "  thread: " + thread + "  start " + Formats.ms(scope.startTicks)
                    + " ms  dur " + Formats.ms(scope.durTicks) + " ms  calls " + Formats.grouped(calls)
                    + (session.ended ? "" : "  [unclosed]"));
            out.json(new Json("scope").put("session", session.number).put("thread", thread)
                    .ms("start_ms", scope.startTicks).ms("dur_ms", scope.durTicks).put("calls", calls));
            return;
        }
        final StringBuilder path = new StringBuilder();
        final StringBuilder jsonPath = new StringBuilder("[");
        for (int i = 0; i < scope.pathMethodIds.length; i++) {
            if (i > 0) {
                path.append(" > ");
                jsonPath.append(',');
            }
            final String step = callId(scope.pathOrdinals[i]) + " " + names.displayName(scope.pathMethodIds[i]);
            path.append(step);
            jsonPath.append(Json.quote(step));
        }
        jsonPath.append(']');
        final long startTicks = scope.startTicks - walker.startTicks;
        out.text("session " + session.number + "  thread: " + thread + "  at " + callId(at) + "  start "
                + Formats.ms(startTicks) + " ms  dur " + Formats.ms(scope.durTicks) + " ms  calls "
                + Formats.grouped(calls));
        out.text("path: " + path);
        out.json(new Json("scope").put("session", session.number).put("thread", thread).put("at", callId(at))
                .ms("start_ms", startTicks).ms("dur_ms", scope.durTicks).put("calls", calls)
                .raw("path", jsonPath.toString()));
    }

    void exceptionsLegend(final Map<Integer, Long> countByException) {
        final StringBuilder sb = new StringBuilder("exceptions:");
        for (final Map.Entry<Integer, Long> e : countByException.entrySet()) {
            final int id = e.getKey();
            if (id <= 0) {
                continue;
            }
            sb.append(" e").append(id).append(" = ").append(file.exceptionName(id)).append(" ×")
                    .append(Formats.grouped(e.getValue())).append(';');
            out.json(new Json("exception").put("id", "e" + id).put("class", file.exceptionName(id)).put("thrown",
                    e.getValue()));
        }
        if (sb.length() == "exceptions:".length()) {
            return;
        }
        sb.setLength(sb.length() - 1);
        out.text(sb.toString());
    }

    void gcLegend(final long startTicks, final long durTicks) {
        final List<TraceFile.GcPause> pauses = new GcPauses(file).overlapping(startTicks, startTicks + durTicks);
        if (pauses.isEmpty()) {
            return;
        }
        long total = 0;
        for (final TraceFile.GcPause g : pauses) {
            total += g.durTicks();
        }
        out.text("gc: " + Formats.plural(pauses.size(), "pause") + " overlap, " + Formats.ms(total)
                + " ms stop-the-world, included in the durations below");
        final List<TraceFile.GcPause> longest = new ArrayList<>(pauses);
        longest.sort((x, y) -> Long.compare(y.durTicks(), x.durTicks()));
        for (int i = 0; i < longest.size(); i++) {
            final TraceFile.GcPause g = longest.get(i);
            final long start = g.startTicks() - walker.startTicks;
            if (i < GC_PAUSES_SHOWN) {
                out.text("  at " + Formats.ms(start) + " ms for " + Formats.ms(g.durTicks()) + " ms, " + g.kind() + " "
                        + g.collector() + ": " + g.cause());
            }
            out.json(new Json("gc").ms("start_ms", start).ms("dur_ms", g.durTicks()).put("kind", g.kind())
                    .put("collector", g.collector()).put("cause", g.cause()));
        }
        if (longest.size() > GC_PAUSES_SHOWN) {
            out.text("  ... " + (longest.size() - GC_PAUSES_SHOWN) + " shorter pauses");
        }
    }
}
