package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MethodPatternTest {

    @TempDir
    Path dir;

    @Test
    void aPatternThatNamesSeveralMethodsIsRejectedUnlessAll() throws IOException {
        final Path t = TestTraces.trace(dir);
        final Cli.Result r = Cli.vbtm("find", t, "App::handle");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("'App::handle' matches 2 methods:"), r.err());
        assertTrue(r.err().contains("hint: "), r.err());
        assertEquals(0, Cli.vbtm("find", t, "App::handle", "--all").code());
        assertTrue(Cli.vbtm("find", t, "App.handle#2").line("matches:").startsWith("matches: 1 call"),
                "the numbered printed name picks one method");
        assertTrue(Cli.vbtm("find", t, "com.other.App::handle").line("pattern:").contains("com.other.App.handle()V"));
        assertEquals(1, Cli.vbtm("find", t, "Nope::x").code());
    }
}
