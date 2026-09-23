package io.github.yagipass.verbatime.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FormatsTest {

    @Test
    void durationsPrintCompactlyAndParseBack() {
        assertEquals("15us", Formats.duration(150));
        assertEquals("1.5us", Formats.duration(15));
        assertEquals("1ms", Formats.duration(10_000));
        assertEquals("177.8984ms", Formats.duration(1_778_984));
        assertEquals("2s", Formats.duration(20_000_000));
        assertEquals(15, Args.parseTicks("x", Formats.duration(15)), "what tree prints as a floor is accepted back");
    }
}
