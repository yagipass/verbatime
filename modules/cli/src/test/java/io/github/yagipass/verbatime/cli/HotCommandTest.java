package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HotCommandTest {

    @TempDir
    Path dir;

    @Test
    void recursionCountsOnceInTotal() throws IOException {
        final Cli.Result r = Cli.vbtm("hot", TestTraces.trace(dir), "1", "--by", "total");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.line("scope: session 1").contains("0.1000 ms in root calls, 6 calls"), r.out());
        final String fib = r.line(" 0.0030");
        assertTrue(fib.matches(" 0\\.0030 +3\\.0% +0\\.0030 +2 +App\\.fib"), "the inner fib call is not added again: " + fib);
        assertTrue(r.out().contains(" 0.0400  40.0%    0.0400      2  App.query"), r.out());
    }
}
