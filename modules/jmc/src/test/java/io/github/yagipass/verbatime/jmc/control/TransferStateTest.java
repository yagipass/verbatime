package io.github.yagipass.verbatime.jmc.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.jmc.control.SpyView.FakeEditorHandle;

final class TransferStateTest {

    private static final Transfer.Listener SILENT = new Transfer.Listener() {
        @Override
        public void progress(final long bytes) {
        }

        @Override
        public void finished(final long bytes) {
        }

        @Override
        public void failed(final String message) {
        }

        @Override
        public void stopped() {
        }
    };

    private static Transfer pull(final long id) {
        return new Transfer(new FakeAgent(), id, Path.of("/tmp/rec-" + id + ".vbtm"), 0, SILENT);
    }

    @Test
    void aPullCancelledByTheConnectionStaysStoppingUntilItReportsStopped() {
        final TransferState t = new TransferState();
        final List<String> cancelled = new ArrayList<>();
        final Transfer first = pull(1);
        t.started(first, () -> cancelled.add("first"));
        t.transferred(120);

        t.cancelCurrent();
        assertEquals(List.of("first"), cancelled, "cancelling runs the job's cancel action");
        assertNull(t.current());
        assertTrue(t.isStopping(), "the old transfer is still winding down");
        assertEquals(120, t.transferredBytes(), "the byte count survives so the message can say where it stopped");

        t.defer(2, Path.of("/tmp/rec-2.vbtm"));
        assertEquals(2, t.transferringId(), "the parked recording counts as attached so status polls do not re-attach it");
        assertTrue(t.isActive(), "a parked transfer still blocks Start");
        assertNull(t.takeDeferred(), "the next transfer must not start while the old one is still stopping");

        assertFalse(t.clearStopping(pull(9)), "a stranger's stopped() does not release the slot");
        assertTrue(t.clearStopping(first));
        final TransferState.Deferred next = t.takeDeferred();
        assertNotNull(next);
        assertEquals(2, next.recordingId());
        assertNull(t.takeDeferred(), "pending is handed out once");
        assertFalse(t.isActive());
        assertEquals(-1, t.transferringId());
    }

    @Test
    void aCallbackFromAnUnknownPullClearsNothing() {
        final TransferState t = new TransferState();
        final Transfer current = pull(5);
        final FakeEditorHandle editor = new FakeEditorHandle();
        t.started(current, () -> {
        });
        t.editorOpened(editor, 1_000);

        final Transfer stale = pull(4);
        assertFalse(t.isCurrent(stale));
        assertFalse(t.clearCurrent(stale), "a late callback from an earlier transfer must not end the current one");
        assertSame(current, t.current());
        assertSame(editor, t.editor(), "the current transfer keeps its editor");

        assertTrue(t.clearCurrent(current));
        assertNull(t.current());
        assertNull(t.editor(), "the editor belongs to the transfer that opened it");
    }

    @Test
    void reloadsAreThrottledFromTheLastReloadNotFromTheFirstByte() {
        final TransferState t = new TransferState();
        t.editorOpened(new FakeEditorHandle(), 10_000);
        assertFalse(t.reloadDue(10_000 + TransferState.RELOAD_THROTTLE_MS - 1));
        assertTrue(t.reloadDue(10_000 + TransferState.RELOAD_THROTTLE_MS));
        t.reloaded(12_000);
        assertFalse(t.reloadDue(13_999), "the clock restarts at every reload");
        assertTrue(t.reloadDue(14_000));
    }

    @Test
    void agentRateAveragesSuccessiveSamplesAndForgetsThemOnReset() {
        final TransferState t = new TransferState();
        assertEquals(0, t.sampleAgentRate(1_000, 100), "one sample is not a rate yet");
        assertEquals(100.0, t.sampleAgentRate(2_000, 200), "100 bytes in 1 s");
        assertEquals(150.0, t.sampleAgentRate(3_000, 400), "(100 + 200) / 2: the new sample is averaged in");
        t.resetAgentRate();
        assertEquals(0, t.sampleAgentRate(4_000, 400), "after a reset the first sample is silent again");
    }
}
