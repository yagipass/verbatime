package io.github.yagipass.verbatime.jmc.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record AgentStatus(int protocol, String pid, boolean recording, List<RootEntry> roots, String instrumentedClasses,
        String instrumentedMethods, long recordingId, String recordingName, long recordingStartEpochMs,
        long recordingBytes, boolean recordingTruncated, long lastRecordingId, String lastRecordingName,
        long lastRecordingStartEpochMs, long lastRecordingBytes, boolean lastRecordingTruncated) {

    static AgentStatus parse(final Map<String, String> st) {
        return new AgentStatus((int) longOr(st, "v", 0), st.getOrDefault("pid", "?"), "recording".equals(st.get("state")),
                rootEntries(st), st.get("instrumentedClasses"), st.get("instrumentedMethods"),
                longOr(st, "recording.id", 0), st.getOrDefault("recording.name", ""),
                longOr(st, "recording.startEpochMs", 0), longOr(st, "recording.bytes", -1),
                "true".equals(st.get("recording.truncated")), longOr(st, "lastRecording.id", 0),
                st.getOrDefault("lastRecording.name", ""), longOr(st, "lastRecording.startEpochMs", 0),
                longOr(st, "lastRecording.bytes", -1), "true".equals(st.get("lastRecording.truncated")));
    }

    static List<RootEntry> rootEntries(final Map<String, String> st) {
        final List<RootEntry> out = new ArrayList<>();
        for (int i = 0; st.containsKey("root." + i); i++) {
            final String v = st.get("root." + i);
            final int sp = v.indexOf(' ');
            final String spec = sp < 0 ? v : v.substring(sp + 1);
            if (spec.isEmpty()) {
                continue;
            }
            out.add(new RootEntry(spec, sp > 0 && "ok".equals(v.substring(0, sp))));
        }
        return out;
    }

    private static long longOr(final Map<String, String> st, final String key, final long dflt) {
        final String v = st.get(key);
        if (v == null) {
            return dflt;
        }
        try {
            return Long.parseLong(v);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }
}
