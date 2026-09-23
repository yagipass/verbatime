package io.github.yagipass.verbatime.jmc.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class RootSpecsTest {

    @Test
    void looksLikeSpecGatesFreeTextEntry() {
        assertTrue(RootSpecs.looksLikeSpec("a.B::m"));
        assertTrue(RootSpecs.looksLikeSpec("  a.B::m  "));
        assertFalse(RootSpecs.looksLikeSpec("::m"));
        assertFalse(RootSpecs.looksLikeSpec("a.B::"));
        assertFalse(RootSpecs.looksLikeSpec("plainText"));
    }

    @Test
    void withIsTheExactReplaceRootsPayload() {
        final List<RootEntry> applied = List.of(new RootEntry("a.B::m", true), new RootEntry("a.B::n", false));
        assertArrayEquals(new String[] { "a.B::m", "a.B::n", "c.D::x" }, RootSpecs.with(applied, "c.D::x"));
        assertArrayEquals(new String[] { "a.B::m", "a.B::n" }, RootSpecs.with(applied, "a.B::n"),
                "re-adding must not duplicate a root");
    }

    @Test
    void withoutRemovesOneRootAndNothingElse() {
        final List<RootEntry> applied = List.of(new RootEntry("a.B::m", true), new RootEntry("a.B::n", false));
        assertArrayEquals(new String[] { "a.B::m" }, RootSpecs.without(applied, "a.B::n"));
        assertArrayEquals(new String[] { "a.B::m", "a.B::n" }, RootSpecs.without(applied, "zz.Not::there"),
                "removing an absent spec is a no-op");
    }
}
