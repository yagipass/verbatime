package io.github.yagipass.verbatime.jmc.views;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

final class CopyTextsTest {

    @Test
    void gcTextSaysHowMuchOfAFrameWasStopTheWorld() {
        assertEquals(null, CopyTexts.gcText(new TraceSnapshot.GcPauses.Overlap(0, 0), 195_000_000L),
                "a frame no pause touched gets no line rather than a zero");
        assertEquals("GC 165.00 ms in 2 pauses, 85% of total",
                CopyTexts.gcText(new TraceSnapshot.GcPauses.Overlap(165_000_000L, 2), 195_000_000L));
        assertEquals("GC 3.00 ms in 1 pause, 100% of total",
                CopyTexts.gcText(new TraceSnapshot.GcPauses.Overlap(3_000_000L, 1), 2_000_000L),
                "a pause longer than the frame because of 1 ms GC granularity caps at 100% instead of over-explaining");
        assertEquals("GC 3.00 ms in 1 pause", CopyTexts.gcText(new TraceSnapshot.GcPauses.Overlap(3_000_000L, 1), 0),
                "a zero-length frame has no share to report");
    }

    @Test
    void ancestorTextListsRootFirstWithDepthTotalAndSessionShare() throws IOException {
        final TraceSnapshot d = rootAndKid();
        final SelectedCall kid = new SelectedCall(7, 11_000, 4_000, 4_000, 1, 2, -1, false,
                List.of(new SelectedCall.Ancestor(10_000, 20_000, 0, 1)), null, null);
        assertEquals("session #1 Root.root on main, 20.00 µs\n"
                + "depth       total  % of session  method\n"
                + "    0    20.00 µs          100%  pkg.Root.root()V\n"
                + "    1     4.00 µs           20%  pkg.Root.kid()V", CopyTexts.ancestorText(d, kid),
                "one frame per line, full names, so the text can be pasted into an issue or handed to an AI");

        final SelectedCall stray = new SelectedCall(7, 40_000, 1_000, 1_000, 0, 2, -1, false, List.of(), null, null);
        assertEquals("not in a session on main\n"
                + "depth       total  % of session  method\n"
                + "    0     1.00 µs                pkg.Root.kid()V", CopyTexts.ancestorText(d, stray),
                "outside a session there is no denominator, so the share column stays blank");
    }

    private static TraceSnapshot rootAndKid() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "root()V", "kid()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).enter(110, 2).exit(150).exit(300);
        w.chunk(7, 100, p.bytes(), true);
        return TestTraces.index(w);
    }

    @Test
    void sessionTextNamesTheSessionTheFrameBelongsTo() throws IOException {
        final TraceSnapshot d = rootAndKid();

        assertEquals("session #1 Root.root", CopyTexts.sessionText(d, 7, 11_000));
        assertEquals("session #1 Root.root", CopyTexts.sessionText(d, 7, 30_000), "the session end is inclusive");
        assertEquals("not in a session", CopyTexts.sessionText(d, 7, 30_001), "after the session: no attribution");
        assertEquals("not in a session", CopyTexts.sessionText(d, 8, 11_000), "another thread's frame is never attributed");
    }
}
