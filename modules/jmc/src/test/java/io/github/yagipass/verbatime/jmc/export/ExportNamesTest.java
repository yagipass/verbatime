package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

final class ExportNamesTest {

    private static TraceSnapshot trace() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.clazz(1, "pkg.a.X", "m()V", "m(I)V", "other()V");
        w.clazz(4, "pkg.b.X", "m()V");
        return TestTraces.index(w);
    }

    @Test
    void collidingShortNamesAreNumberedInFirstUseOrder() throws IOException {
        final ExportNames names = new ExportNames(trace());
        assertEquals("X.m", names.displayName(2), "the first id met keeps the bare name, whatever its id");
        assertEquals("X.m#2", names.displayName(1), "an overload met later is numbered");
        assertEquals("X.m#3", names.displayName(4), "a same-named class in another package is numbered too");
        assertEquals("X.other", names.displayName(3));
        assertEquals("X.m", names.displayName(2), "stable on repeated lookups");
        assertEquals(4, names.registeredCount());
        assertEquals(2, names.registeredIdAt(0));
        assertEquals(1, names.registeredIdAt(1));
        assertEquals(4, names.registeredIdAt(2));
        assertEquals(3, names.registeredIdAt(3));
        assertEquals("pkg.a.X.m(I)V", names.fullName(2), "the methods section resolves a name to its signature");
        assertEquals("pkg.a.X.m()V", names.fullName(1), "…and a numbered name to the other overload");
    }

    @Test
    void unknownIdsAreNamedLikeTheViewerAndCanBeLookedUp() throws IOException {
        final ExportNames names = new ExportNames(trace());
        assertEquals("<unknown#9>", names.displayName(9));
        assertEquals("<unknown#9>", names.fullName(9));
        assertEquals("<unknown#70000>", names.displayName(70_000), "ids beyond the registered range grow the table");
        assertArrayEquals("<unknown#9>".getBytes(StandardCharsets.UTF_8), names.utf8(9));
    }

    @Test
    void shortNameMatchesTheViewerPageRule() {
        assertEquals("Cls.m", ExportNames.shortName("pkg.Cls.m(I)V"));
        assertEquals("Cls.m", ExportNames.shortName("Cls.m"));
        assertEquals("m", ExportNames.shortName("m()V"));
        assertEquals("<no enter>", ExportNames.shortName("<no enter>"));
    }
}
