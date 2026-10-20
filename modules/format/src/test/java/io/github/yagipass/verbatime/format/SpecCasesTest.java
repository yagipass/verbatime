package io.github.yagipass.verbatime.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class SpecCasesTest {

  @ParameterizedTest
  @MethodSource("io.github.yagipass.verbatime.format.SpecCases#all")
  void theReferenceDecoderReachesTheVerdictEachCaseDocuments(SpecCases.SpecCase c) {
    assertEquals(c.expected(), ReferenceDecoder.verdict(c.bytes()));
  }
}
