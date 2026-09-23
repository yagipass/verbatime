package io.github.yagipass.verbatime.jmc.control;

import java.util.List;

import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.recordings.LocalRecordings.Entry;

public final class ControlTexts {

    private static final int DELETE_PROMPT_NAMES = 10;

    private ControlTexts() {
    }

    public static String deletePrompt(final List<Entry> targets, final int skippedTransferring) {
        final StringBuilder sb = new StringBuilder();
        if (targets.size() == 1) {
            final Entry e = targets.get(0);
            sb.append("Delete ").append(e.name()).append(", ").append(sizeOrUnknown(e.size())).append("?\n")
                    .append(e.file());
        } else {
            long total = 0;
            for (final Entry e : targets) {
                total += Math.max(e.size(), 0);
            }
            sb.append("Delete ").append(Formats.plural(targets.size(), "recording")).append(", ").append(Formats.fmtBytes(total))
                    .append(" in total?\n");
            final int shown = Math.min(targets.size(), DELETE_PROMPT_NAMES);
            for (int i = 0; i < shown; i++) {
                sb.append('\n').append(targets.get(i).name());
            }
            if (targets.size() > shown) {
                sb.append("\n\u2026 and ").append(targets.size() - shown).append(" more");
            }
        }
        if (skippedTransferring > 0) {
            sb.append("\n\n").append(skippedTransferring).append(" still being transferred will be skipped.");
        }
        return sb.append("\n\nThis cannot be undone.").toString();
    }

    static String connectionText(final String target, final String pid) {
        return target + ", pid " + pid;
    }

    static String countText(final String instrumentedClasses, final String instrumentedMethods) {
        return instrumentedClasses == null || instrumentedMethods == null ? ""
                : instrumentedClasses + " classes, " + instrumentedMethods + " methods instrumented";
    }

    static String recordingText(final long recordingId, final long elapsedMs, final long transferredBytes,
            final long agentBytes, final double agentBytesPerSec) {
        final String id = recordingId > 0 ? Long.toString(recordingId) : "?";
        final String lag = agentBytes < 0 ? "" : ", " + Formats.fmtLag(agentBytes, transferredBytes, agentBytesPerSec);
        return "Recording #" + id + ", " + Formats.fmtElapsed(elapsedMs) + " elapsed, " + Formats.fmtBytes(transferredBytes)
                + " transferred" + lag;
    }

    static String waitingText(final long recordingId) {
        return "Stopping the previous transfer before saving #" + recordingId;
    }

    static String transferringText(final long recordingId, final long transferredBytes, final long agentBytes) {
        return "Transferring #" + recordingId + ": " + Formats.fmtBytes(transferredBytes)
                + (agentBytes < 0 ? " transferred" : " of " + Formats.fmtBytes(agentBytes));
    }

    static String idleText(final boolean hasRoots) {
        return hasRoots ? "Idle" : "Idle. Add a root to start recording";
    }

    public static String stillTransferringText(final Entry e) {
        return "Cannot delete " + e.name() + ": it is still being transferred.\n\n"
                + "Stop the recording in the Verbatime Control view first.";
    }

    public static String deleteFailedText(final int failed, final int total, final String firstError) {
        return "Could not delete " + failed + " of " + Formats.plural(total, "recording") + ".\n\n" + firstError;
    }

    public static String sizeOrUnknown(final long b) {
        return b < 0 ? "?" : Formats.fmtBytes(b);
    }
}
