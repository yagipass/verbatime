package io.github.yagipass.verbatime.jmc.control;

import java.nio.file.Path;

import io.github.yagipass.verbatime.jmc.control.ControlPresenter.EditorHandle;

public final class TransferState {

    static final long RELOAD_THROTTLE_MS = 2_000;

    record Deferred(long recordingId, Path file) {
    }

    private Transfer current;

    private Runnable cancel;

    private Transfer stopping;

    private Deferred deferred;

    private long transferredBytes;

    private EditorHandle editor;

    private long lastReloadMs;

    private long agentSampleMs;

    private long agentSampleBytes;

    private double agentBytesPerSec;

    Transfer current() {
        return current;
    }

    boolean isStopping() {
        return stopping != null;
    }

    Deferred deferred() {
        return deferred;
    }

    boolean isActive() {
        return current != null || deferred != null;
    }

    long transferringId() {
        if (current != null) {
            return current.recordingId();
        }
        return deferred == null ? -1 : deferred.recordingId();
    }

    long transferredBytes() {
        return transferredBytes;
    }

    void transferred(final long bytes) {
        transferredBytes = bytes;
    }

    void started(final Transfer p, final Runnable cancelAction) {
        current = p;
        cancel = cancelAction;
    }

    void cancelCurrent() {
        if (current != null) {
            stopping = current;
        }
        if (cancel != null) {
            cancel.run();
        }
        cancel = null;
        current = null;
        editor = null;
    }

    void defer(final long recordingId, final Path file) {
        deferred = new Deferred(recordingId, file);
    }

    Deferred takeDeferred() {
        if (stopping != null) {
            return null;
        }
        final Deferred p = deferred;
        deferred = null;
        return p;
    }

    void dropDeferred() {
        deferred = null;
    }

    @SuppressWarnings("ReferenceEquality")
    boolean isCurrent(final Transfer p) {
        return current == p;
    }

    @SuppressWarnings("ReferenceEquality")
    boolean clearStopping(final Transfer p) {
        if (stopping != p) {
            return false;
        }
        stopping = null;
        return true;
    }

    @SuppressWarnings("ReferenceEquality")
    boolean clearCurrent(final Transfer p) {
        if (current != p) {
            return false;
        }
        current = null;
        cancel = null;
        editor = null;
        return true;
    }

    EditorHandle editor() {
        return editor;
    }

    void editorOpened(final EditorHandle e, final long now) {
        editor = e;
        lastReloadMs = now;
    }

    boolean reloadDue(final long now) {
        return now - lastReloadMs >= RELOAD_THROTTLE_MS;
    }

    void reloaded(final long now) {
        lastReloadMs = now;
    }

    double sampleAgentRate(final long now, final long agentBytes) {
        if (agentSampleMs > 0 && now > agentSampleMs && agentBytes >= agentSampleBytes) {
            final double r = (agentBytes - agentSampleBytes) * 1000.0 / (now - agentSampleMs);
            agentBytesPerSec = agentBytesPerSec <= 0 ? r : (agentBytesPerSec + r) / 2;
        }
        agentSampleMs = now;
        agentSampleBytes = agentBytes;
        return agentBytesPerSec;
    }

    void resetAgentRate() {
        agentSampleMs = 0;
        agentSampleBytes = 0;
        agentBytesPerSec = 0;
    }
}
