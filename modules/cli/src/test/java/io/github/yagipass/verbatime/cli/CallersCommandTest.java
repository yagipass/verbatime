package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CallersCommandTest {

    @TempDir
    Path dir;

    @Test
    void callersAreGroupedByPath() throws IOException {
        final Cli.Result r = Cli.vbtm("callers", TestTraces.trace(dir), "App::query");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.line("    4").matches(" +4 +0\\.0490 +1\\.1 +App\\.query"), r.out());
        assertTrue(r.out().contains("<- App.handle, root"), r.out());
        assertTrue(r.out().contains("<- App.handle#2, root"), r.out());
    }
}
