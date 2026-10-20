package io.github.yagipass.verbatime.format;

import java.util.Arrays;
import java.util.List;

public final class SpecCases {

  public static final class SpecCase {

    private final String name;

    private final byte[] bytes;

    private final Verdict expected;

    private SpecCase(String name, byte[] bytes, Verdict expected) {
      this.name = name;
      this.bytes = bytes;
      this.expected = expected;
    }

    @Override
    public String toString() {
      return name;
    }

    public byte[] bytes() {
      return bytes.clone();
    }

    public Verdict expected() {
      return expected;
    }
  }

  private static final long EPOCH_MS = 1_700_000_000_000L;

  private SpecCases() {}

  public static List<SpecCase> all() {
    return List.of(
        aRecordingThatEndsWithTheEndRecordIsComplete(),
        aRecordingCutInsideAChunkIsTruncatedAndKeepsTheEventsBeforeTheCut(),
        aWholeChunkThatEndsMidEventIsCorruptEvenWithoutTheEndRecord(),
        theFirstCorruptionInTheFileIsReportedEvenWhenACorruptRecordFollows(),
        anExitWithNoOpenFrameIsCorruptAtThatExit(),
        aStringLongerThanAnyJavaArrayIsCorruptNotCut(),
        dataAfterTheEndRecordIsCorruptFromItsFirstByte(),
        anImplausibleChunkLengthIsReportedAtTheStartOfItsRecord(),
        aMalformedSignatureIsCorruptEvenWhenTheClassDeclaresMoreThanTheBytesLeft());
  }

  private static SpecCase aRecordingThatEndsWithTheEndRecordIsComplete() {
    byte[] bytes = trace().chunk(1, 0, runCallingWork(), true).end().bytes();
    return new SpecCase(
        "a recording that ends with the END record is complete",
        bytes,
        Verdict.readable(false, List.of(new Verdict.Session(1, 0, 2, true, 0, 20))));
  }

  private static SpecCase aRecordingCutInsideAChunkIsTruncatedAndKeepsTheEventsBeforeTheCut() {
    byte[] whole = trace().chunk(1, 0, runCallingWork(), true).bytes();
    return new SpecCase(
        "a recording cut inside a chunk is truncated and keeps the events before the cut",
        Arrays.copyOf(whole, whole.length - 1),
        Verdict.readable(true, List.of(new Verdict.Session(1, 0, 2, false, 0, 15))));
  }

  private static SpecCase aWholeChunkThatEndsMidEventIsCorruptEvenWithoutTheEndRecord() {
    byte[] payload = enterWithoutItsMethodId();
    TraceBuilder w = trace().chunk(1, 0, payload, false);
    int cutEvent = w.bytes().length - 1;
    return new SpecCase(
        "a whole chunk that ends mid-event is corrupt even without the END record",
        w.thread(2, "later").bytes(),
        Verdict.corrupt(cutEvent));
  }

  private static SpecCase theFirstCorruptionInTheFileIsReportedEvenWhenACorruptRecordFollows() {
    byte[] payload = enterWithoutItsMethodId();
    TraceBuilder w = trace().chunk(1, 0, payload, false);
    int cutEvent = w.bytes().length - 1;
    return new SpecCase(
        "the first corruption in the file is reported even when a corrupt record follows",
        w.rawBytes(0x7F).end().bytes(),
        Verdict.corrupt(cutEvent));
  }

  private static SpecCase anExitWithNoOpenFrameIsCorruptAtThatExit() {
    byte[] closed = new TraceBuilder.Payload(0).enter(0, 0).exit(5).bytes();
    byte[] payload = Arrays.copyOf(closed, closed.length + 1);
    payload[closed.length] = (byte) ((1 << 2) | EventEncoder.EXIT_BIT);
    TraceBuilder w = trace().chunk(1, 0, payload, true);
    int extraExit = w.bytes().length - 1;
    return new SpecCase(
        "an exit with no open frame is corrupt at that exit",
        w.end().bytes(),
        Verdict.corrupt(extraExit));
  }

  private static SpecCase aStringLongerThanAnyJavaArrayIsCorruptNotCut() {
    TraceBuilder w = trace();
    int record = w.bytes().length;
    return new SpecCase(
        "a string longer than any Java array is corrupt, not a recording cut short",
        varint(w.rawBytes(Vbtm.RECORD_THREAD, 7), 1L << 31).bytes(),
        Verdict.corrupt(record));
  }

  private static SpecCase dataAfterTheEndRecordIsCorruptFromItsFirstByte() {
    TraceBuilder w = trace().chunk(1, 0, runCallingWork(), true).end();
    int extra = w.bytes().length;
    return new SpecCase(
        "data after the END record is corrupt from its first byte",
        w.thread(2, "late").bytes(),
        Verdict.corrupt(extra));
  }

  private static SpecCase anImplausibleChunkLengthIsReportedAtTheStartOfItsRecord() {
    TraceBuilder w = trace();
    int record = w.bytes().length;
    return new SpecCase(
        "an implausible chunk payload length is reported at the start of its record",
        varint(w.rawBytes(Vbtm.RECORD_CHUNK, 1, 0), Vbtm.MAX_CHUNK_PAYLOAD_BYTES + 1).bytes(),
        Verdict.corrupt(record));
  }

  private static SpecCase
      aMalformedSignatureIsCorruptEvenWhenTheClassDeclaresMoreThanTheBytesLeft() {
    TraceBuilder w = trace();
    int record = w.bytes().length;
    varint(w.rawBytes(Vbtm.RECORD_CLASS, 2), 1000).rawBytes(1, 'B');
    return new SpecCase(
        "a malformed signature is corrupt even when the class declares more signatures than bytes"
            + " are left",
        varint(w, -1).bytes(),
        Verdict.corrupt(record));
  }

  private static TraceBuilder trace() {
    return new TraceBuilder(EPOCH_MS, 0).thread(1, "main").clazz(0, "a.Main", "run()V", "work()V");
  }

  private static byte[] runCallingWork() {
    return new TraceBuilder.Payload(0).enter(0, 0).enter(5, 1).exit(15).exit(20).bytes();
  }

  private static byte[] enterWithoutItsMethodId() {
    byte[] whole = new TraceBuilder.Payload(0).enter(0, 0).enter(1, 1).bytes();
    return Arrays.copyOf(whole, whole.length - 1);
  }

  private static TraceBuilder varint(TraceBuilder w, long v) {
    byte[] b = new byte[Varint.MAX_BYTES];
    int n = Varint.put(b, 0, v);
    int[] raw = new int[n];
    for (int i = 0; i < n; i++) {
      raw[i] = b[i] & 0xFF;
    }
    return w.rawBytes(raw);
  }
}
