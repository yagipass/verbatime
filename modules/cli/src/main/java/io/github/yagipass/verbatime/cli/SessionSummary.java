package io.github.yagipass.verbatime.cli;

final class SessionSummary implements SessionWalker.Visitor {

    final TraceFile.Session session;

    int rootMethodId = -1;

    int rootCalls;

    long startTicks;

    long durTicks;

    long calls;

    long throwCount;

    int maxDepth;

    boolean unclosed;

    private final Throws thrown = new Throws(null);

    private SessionSummary(final TraceFile.Session session) {
        this.session = session;
    }

    static SessionSummary of(final SessionWalker walker, final TraceFile.Session s) {
        final SessionSummary summary = new SessionSummary(s);
        walker.walk(s, summary);
        summary.throwCount = summary.thrown.count;
        summary.startTicks = walker.startTicks;
        summary.durTicks = walker.endTicks - walker.startTicks;
        summary.unclosed |= !s.ended;
        return summary;
    }

    long endTicks() {
        return startTicks + durTicks;
    }

    @Override
    public void enter(final long ordinal, final int depth, final int methodId, final long startTicks) {
        if (depth == 0) {
            rootCalls++;
            if (rootMethodId < 0) {
                rootMethodId = methodId;
            }
        }
        if (depth > maxDepth) {
            maxDepth = depth;
        }
        thrown.enter(depth, methodId);
    }

    @Override
    public void exit(final long ordinal, final int depth, final int methodId, final long startTicks,
            final long durTicks, final long selfTicks, final int exceptionId, final boolean unclosed) {
        calls++;
        thrown.exit(ordinal, depth, methodId, durTicks, exceptionId);
        this.unclosed |= unclosed;
    }
}
