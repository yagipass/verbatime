package io.github.yagipass.verbatime.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Seeds {

  private Seeds() {}

  public static List<byte[]> recordings() {
    List<byte[]> seeds = new ArrayList<>();
    seeds.add(everyRecordOnTwoThreads().end().bytes());
    byte[] open = everyRecordOnTwoThreads().bytes();
    seeds.add(Arrays.copyOf(open, open.length - 1));
    for (SpecCases.SpecCase c : SpecCases.all()) {
      seeds.add(c.bytes());
    }
    return seeds;
  }

  private static TraceBuilder everyRecordOnTwoThreads() {
    byte[] mainStart = new TraceBuilder.Payload(0).enter(0, 0).enter(5, 1).exitThrow(9, 1).bytes();
    byte[] worker = new TraceBuilder.Payload(3).enter(3, 0).exit(30).bytes();
    byte[] mainEnd = new TraceBuilder.Payload(12).enter(12, 1).exit(14).exit(20).bytes();
    return new TraceBuilder(1_700_000_000_000L, 9 * 3600)
        .thread(1, "main")
        .thread(2, "worker")
        .clazz(0, "a.Main", "run()V", "work()V")
        .exception(1, "a.Failure")
        .chunk(1, 0, mainStart, false)
        .gc(10, 2, Vbtm.GC_ACTION_MINOR, "G1 Young Generation", "G1 Evacuation Pause")
        .chunk(2, 3, worker, true)
        .chunk(1, 12, mainEnd, true);
  }
}
