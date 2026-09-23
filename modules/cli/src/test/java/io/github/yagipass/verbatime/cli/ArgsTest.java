package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ArgsTest {

    @Test
    void durationsParseToTicks() {
        assertEquals(10_000, Args.parseTicks("x", "1ms"));
        assertEquals(10_000, Args.parseTicks("x", "1"), "a bare number is ms");
        assertEquals(5_000, Args.parseTicks("x", "500us"));
        assertEquals(5_000, Args.parseTicks("x", "0.5ms"));
        assertEquals(20_000_000, Args.parseTicks("x", "2s"));
        assertEquals(1, Args.parseTicks("x", "1ns"), "a fraction of a tick rounds up to one tick");
        assertThrows(CliException.class, () -> Args.parseTicks("x", "fast"));
        assertThrows(CliException.class, () -> Args.parseTicks("x", "3h"));
    }
}
