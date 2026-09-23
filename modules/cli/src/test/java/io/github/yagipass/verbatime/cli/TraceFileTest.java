package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TraceFileTest {

    @TempDir
    Path dir;

    @Test
    void aTruncatedFileIsReadUpToTheCut() throws IOException {
        final byte[] full = TestTraces.builder().end().bytes();
        final Path t = TestTraces.write(dir, "cut.vbtm", Arrays.copyOf(full, full.length - 4));
        final Cli.Result r = Cli.vbtm("sessions", t);
        assertEquals(0, r.code(), "a recording still being written is not an error: " + r.err());
        assertTrue(r.out().contains("status: truncated"), r.out());
        assertTrue(r.line("# status: truncated").contains("marked ~"), r.out());
    }

    @Test
    void aCorruptFilePrintsWhatWasReadAndExitsWithThree() throws IOException {
        final Path t = TestTraces.write(dir, "bad.vbtm", TestTraces.builder().rawBytes(0x7F).bytes());
        final Cli.Result r = Cli.vbtm("sessions", t);
        assertEquals(3, r.code());
        assertTrue(r.out().contains("corrupt at offset"), r.out());
        assertTrue(r.out().contains("unknown record type 127"), r.out());
        assertTrue(r.line("length:").contains("sessions: 3"), "the sessions before the bad record are kept: " + r.out());
    }

    @Test
    void anUnreadableFileExitsWithTwo() throws IOException {
        assertEquals(2, Cli.vbtm("sessions", dir.resolve("missing.vbtm")).code());
        final Cli.Result r = Cli.vbtm("hot", TestTraces.write(dir, "x.txt", "hello".getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, r.code());
        assertTrue(r.err().contains("is not a .vbtm recording"), r.err());
    }
}
