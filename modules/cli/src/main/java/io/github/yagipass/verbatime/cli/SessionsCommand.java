package io.github.yagipass.verbatime.cli;

import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SessionsCommand {

    static final String HELP = """
            vbtm sessions <file> [--root PATTERN] [--thread TEXT] [--sort start|dur|calls] [--limit N] [--json]

            Summarizes the recording and lists its sessions. A session is one call tree under a root method on one
            thread, such as one request. This is where an investigation starts.

              --root PATTERN   only sessions whose root method matches PATTERN, as described in vbtm --help
              --thread TEXT    only sessions on threads whose name contains TEXT
              --sort KEY       start, the default, dur or calls. dur and calls put the largest first
              --limit N        rows to print, 20 by default

            Columns: id is what hot, tree, find and callers take. start is ms from the start of the recording.
            depth is the deepest nesting. throws counts exceptions thrown, and an exception that propagates through
            several calls counts once. gc_ms is the stop-the-world GC time inside the session. A trailing ~ marks
            a session still open when the recording ended.
            """;

    private static final DateTimeFormatter RECORDED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private SessionsCommand() {
    }

    static int run(final List<String> argv, final PrintStream stdout) {
        final Args args = Args.parse("sessions", argv, Set.of("root", "thread", "sort", "limit"), Set.of("json"));
        args.rejectPositionalsBeyond(1);
        final TraceFile file = TraceFile.open(args.positional(0, "the .vbtm file"));
        final Out out = new Out(stdout, args.has("json"));
        final Names names = new Names(file);
        final String sort = args.choice("sort", "start", "start", "dur", "calls");
        final int limit = args.positiveInt("limit", 20);
        final MethodPattern root = args.has("root") ? MethodPattern.resolve(file, names, args.value("root"), true)
                : null;
        final String thread = args.value("thread");

        final SessionWalker walker = new SessionWalker(file);
        final GcPauses gc = new GcPauses(file);
        final List<SessionSummary> all = new ArrayList<>(file.sessions.size());
        long totalCalls = 0;
        long lengthTicks = 0;
        final Set<Long> tids = new HashSet<>();
        for (final TraceFile.Session s : file.sessions) {
            final SessionSummary summary = SessionSummary.of(walker, s);
            all.add(summary);
            totalCalls += summary.calls;
            lengthTicks = Math.max(lengthTicks, summary.endTicks());
            tids.add(s.tid);
        }

        final String recorded = RECORDED.format(file.wallClock(0));
        out.text("file: " + file.fileName() + "  status: " + file.statusText() + "  recorded: " + recorded);
        out.text("length: " + Formats.ms(lengthTicks) + " ms  threads: " + Formats.grouped(tids.size())
                + "  sessions: " + Formats.grouped(file.sessions.size()) + "  calls: " + Formats.grouped(totalCalls)
                + "  methods: " + Formats.grouped(file.methodCount) + "  gc: " + Formats.plural(gc.count(), "pause")
                + ", " + Formats.ms(gc.totalTicks()) + " ms");
        out.text("units: ms, 0.0001 ms = 1 tick of 100 ns");
        out.json(new Json("file").put("file", file.fileName())
                .put("status", file.status.name().toLowerCase(Locale.ROOT)).put("detail", file.statusText())
                .put("recorded", recorded).ms("length_ms", lengthTicks).put("threads", tids.size())
                .put("sessions", file.sessions.size()).put("calls", totalCalls).put("methods", file.methodCount)
                .put("gc_pauses", gc.count()).ms("gc_ms", gc.totalTicks()));

        final List<SessionSummary> rows = new ArrayList<>();
        for (final SessionSummary summary : all) {
            if (root != null && !root.matches(summary.rootMethodId)) {
                continue;
            }
            if (thread != null && !file.threadName(summary.session.tid).contains(thread)) {
                continue;
            }
            rows.add(summary);
        }
        rows.sort(order(sort));
        final boolean filtered = root != null || thread != null;
        out.text((filtered
                ? Formats.grouped(rows.size()) + " of " + Formats.plural(all.size(), "session") + " match"
                : Formats.plural(rows.size(), "session")) + ", sorted by " + sort + ", showing "
                + Formats.grouped(Math.min(limit, rows.size())));
        out.text("");
        final Out.Table table = new Out.Table(">id", ">start", ">dur", ">calls", ">depth", ">throws", ">gc_ms",
                "thread", "root");
        final int shown = Math.min(limit, rows.size());
        final Legend legend = new Legend(names);
        for (int i = 0; i < shown; i++) {
            final SessionSummary summary = rows.get(i);
            final long gcTicks = gc.overlapTicks(summary.startTicks, summary.endTicks());
            final String threadName = file.threadName(summary.session.tid);
            final String rootName = summary.rootMethodId < 0 ? "<no calls>" : names.displayName(summary.rootMethodId);
            table.add(String.valueOf(summary.session.number), Formats.ms(summary.startTicks),
                    Formats.ms(summary.durTicks), Formats.grouped(summary.calls), String.valueOf(summary.maxDepth),
                    Formats.grouped(summary.throwCount), Formats.ms(gcTicks), threadName,
                    rootName + (summary.rootCalls > 1 ? " +" + (summary.rootCalls - 1) + " more roots" : "")
                            + (summary.unclosed ? " ~" : ""));
            out.json(new Json("session").put("id", summary.session.number).ms("start_ms", summary.startTicks)
                    .ms("dur_ms", summary.durTicks).put("calls", summary.calls).put("depth", summary.maxDepth)
                    .put("throws", summary.throwCount).ms("gc_ms", gcTicks).put("thread", threadName)
                    .put("root", summary.rootMethodId < 0 ? null : names.displayName(summary.rootMethodId))
                    .put("root_full", summary.rootMethodId < 0 ? null : names.fullName(summary.rootMethodId))
                    .put("roots", summary.rootCalls).put("unclosed", summary.unclosed));
            legend.add(summary.rootMethodId);
        }
        table.print(out);
        legend.print(out);
        out.more(rows.size() - shown, "sessions", args.commandWith("limit", limit * 3L));
        out.status(file);
        return file.exitCode();
    }

    private static Comparator<SessionSummary> order(final String sort) {
        final Comparator<SessionSummary> key = switch (sort) {
            case "dur" -> Comparator.comparingLong(summary -> -summary.durTicks);
            case "calls" -> Comparator.comparingLong(summary -> -summary.calls);
            default -> Comparator.comparingLong(summary -> summary.startTicks);
        };
        return key.thenComparingInt(summary -> summary.session.number);
    }
}
