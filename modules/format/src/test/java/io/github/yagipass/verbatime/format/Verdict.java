package io.github.yagipass.verbatime.format;

import java.util.List;

public record Verdict(Verdict.Status status, long corruptOffset, List<Verdict.Session> sessions) {

  public enum Status {
    COMPLETE,
    TRUNCATED,
    CORRUPT,
    NOT_A_RECORDING
  }

  public record Session(
      long tid, int rootMethodId, long calls, boolean ended, long startTicks, long endTicks) {}

  public static final Verdict NOT_A_RECORDING = new Verdict(Status.NOT_A_RECORDING, -1, List.of());

  public static Verdict corrupt(long offset) {
    return new Verdict(Status.CORRUPT, offset, List.of());
  }

  public static Verdict readable(boolean truncated, List<Session> sessions) {
    return new Verdict(truncated ? Status.TRUNCATED : Status.COMPLETE, -1, List.copyOf(sessions));
  }
}
