package io.github.yagipass.verbatime.agent.test;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.Vbtm;
import java.nio.file.Path;
import java.util.List;

public final class E2EVerify {

  private static final long GC_SLACK_TICKS = 3 * Vbtm.TICKS_PER_MS;

  private E2EVerify() {}

  public static void main(String[] args) throws Exception {
    DecodedTrace d = DecodedTrace.decode(Path.of(args[0]));
    @Var int failures = 0;
    failures += check(d.cleanEnd, "end-of-recording footer present, so the trace is complete");
    failures +=
        check(
            d.sessions.size() == 3,
            "3 sessions for root, rootThrows, and rootAllocates, got " + d.sessions.size());
    failures +=
        check(
            d.methodNames.containsValue(
                "io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;"),
            "CLASS records name the root");
    DecodedTrace.DecodedSession s1 = d.sessions.get(1);
    if (s1 != null) {
      failures += check(s1.ended, "session 1 ended");
      failures += check(DecodedTrace.toPreorder(s1).size() > 5, "session 1 recorded the callees");
    } else {
      failures++;
    }
    DecodedTrace.DecodedSession s2 = d.sessions.get(2);
    if (s2 != null) {
      failures += check(s2.ended, "session 2 ended");
      DecodedTrace.Node root2 = DecodedTrace.toPreorder(s2).get(0);
      failures += check(root2.thrown(), "throw flag on rootThrows");
      failures +=
          check(
              "java.lang.IllegalStateException".equals(d.exceptionName(root2.exceptionId())),
              "rootThrows records what was thrown, got " + d.exceptionName(root2.exceptionId()));
    } else {
      failures++;
    }
    failures +=
        check(
            d.danglingExceptionRefs == 0,
            "every EXCEPTION record precedes the chunk that references it, "
                + d.danglingExceptionRefs
                + " dangling");
    DecodedTrace.DecodedSession s3 = d.sessions.get(3);
    if (s3 != null && !s3.events.isEmpty()) {
      failures += check(s3.ended, "session 3 ended");
      long[] collect = span(d, s3, "io.github.yagipass.verbatime.fixtures.Fixture.collect()V");
      if (collect != null) {
        long from = collect[0] - GC_SLACK_TICKS;
        long to = collect[1] + GC_SLACK_TICKS;
        failures +=
            check(
                d.gcPauses.stream()
                    .anyMatch(
                        p ->
                            p.startTicks() >= from
                                && p.startTicks() <= to
                                && p.action() == Vbtm.GC_ACTION_MAJOR
                                && p.durTicks() > 0),
                "the major pause of System.gc() starts inside collect() at ticks "
                    + collect[0]
                    + ".."
                    + collect[1]
                    + ", so the pause is charged to the call it stopped rather than to one that ran earlier: "
                    + d.gcPauses);
      } else {
        failures += check(false, "collect() recorded in session 3");
      }
      failures +=
          check(
              d.gcPauses.stream().allMatch(p -> !p.collector().isEmpty()),
              "every GC record names its collector");
    } else {
      failures++;
    }
    if (failures > 0) {
      System.err.println("[e2e] " + failures + (failures == 1 ? " check" : " checks") + " FAILED");
      System.exit(1);
    }
    System.err.println("[e2e] verify OK");
  }

  private static long[] span(DecodedTrace d, DecodedTrace.DecodedSession s, String method) {
    List<DecodedTrace.Event> ev = s.events;
    for (int i = 0; i < ev.size(); i++) {
      DecodedTrace.Event enter = ev.get(i);
      if (enter.tag() != DecodedTrace.TAG_ENTER
          || !method.equals(d.methodNames.get(enter.methodId()))) {
        continue;
      }
      @Var int open = 0;
      for (int j = i + 1; j < ev.size(); j++) {
        if (ev.get(j).tag() == DecodedTrace.TAG_ENTER) {
          open++;
        } else if (open == 0) {
          return new long[] {enter.ticks(), ev.get(j).ticks()};
        } else {
          open--;
        }
      }
    }
    return null;
  }

  private static int check(boolean cond, String msg) {
    if (!cond) {
      System.err.println("[e2e]   FAIL " + msg);
      return 1;
    }
    return 0;
  }
}
