package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.junit.FuzzTest;
import io.github.yagipass.verbatime.format.ReferenceDecoder;
import io.github.yagipass.verbatime.format.SpecCases;
import io.github.yagipass.verbatime.format.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TraceFileFuzzTest {

  private static final class Calls implements SessionWalker.Visitor {

    int rootMethodId = -1;

    long count;

    @Override
    public void enter(long ordinal, int depth, int methodId, long startTicks) {
      if (rootMethodId < 0) {
        rootMethodId = methodId;
      }
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
      count++;
    }
  }

  static Path write(byte[] bytes) throws IOException {
    return Files.write(Files.createTempFile("vbtm-fuzz", ".vbtm"), bytes);
  }

  @ParameterizedTest
  @MethodSource("io.github.yagipass.verbatime.format.SpecCases#all")
  void readsEachSpecCaseAsDocumented(SpecCases.SpecCase c) throws IOException {
    Path file = write(c.bytes());
    try {
      assertEquals(c.expected(), verdict(file));
    } finally {
      Files.delete(file);
    }
  }

  @FuzzTest
  void readsAnyBytesLikeTheReferenceAndSaysSoInItsExitCode(byte[] bytes) throws IOException {
    Verdict expected = ReferenceDecoder.verdict(bytes, ReferenceDecoder.SessionStart.AS_RECORDED);
    Path file = write(bytes);
    try {
      assertEquals(expected, verdict(file));
      Cli.Result r = Cli.vbtm("sessions", file, "--json");
      assertEquals(exitCode(expected), r.code(), r.err());
      for (String line : r.lines()) {
        if (!line.isEmpty()) {
          StrictJson.requireObject(line);
        }
      }
    } finally {
      Files.delete(file);
    }
  }

  private static Verdict verdict(Path file) {
    TraceFile f;
    try {
      f = TraceFile.open(file);
    } catch (CliException e) {
      if (e.exitCode() == CliException.UNREADABLE) {
        return Verdict.NOT_A_RECORDING;
      }
      throw e;
    }
    SessionWalker walker = new SessionWalker(f);
    List<Verdict.Session> sessions = new ArrayList<>();
    for (TraceFile.Session s : f.sessions) {
      Calls calls = new Calls();
      walker.walk(s, calls);
      sessions.add(
          new Verdict.Session(
              s.tid, calls.rootMethodId, calls.count, s.ended, walker.startTicks, walker.endTicks));
    }
    TraceFile.Corruption c = f.corruption;
    if (c != null) {
      return Verdict.corrupt(c.offset());
    }
    return Verdict.readable(f.status == TraceFile.Status.TRUNCATED, sessions);
  }

  private static int exitCode(Verdict v) {
    return switch (v.status()) {
      case NOT_A_RECORDING -> CliException.UNREADABLE;
      case CORRUPT -> CliException.CORRUPT;
      case COMPLETE, TRUNCATED -> 0;
    };
  }
}
