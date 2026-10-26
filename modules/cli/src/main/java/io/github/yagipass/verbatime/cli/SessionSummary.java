package io.github.yagipass.verbatime.cli;

final class SessionSummary {

  private static final class SummaryVisitor implements SessionWalker.Visitor {

    private final SessionSummary summary;

    private final Throws thrown = new Throws(null);

    private SummaryVisitor(SessionSummary summary) {
      this.summary = summary;
    }

    @Override
    public void enter(long ordinal, int depth, int methodId, long startTicks) {
      if (depth == 0) {
        summary.rootCalls++;
        if (summary.rootMethodId < 0) {
          summary.rootMethodId = methodId;
        }
      }
      if (depth > summary.maxDepth) {
        summary.maxDepth = depth;
      }
      thrown.enter(depth, methodId);
    }

    @Override
    public void exit(
        long ordinal,
        int depth,
        int methodId,
        long startTicks,
        long durTicks,
        long selfTicks,
        int exceptionId,
        boolean unclosed) {
      summary.calls++;
      thrown.exit(ordinal, depth, methodId, durTicks, exceptionId);
      summary.unclosed |= unclosed;
    }
  }

  final TraceFile.Session session;

  int rootMethodId = -1;

  int rootCalls;

  long startTicks;

  long durTicks;

  long calls;

  long throwCount;

  int maxDepth;

  boolean unclosed;

  private SessionSummary(TraceFile.Session session) {
    this.session = session;
  }

  static SessionSummary of(SessionWalker walker, TraceFile.Session s) {
    SessionSummary summary = new SessionSummary(s);
    SummaryVisitor visitor = new SummaryVisitor(summary);
    walker.walk(s, visitor);
    summary.throwCount = visitor.thrown.count;
    summary.startTicks = walker.startTicks;
    summary.durTicks = walker.endTicks - walker.startTicks;
    summary.unclosed |= !s.ended;
    return summary;
  }

  long endTicks() {
    return startTicks + durTicks;
  }
}
