package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yagipass.verbatime.format.TraceBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TraceFileTest {

  @TempDir Path dir;

  @Test
  void aTruncatedFileIsReadUpToTheCut() throws IOException {
    byte[] full = TestTraces.builder().end().bytes();
    Path t = TestTraces.write(dir, "cut.vbtm", Arrays.copyOf(full, full.length - 4));
    Cli.Result r = Cli.vbtm("sessions", t);
    assertEquals(0, r.code(), "a recording still being written is not an error: " + r.err());
    assertTrue(r.out().contains("status: truncated"), r.out());
    assertTrue(r.line("# status: truncated").contains("marked ~"), r.out());
  }

  @Test
  void aCorruptFilePrintsWhatWasReadAndExitsWithThree() throws IOException {
    Path t = TestTraces.write(dir, "bad.vbtm", TestTraces.builder().rawBytes(0x7F).bytes());
    Cli.Result r = Cli.vbtm("sessions", t);
    assertEquals(3, r.code());
    assertTrue(r.out().contains("corrupt at offset"), r.out());
    assertTrue(r.out().contains("unknown record type 127"), r.out());
    assertTrue(
        r.line("length:").contains("sessions: 3"),
        "the sessions before the bad record are kept: " + r.out());
  }

  @Test
  void aWholeChunkThatEndsMidEventIsCorruptEvenWithoutTheEndRecord() throws IOException {
    TraceBuilder w = TestTraces.builder().chunk(3, 3000, enterWithoutItsMethodId(), false);
    int cutEvent = w.bytes().length - 1;
    Path t = TestTraces.write(dir, "mid-event.vbtm", w.thread(3, "later").bytes());
    Cli.Result r = Cli.vbtm("sessions", t);
    assertEquals(
        3, r.code(), "only the end of the file may cut a chunk, so this is not a cut: " + r.out());
    assertTrue(
        r.out().contains("corrupt at offset " + cutEvent + ": chunk payload ends mid-event"),
        r.out());
  }

  @Test
  void aChunkThatEndsMidEventIsReportedBeforeACorruptRecordAfterIt() throws IOException {
    TraceBuilder w = TestTraces.builder().chunk(3, 3000, enterWithoutItsMethodId(), false);
    int cutEvent = w.bytes().length - 1;
    Path t = TestTraces.write(dir, "mid-event-then-bad.vbtm", w.rawBytes(0x7F).end().bytes());
    Cli.Result r = Cli.vbtm("sessions", t);
    assertEquals(3, r.code());
    assertTrue(
        r.out().contains("corrupt at offset " + cutEvent + ": chunk payload ends mid-event"),
        "everything after the reported offset is called missing, so it must be the first damage: "
            + r.out());
  }

  @Test
  void anUnreadableFileExitsWithTwo() throws IOException {
    assertEquals(2, Cli.vbtm("sessions", dir.resolve("missing.vbtm")).code());
    Cli.Result r =
        Cli.vbtm("hot", TestTraces.write(dir, "x.txt", "hello".getBytes(StandardCharsets.UTF_8)));
    assertEquals(2, r.code());
    assertTrue(r.err().contains("is not a .vbtm recording"), r.err());
  }

  private static byte[] enterWithoutItsMethodId() {
    byte[] whole = new TraceBuilder.Payload(3000).enter(3000, 0).enter(3001, 1).bytes();
    return Arrays.copyOf(whole, whole.length - 1);
  }
}
