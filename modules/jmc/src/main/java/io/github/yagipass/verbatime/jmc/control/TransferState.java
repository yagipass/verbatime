package io.github.yagipass.verbatime.jmc.control;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import io.github.yagipass.verbatime.jmc.control.ControlPresenter.EditorHandle;

public final class TransferState {

    static final long RELOAD_THROTTLE_MS = 2_000;

    record Deferred(long recordingId, Path file) {
    }

    private @Nullable Transfer current;

    private @Nullable Runnable cancel;

    private @Nullable Transfer stopping;

    private @Nullable Deferred deferred;

    private long transferredBytes;

    private @Nullable EditorHandle editor;

    private long lastReloadMs;

    private long agentSampleMs;

    private long agentSampleBytes;

    private double agentBytesPerSec;

    @Nullable
    Transfer current() {
        return current;
    }

    boolean isStopping() {
        return stopping != null;
    }

    @Nullable
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

    void transferred(long bytes) {
        transferredBytes = bytes;
    }

    void started(Transfer p, Runnable cancelAction) {
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

    void defer(long recordingId, Path file) {
        deferred = new Deferred(recordingId, file);
    }

    @Nullable
    Deferred takeDeferred() {
        if (stopping != null) {
            return null;
        }
        Deferred p = deferred;
        deferred = null;
        return p;
    }

    void dropDeferred() {
        deferred = null;
    }

    @SuppressWarnings("ReferenceEquality")
    boolean isCurrent(Transfer p) {
        return current == p;
    }

    @SuppressWarnings("ReferenceEquality")
    boolean clearStopping(Transfer p) {
        if (stopping != p) {
            return false;
        }
        stopping = null;
        return true;
    }

    @SuppressWarnings("ReferenceEquality")
    boolean clearCurrent(Transfer p) {
        if (current != p) {
            return false;
        }
        current = null;
        cancel = null;
        editor = null;
        return true;
    }

    @Nullable
    EditorHandle editor() {
        return editor;
    }

    void editorOpened(@Nullable EditorHandle e, long now) {
        editor = e;
        lastReloadMs = now;
    }

    boolean reloadDue(long now) {
        return now - lastReloadMs >= RELOAD_THROTTLE_MS;
    }

    void reloaded(long now) {
        lastReloadMs = now;
    }

    double sampleAgentRate(long now, long agentBytes) {
        if (agentSampleMs > 0 && now > agentSampleMs && agentBytes >= agentSampleBytes) {
            double r = (agentBytes - agentSampleBytes) * 1000.0 / (now - agentSampleMs);
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
