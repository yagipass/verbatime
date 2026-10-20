package io.github.yagipass.verbatime.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.code_intelligence.jazzer.junit.FuzzTest;
import io.github.yagipass.verbatime.format.TraceReader.Outcome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TraceReaderFuzzTest {

  private static final TraceReader.Visitor IGNORE = new TraceReader.Visitor() {};

  @ParameterizedTest
  @MethodSource("io.github.yagipass.verbatime.format.SpecCases#all")
  void readsEachSpecCaseLikeTheReference(SpecCases.SpecCase c) {
    assertReadsLikeTheReference(c.bytes());
  }

  @FuzzTest
  void readsAnyBytesLikeTheReference(byte[] bytes) {
    assertReadsLikeTheReference(bytes);
  }

  private static void assertReadsLikeTheReference(byte[] bytes) {
    ReferenceDecoder.Result ref;
    try {
      ref = ReferenceDecoder.decode(bytes);
    } catch (IllegalArgumentException notARecording) {
      CorruptTraceException e =
          assertThrows(CorruptTraceException.class, () -> TraceReader.read(bytes, IGNORE));
      assertTrue(e.offset() <= Vbtm.VERSION_OFFSET, e.getMessage());
      return;
    }
    if (ref.corruptOffset >= 0 && !ref.corruptInChunk) {
      CorruptTraceException e =
          assertThrows(
              CorruptTraceException.class,
              () -> TraceReader.read(bytes, IGNORE),
              "the reference finds a corrupt record at offset " + ref.corruptOffset);
      assertEquals(ref.corruptOffset, e.offset(), e.getMessage());
      return;
    }
    Outcome outcome;
    try {
      outcome = TraceReader.read(bytes, IGNORE);
    } catch (CorruptTraceException e) {
      assertTrue(
          ref.corruptInChunk && e.offset() > ref.corruptOffset,
          "the reader finds a corrupt record at offset "
              + e.offset()
              + " ("
              + e.getMessage()
              + "), the reference finds "
              + describe(ref));
      return;
    }
    if (ref.corruptOffset < 0) {
      assertEquals(
          ref.truncated ? Outcome.TRUNCATED : Outcome.CLEAN, outcome, "the reference finds it so");
    }
  }

  private static String describe(ReferenceDecoder.Result ref) {
    if (ref.corruptOffset >= 0) {
      return "a corrupt event at offset " + ref.corruptOffset;
    }
    return ref.truncated ? "a truncated recording" : "a complete recording";
  }
}
