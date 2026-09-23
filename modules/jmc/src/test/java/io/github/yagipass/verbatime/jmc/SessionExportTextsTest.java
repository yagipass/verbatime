package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.export.SessionExporter;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

final class SessionExportTextsTest {

    @Test
    void defaultFileNameSitsBesideTheRecordingAndSaysWhichFloorProducedIt() {

        assertEquals("rec-1-1788323855884-session1-10us.txt", SessionExportTexts.exportFileName("rec-1-1788323855884.vbtm", 1, 10));
        assertEquals("trace-session12-100us.txt", SessionExportTexts.exportFileName("trace.VBTM", 12, 100),
                "the extension is stripped whatever its case, so the name never reads x.VBTM-session…");
        assertEquals("plain-session1-1us.txt", SessionExportTexts.exportFileName("plain", 1, 1),
                "a file opened without the .vbtm extension keeps its whole name");
        assertEquals("rec-1-2-session3-nofloor.txt", SessionExportTexts.exportFileName("rec-1-2.vbtm", 3, 0),
                "no floor is named, not numbered, so it cannot be mistaken for a 0 µs threshold");
    }

    @Test
    void theFloorRadioIsInMicrosecondsButTheExporterTakesNanoseconds() {
        assertEquals(0, SessionExportTexts.floorNs(0), "none: every call is listed");
        assertEquals(1_000, SessionExportTexts.floorNs(1));
        assertEquals(10_000, SessionExportTexts.floorNs(10));
        assertEquals(100_000, SessionExportTexts.floorNs(100));
    }

    private static TraceSnapshot trace() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "a");
        w.thread(8, "b");
        w.clazz(1, "pkg.R", "r()V", "k()V");
        w.chunk(7, 100, new TraceBuilder.Payload(100).enter(100, 1).enter(110, 2).exit(150).exit(300).bytes(), true);
        w.chunk(7, 400, new TraceBuilder.Payload(400).enter(400, 1).exit(1400).bytes(), true);
        w.chunk(8, 100, new TraceBuilder.Payload(100).enter(100, 1).exit(700).bytes(), true);
        w.end();
        return TestTraces.index(w);
    }

    private static SelectedCall frame(final long tid, final long startNs) {
        return new SelectedCall(tid, startNs, 1, 1, 0, 1, -1, false, List.of(), null, null);
    }

    @Test
    void preselectionFollowsTheSelectedFrameThenTheLongestSession() throws IOException {
        final TraceSnapshot d = trace();
        assertEquals(1, SessionExportTexts.defaultSession(d, frame(7, 11_000)).seq, "the clicked frame's session, even if short");
        assertEquals(1, SessionExportTexts.defaultSession(d, frame(7, 30_000)).seq, "the session end is inclusive");
        assertEquals(3, SessionExportTexts.defaultSession(d, frame(8, 20_000)).seq);
        assertEquals(2, SessionExportTexts.defaultSession(d, null).seq, "no selection: the longest session, like the chart's first focus");
        assertEquals(2, SessionExportTexts.defaultSession(d, frame(9, 11_000)).seq, "a frame the index cannot place falls back too");
        final TraceBuilder w = TestTraces.writer();
        w.end();
        assertNull(SessionExportTexts.defaultSession(TestTraces.index(w), null), "no sessions at all");
    }

    @Test
    void tiesGoToTheLowestSeqSoTheChoiceIsStable() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.clazz(1, "pkg.R", "r()V");
        w.chunk(7, 100, new TraceBuilder.Payload(100).enter(100, 1).exit(200).bytes(), true);
        w.chunk(7, 300, new TraceBuilder.Payload(300).enter(300, 1).exit(400).bytes(), true);
        w.end();
        assertEquals(1, SessionExportTexts.defaultSession(TestTraces.index(w), null).seq);
    }

    @Test
    void flagsExplainWhyARowIsGreyOrPartial() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.clazz(1, "pkg.R", "r()V");
        w.chunk(7, 100, new TraceBuilder.Payload(100).enter(100, 1).exit(200).bytes(), true);
        w.chunk(8, 100, new TraceBuilder.Payload(100).enter(100, 1).bytes(), false);
        w.chunk(9, 150, new byte[0], true);
        w.end();
        final TraceSnapshot d = TestTraces.index(w);
        assertEquals("", SessionExportTexts.flags(d.sessions.get(0)));
        assertTrue(SessionExportTexts.isExportable(d.sessions.get(0)));
        assertEquals("unclosed", SessionExportTexts.flags(d.sessions.get(1)), "flushed while still running");
        assertTrue(SessionExportTexts.isExportable(d.sessions.get(1)), "an unclosed session still has frames to export");
        assertEquals("no enter, empty", SessionExportTexts.flags(d.sessions.get(2)));
        assertFalse(SessionExportTexts.isExportable(d.sessions.get(2)), "nothing to walk: the index keeps no chunk model");
    }

    @Test
    void summaryStatesWhatWasWrittenAndWhere() throws IOException {
        final TraceSnapshot.Session s = trace().sessions.get(0);
        final SessionExporter.Result r = new SessionExporter.Result(Path.of("/tmp/x.txt"), 12_031_175, 128_836, 124_739,
                5_298_537, 75_430, 5_223_107, 128, 14_963_000);
        assertEquals("Exported session #1 with a floor of 10 µs to\n/tmp/x.txt\n\n124,739 body lines, 11.5 MB\n"
                + "75,430 calls listed, 5,223,107 calls below the floor kept as counts",
                SessionExportTexts.summary(Path.of("/tmp/x.txt"), s, 10_000, r));
    }
}
