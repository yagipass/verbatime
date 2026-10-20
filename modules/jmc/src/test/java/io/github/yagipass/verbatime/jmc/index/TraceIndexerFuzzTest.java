package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.ReferenceDecoder;
import io.github.yagipass.verbatime.format.Seeds;
import io.github.yagipass.verbatime.format.SpecCases;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.format.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TraceIndexerFuzzTest {

  private static final int NO_COMPACTION = 1 << 30;

  private static final List<byte[]> SEEDS = Seeds.recordings();

  @ParameterizedTest
  @MethodSource("io.github.yagipass.verbatime.format.SpecCases#all")
  void indexesEachSpecCaseAsDocumented(SpecCases.SpecCase c) throws IOException {
    assertEquals(c.expected(), indexWhole(c.bytes()));
  }

  @ParameterizedTest
  @MethodSource("io.github.yagipass.verbatime.format.Seeds#recordings")
  void aSeedGrowingByteByByteIsIndexedLikeEachOfItsPrefixes(byte[] bytes) throws IOException {
    int[] cuts = new int[bytes.length];
    for (int i = 0; i < cuts.length; i++) {
      cuts[i] = i + 1;
    }
    assertIndexedLikeEachPrefix(bytes, 1, cuts);
  }

  @MethodSource("io.github.yagipass.verbatime.format.Seeds#recordings")
  @FuzzTest
  void indexesAnyBytesLikeTheReference(byte[] bytes) throws IOException {
    assertEquals(ReferenceDecoder.verdict(bytes), indexWhole(bytes));
  }

  @FuzzTest
  void aGrowingRecordingIsIndexedLikeEachOfItsPrefixes(FuzzedDataProvider data) throws IOException {
    int overviewBudget = data.consumeInt(1, 64);
    int[] cuts = new int[data.consumeInt(0, 3) + 1];
    for (int i = 0; i < cuts.length - 1; i++) {
      cuts[i] = data.consumeInt(0, Integer.MAX_VALUE);
    }
    byte[] bytes = data.consumeBoolean() ? data.pickValue(SEEDS) : data.consumeRemainingAsBytes();
    for (int i = 0; i < cuts.length - 1; i++) {
      cuts[i] %= bytes.length + 1;
    }
    cuts[cuts.length - 1] = bytes.length;
    Arrays.sort(cuts);
    assertIndexedLikeEachPrefix(bytes, overviewBudget, cuts);
  }

  private static void assertIndexedLikeEachPrefix(byte[] bytes, int overviewBudget, int[] cuts)
      throws IOException {
    Path file = Files.createTempFile("vbtm-fuzz", ".vbtm");
    TraceIndexer ix = TraceIndexer.open(file, overviewBudget);
    try {
      @Var int written = 0;
      for (int cut : cuts) {
        Files.write(file, Arrays.copyOfRange(bytes, written, cut), StandardOpenOption.APPEND);
        written = cut;
        assertEquals(
            ReferenceDecoder.verdict(Arrays.copyOf(bytes, cut)),
            verdict(ix),
            "after the recording grew to " + cut + " bytes");
      }
    } finally {
      ix.close();
      Files.delete(file);
    }
  }

  private static Verdict indexWhole(byte[] bytes) throws IOException {
    Path file = Files.write(Files.createTempFile("vbtm-fuzz", ".vbtm"), bytes);
    TraceIndexer ix = TraceIndexer.open(file, NO_COMPACTION);
    try {
      return verdict(ix);
    } finally {
      ix.close();
      Files.delete(file);
    }
  }

  private static Verdict verdict(TraceIndexer ix) throws IOException {
    try {
      ix.advance(TraceIndexer.ProgressListener.NONE);
    } catch (TraceIndexer.NotTraceFormatException notARecording) {
      return Verdict.NOT_A_RECORDING;
    }
    TraceSnapshot d = ix.snapshot();
    try {
      if (d.corruption != null) {
        return Verdict.corrupt(d.corruption.offset());
      }
      List<Verdict.Session> sessions = new ArrayList<>();
      for (TraceSnapshot.Session s : d.sessions) {
        sessions.add(
            new Verdict.Session(
                s.tid,
                s.rootMethodId,
                s.callCount,
                s.ended,
                s.startNs / Vbtm.NANOS_PER_TICK,
                s.endNs / Vbtm.NANOS_PER_TICK));
      }
      return Verdict.readable(d.truncated, sessions);
    } finally {
      d.release();
    }
  }
}
