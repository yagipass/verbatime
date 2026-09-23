package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MainTest {

    @TempDir
    Path dir;

    @Test
    void usageErrorsExitWithOne() throws IOException {
        final Path t = TestTraces.trace(dir);
        assertEquals(1, Cli.vbtm("frobnicate").code());
        assertTrue(Cli.vbtm("frobnicate").err().contains("sessions, hot, throws, tree, find and callers"));
        assertEquals(1, Cli.vbtm("tree", t, "1", "--bogus").code());
        assertEquals(1, Cli.vbtm("hot", t, "--by", "speed").code());
        assertEquals(1, Cli.vbtm("throws", t, "--by", "colour").code());
    }

    @Test
    void helpIsPrintedForTheToolAndForEachCommand() {
        final Cli.Result help = Cli.vbtm("--help");
        assertEquals(0, help.code());
        assertTrue(help.out().contains("A typical investigation"), help.out());
        assertTrue(help.out().contains("3. vbtm throws rec.vbtm 7"), help.out());
        assertTrue(Cli.vbtm("tree", "--help").out().startsWith("vbtm tree <file> SESSION"));
        assertTrue(Cli.vbtm("throws", "--help").out().startsWith("vbtm throws <file> [SESSION]"));
        assertTrue(Cli.vbtm("find", "--help").out().contains("--thrown"));
    }
}
