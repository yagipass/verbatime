package io.github.yagipass.verbatime.jmc.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

final class AgentStatusTest {

    @Test
    void rootEntriesDriveChipStateFromTheStatusPrefix() {
        final List<RootEntry> l = AgentStatus
                .rootEntries(Map.of("root.0", "ok a.B::m", "root.1", "pending a.B::n", "roots", "2"));
        assertEquals(List.of(new RootEntry("a.B::m", true), new RootEntry("a.B::n", false)), l);
    }

    @Test
    void rootEntriesStopAtTheFirstGapSoOrderFollowsTheAgent() {
        final List<RootEntry> l = AgentStatus.rootEntries(Map.of("root.0", "ok a.B::m", "root.2", "ok a.B::x"));
        assertEquals(List.of(new RootEntry("a.B::m", true)), l);
    }

    @Test
    void rootEntriesSplitAtTheFirstSpaceOnlyAndTolerateGarbledLines() {
        final List<RootEntry> l = AgentStatus
                .rootEntries(Map.of("root.0", "future-state a.B::m", "root.1", "specwithoutstate"));
        assertEquals(new RootEntry("a.B::m", false), l.get(0));
        assertEquals(new RootEntry("specwithoutstate", false), l.get(1));
    }

    @Test
    void everyStatusKeyIsReadOnceIntoTypedFields() {
        final AgentStatus s = AgentStatus.parse(Map.ofEntries(Map.entry("v", "4"), Map.entry("pid", "4242"),
                Map.entry("state", "recording"), Map.entry("roots", "1"), Map.entry("root.0", "ok a.B::m"),
                Map.entry("instrumentedClasses", "12"), Map.entry("instrumentedMethods", "340"),
                Map.entry("recording.id", "5"), Map.entry("recording.name", "load test"),
                Map.entry("recording.startEpochMs", "1700000000000"), Map.entry("recording.bytes", "2048"),
                Map.entry("recording.truncated", "true"), Map.entry("lastRecording.id", "4"),
                Map.entry("lastRecording.name", "warm up"), Map.entry("lastRecording.startEpochMs", "1699999000000"),
                Map.entry("lastRecording.bytes", "99"), Map.entry("lastRecording.truncated", "true")));
        assertEquals(4, s.protocol());
        assertEquals("4242", s.pid());
        assertTrue(s.recording());
        assertEquals(List.of(new RootEntry("a.B::m", true)), s.roots());
        assertEquals("12", s.instrumentedClasses());
        assertEquals("340", s.instrumentedMethods());
        assertEquals(5, s.recordingId());
        assertEquals("load test", s.recordingName());
        assertEquals(1_700_000_000_000L, s.recordingStartEpochMs());
        assertEquals(2048, s.recordingBytes());
        assertTrue(s.recordingTruncated());
        assertEquals(4, s.lastRecordingId());
        assertEquals("warm up", s.lastRecordingName());
        assertEquals(1_699_999_000_000L, s.lastRecordingStartEpochMs());
        assertEquals(99, s.lastRecordingBytes());
        assertTrue(s.lastRecordingTruncated());
    }

    @Test
    void anOlderOrTerserAgentYieldsDefaultsInsteadOfExceptions() {
        final AgentStatus s = AgentStatus.parse(Map.of("state", "idle", "recording.bytes", "not-a-number"));
        assertEquals(0, s.protocol(), "no v= means the pre-search protocol");
        assertEquals("?", s.pid());
        assertFalse(s.recording());
        assertEquals(List.of(), s.roots());
        assertEquals(0, s.recordingId());
        assertEquals("", s.recordingName());
        assertEquals(-1, s.recordingBytes(), "unparsable and absent byte counts both read as unknown");
        assertEquals(-1, s.lastRecordingBytes());
        assertEquals(0, s.lastRecordingId());
        assertEquals("", s.lastRecordingName());
        assertEquals(0, s.lastRecordingStartEpochMs(),
                "an agent that does not report the start time cannot be resumed");
        assertFalse(s.recordingTruncated());
        assertFalse(s.lastRecordingTruncated());
    }
}
