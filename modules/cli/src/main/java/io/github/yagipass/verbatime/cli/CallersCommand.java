package io.github.yagipass.verbatime.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

final class CallersCommand {

    static final String HELP = """
            vbtm callers <file> PATTERN [--session SESSION] [--depth N] [--limit N] [--all] [--json]

            Shows where a method is called from: its callers, their callers and so on, each with the number of
            calls and the time of the method through that path. Use it when a method is hot or called too often
            and you need to know which code path causes it.

              --session S  one session instead of all of them
              --depth N    caller levels to follow up the stack, 6 by default
              --limit N    lines to print, 30 by default
              --all        accept a PATTERN that matches several methods

            Line: calls total_ms slowest caller. The first line is the method itself. Each indented line is a
            caller of the line above it, and its numbers count only the calls of the method that went through
            that path. slowest is the id of the slowest such call, for tree --at. A caller marked root is the top
            of a session. A recursive method counts only its outermost call.
            """;

    private CallersCommand() {
    }

    private static final class CallerPaths implements SessionWalker.Visitor {

        final PathTrie trie = new PathTrie();

        private final MethodPattern pattern;

        private final int levels;

        private final CallStack stack = new CallStack();

        private int session;

        private int openMatches;

        CallerPaths(final MethodPattern pattern, final int levels) {
            this.pattern = pattern;
            this.levels = levels;
        }

        void walk(final SessionWalker walker, final TraceFile.Session s) {
            session = s.number;
            walker.walk(s, this);
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
            if (!pattern.matches(methodId) || --openMatches > 0) {
                return;
            }
            trie.record(0, session, ordinal, durTicks, 0, false);
            int node = 0;
            for (int d = depth - 1; d >= 0 && depth - d <= levels; d--) {
                node = trie.child(node, stack.methodId(d), depth - d);
                if (node < 0) {
                    return;
                }
                if (d == 0) {
                    trie.rootCalls[node]++;
                }
                trie.record(node, session, ordinal, durTicks, 0, false);
            }
        }
    }

    static int run(final List<String> argv, final PrintStream stdout) {
        final Args args = Args.parse("callers", argv, Set.of("session", "depth", "limit"), Set.of("json", "all"));
        args.rejectPositionalsBeyond(2);
        final TraceFile file = TraceFile.open(args.positional(0, "the .vbtm file"));
        final Out out = new Out(stdout, args.has("json"));
        final Names names = new Names(file);
        final MethodPattern pattern = MethodPattern.resolve(file, names, args.positional(1, "the method PATTERN"),
                args.has("all"));
        final Scope scope = Scope.of(file, args.value("session"));
        final int levels = args.positiveInt("depth", 6);
        final int limit = args.positiveInt("limit", 30);

        final CallerPaths paths = new CallerPaths(pattern, levels);
        final SessionWalker walker = new SessionWalker(file);
        for (final TraceFile.Session s : scope.sessions) {
            paths.walk(walker, s);
        }
        final PathTrie trie = paths.trie;

        out.text("pattern: " + pattern.text + " -> " + pattern.fullNames(names) + "  scope: " + scope.label);
        out.text("each <- line is a caller of the line above it, counting only the calls that went through it. "
                + "units: ms");
        out.json(new Json("callers").put("pattern", pattern.text).put("scope", scope.label).put("calls", trie.calls[0])
                .ms("total_ms", trie.totalTicks[0]).put("depth", levels));
        if (trie.dropped > 0) {
            out.text("# " + Formats.grouped(trie.dropped) + " paths beyond the " + Formats.grouped(PathTrie.MAX_NODES)
                    + "-path limit are left out. Narrow with --session or --depth");
        }
        out.text("");
        if (trie.calls[0] == 0) {
            out.text("no calls in " + scope.label);
            out.status(file);
            return file.exitCode();
        }
        final int[][] children = trie.childrenByTotal();
        final Out.Table table = new Out.Table(">calls", ">total_ms", ">slowest", "method");
        final Legend legend = new Legend(names);
        long shown = 0;
        long hidden = 0;
        final NodeStack stack = new NodeStack();
        stack.push(0);
        while (!stack.isEmpty()) {
            final int node = stack.node();
            if (stack.state() < 0) {
                stack.setState(0);
                if (shown >= limit) {
                    hidden++;
                    continue;
                }
                shown++;
                final int level = stack.size() - 1;
                final String method = node == 0 ? pattern.shortNames(names) : names.displayName(trie.methodId[node]);
                final boolean root = trie.rootCalls[node] > 0;
                final String indent = "  ".repeat(Math.max(level - 1, 0));
                final String slowest = trie.slowest(node).toString();
                table.add(Formats.grouped(trie.calls[node]), Formats.ms(trie.totalTicks[node]), slowest,
                        indent + (node == 0 ? method : "<- " + method + (root ? ", root" : "")));
                out.json(new Json("caller").put("level", level).put("method", method).put("calls", trie.calls[node])
                        .ms("total_ms", trie.totalTicks[node]).put("slowest", slowest).put("root", root));
                legend.add(trie.methodId[node]);
                continue;
            }
            final int[] kids = children[node];
            if (stack.state() < kids.length) {
                final int child = kids[stack.state()];
                stack.setState(stack.state() + 1);
                stack.push(child);
            } else {
                stack.pop();
            }
        }
        table.print(out);
        legend.print(out);
        out.more(hidden, "lines", args.commandWith("limit", limit * 3L));
        out.status(file);
        return file.exitCode();
    }
}
