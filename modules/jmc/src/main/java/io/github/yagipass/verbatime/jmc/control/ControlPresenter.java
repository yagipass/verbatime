package io.github.yagipass.verbatime.jmc.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.UiThread;
import io.github.yagipass.verbatime.jmc.recordings.LocalRecordings;

public final class ControlPresenter {

    enum State {
        DISCONNECTED, CONNECTING, CONNECTED, LOST
    }

    record ViewState(State state, String connectionText, boolean recording, List<RootEntry> roots,
            String instrumentedText, String statusText, boolean startPending, boolean stopPending, boolean transferring) {

        static ViewState disconnected(final State state) {
            return new ViewState(state, null, false, List.of(), "", "Not connected", false, false, false);
        }

        static ViewState connected(final String connectionText, final boolean recording,
                final List<RootEntry> roots, final String instrumentedText, final String statusText,
                final boolean startPending, final boolean stopPending, final boolean transferring) {
            return new ViewState(State.CONNECTED, connectionText, recording, roots, instrumentedText, statusText,
                    startPending, stopPending, transferring);
        }

        ViewState withPending(final boolean startPending, final boolean stopPending) {
            return new ViewState(state, connectionText, recording, roots, instrumentedText, statusText, startPending,
                    stopPending, transferring);
        }

        boolean canStart() {
            return !recording && !transferring && !roots.isEmpty() && !startPending;
        }

        boolean canStop() {
            return recording && !stopPending;
        }

        boolean rootsLocked() {
            return recording;
        }
    }

    interface EditorHandle {

        boolean isOpen();

        boolean isLoading();

        void reload(boolean live);
    }

    interface View {

        void render(ViewState p);

        void message(String text);

        void rootCandidates(String[] specs);

        void rootAccepted();

        EditorHandle openEditor(Path file);

        void recordingsChanged();
    }

    public interface Settings {

        String roots();

        Path recordingsDir();

        void save(String target, String roots);
    }

    interface TransferScheduler {

        Runnable schedule(Transfer p);
    }

    static final int POLL_MS = 1_000;

    static final int CONNECT_TIMEOUT_MS = 30_000;

    static final int STALL_TIMEOUT_MS = 15_000;

    static final int SEARCH_DEBOUNCE_MS = 250;

    private static final int SEARCH_MAX_RESULTS = 50;

    private static final int SEARCH_MIN_PROTOCOL = 4;

    static final int FINAL_RELOAD_RETRY_MS = 500;

    private final View view;

    private final UiThread uiThread;

    private final Supplier<ExecutorService> newExecutor;

    private final Agent.Dialer dialer;

    private final Settings settings;

    private final LongSupplier nowMs;

    private final TransferScheduler scheduler;

    private volatile State state = State.DISCONNECTED;

    private ViewState lastState = ViewState.disconnected(State.DISCONNECTED);

    private ExecutorService background;

    private final AtomicInteger attempt = new AtomicInteger();

    private volatile Agent agent;

    private String target;

    private String connectionDir;

    private String savedRoots;

    private List<RootEntry> appliedRoots = List.of();

    private boolean rootsReapplied;

    private boolean searchSupported;

    private int searchGeneration;

    private boolean startPending;

    private boolean stopPending;

    private long resumeTriedId;

    private final TransferState transfer = new TransferState();

    private final AtomicReference<Agent> statusInFlight = new AtomicReference<>();

    private long pollStartedMs;

    private boolean pollChainActive;

    private volatile boolean disposed;

    ControlPresenter(final View view, final UiThread uiThread, final Supplier<ExecutorService> newExecutor,
            final Agent.Dialer dialer, final Settings settings, final LongSupplier nowMs,
            final TransferScheduler scheduler) {
        this.view = view;
        this.uiThread = uiThread;
        this.newExecutor = newExecutor;
        this.dialer = dialer;
        this.settings = settings;
        this.nowMs = nowMs;
        this.scheduler = scheduler;
        this.savedRoots = settings.roots();
    }

    State state() {
        return state;
    }

    void connect(final String target) {
        if (agent != null || state == State.CONNECTING || target.isEmpty()) {
            return;
        }
        this.target = target;
        state = State.CONNECTING;
        final int a = attempt.incrementAndGet();
        final ExecutorService bg = newExecutor.get();
        background = bg;
        view.render(ViewState.disconnected(State.CONNECTING));
        view.message("Connecting: " + target);
        uiThread.postAfter(CONNECT_TIMEOUT_MS, () -> {
            if (isAttempt(a)) {
                abandonAttempt("Connection timed out after " + CONNECT_TIMEOUT_MS / 1000 + " s: " + target);
            }
        });
        bg.execute(() -> {
            Agent c = null;
            try {
                c = dialer.dial(target);
                final Map<String, String> st = c.status();
                if (!isAttempt(a)) {
                    closeQuietly(c);
                    bg.shutdown();
                    return;
                }
                final Agent ok = c;
                uiThread.post(() -> connected(a, bg, ok, st));
            } catch (final Exception e) {
                if (c != null) {
                    closeQuietly(c);
                }
                bg.shutdown();
                final String msg = String.valueOf(e.getMessage());
                uiThread.post(() -> isAttempt(a), () -> abandonAttempt("Connection failed: " + msg));
            }
        });
    }

    void cancelConnect() {
        if (state == State.CONNECTING) {
            abandonAttempt("Connection cancelled");
        }
    }

    private boolean isAttempt(final int a) {
        return !disposed && state == State.CONNECTING && a == attempt.get();
    }

    private void abandonAttempt(final String message) {
        state = State.DISCONNECTED;
        background = null;
        view.render(ViewState.disconnected(State.DISCONNECTED));
        view.message(message);
    }

    private void connected(final int a, final ExecutorService bg, final Agent c, final Map<String, String> st) {
        if (!isAttempt(a)) {
            bg.execute(() -> closeQuietly(c));
            bg.shutdown();
            return;
        }
        agent = c;
        connectionDir = LocalRecordings.fileSafe(target);
        settings.save(target, savedRoots);
        state = State.CONNECTED;
        final AgentStatus status = AgentStatus.parse(st);
        searchSupported = status.protocol() >= SEARCH_MIN_PROTOCOL;
        view.message("Connected to agent pid " + status.pid());
        apply(status);
        startPolling();
    }

    private <T> void call(final Agent c, final Callable<T> op, final Consumer<T> onOk,
            final Consumer<String> onFail) {
        final ExecutorService bg = background;
        if (bg == null || !isCurrent(c)) {
            return;
        }
        bg.execute(() -> {
            final T result;
            try {
                result = op.call();
            } catch (final Exception e) {
                final String msg = String.valueOf(e.getMessage());
                uiThread.post(() -> isCurrent(c), () -> onFail.accept(msg));
                return;
            }
            uiThread.post(() -> isCurrent(c), () -> onOk.accept(result));
        });
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean isCurrent(final Agent c) {
        return agent == c;
    }

    void disconnect() {
        teardown(State.DISCONNECTED, "Disconnected");
    }

    private void lost(final String msg) {
        teardown(State.LOST, "Connection lost: " + msg);
    }

    private void teardown(final State next, final String message) {
        transfer.cancelCurrent();
        transfer.dropDeferred();
        releaseClient();
        connectionDir = null;
        state = next;
        appliedRoots = List.of();
        rootsReapplied = false;
        startPending = false;
        stopPending = false;
        resumeTriedId = 0;
        lastState = ViewState.disconnected(next);
        view.render(lastState);
        view.message(message);
    }

    private void startPolling() {
        if (!pollChainActive) {
            pollChainActive = true;
            uiThread.postAfter(POLL_MS, this::pollTick);
        }
    }

    @SuppressWarnings("ReferenceEquality")
    private void pollTick() {
        if (disposed || agent == null) {
            pollChainActive = false;
            return;
        }
        final Agent c = agent;
        final long now = nowMs.getAsLong();
        if (statusInFlight.get() == c && now - pollStartedMs >= STALL_TIMEOUT_MS) {
            lost("the agent has not answered for " + STALL_TIMEOUT_MS / 1000 + " s");
            pollChainActive = false;
            return;
        }
        if (statusInFlight.compareAndSet(null, c)) {
            pollStartedMs = now;
            call(c, () -> {
                try {
                    return c.status();
                } finally {
                    statusInFlight.compareAndSet(c, null);
                }
            }, st -> apply(AgentStatus.parse(st)), this::lost);
        }
        uiThread.postAfter(POLL_MS, this::pollTick);
    }

    private void apply(final AgentStatus st) {
        appliedRoots = st.roots();
        if (st.recording()) {
            startPending = false;
            final long rid = st.recordingId();
            if (rid > 0 && transfer.transferringId() != rid) {
                transferRecording(rid, st.recordingName(), Long.toString(st.recordingStartEpochMs()));
            }
        } else {
            stopPending = false;
            resumeUndelivered(st);
        }
        lastState = viewStateFor(st);
        view.render(lastState);

        if (!rootsReapplied) {
            rootsReapplied = true;
            if (!st.recording() && st.roots().isEmpty() && !savedRoots.isBlank()) {
                reapplySavedRoots();
            }
        }
        if (st.recordingTruncated()) {
            view.message("The agent could not write its output, so the current recording is truncated");
        } else if (!st.recording() && st.lastRecordingTruncated()) {
            view.message("The agent could not write its output, so the last recording is truncated");
        }
    }

    private ViewState viewStateFor(final AgentStatus st) {
        final String countText = ControlTexts.countText(st.instrumentedClasses(), st.instrumentedMethods());
        final boolean transferring = !st.recording() && transfer.isActive();
        final String recordingText;
        if (st.recording()) {
            final long now = nowMs.getAsLong();
            final long agentBytes = st.recordingBytes();
            final double rate = agentBytes < 0 ? 0 : transfer.sampleAgentRate(now, agentBytes);
            recordingText = ControlTexts.recordingText(st.recordingId(), now - st.recordingStartEpochMs(),
                    transfer.transferredBytes(), agentBytes, rate);
        } else if (transfer.deferred() != null) {
            recordingText = ControlTexts.waitingText(transfer.deferred().recordingId());
        } else if (transferring) {
            recordingText = ControlTexts.transferringText(transfer.current().recordingId(), transfer.transferredBytes(),
                    st.lastRecordingBytes());
        } else {
            recordingText = ControlTexts.idleText(!st.roots().isEmpty());
        }
        return ViewState.connected(ControlTexts.connectionText(target, st.pid()), st.recording(), st.roots(),
                countText, recordingText, startPending, stopPending, transferring);
    }

    void search(final String query) {
        searchGeneration++;
        final int gen = searchGeneration;
        final String q = query.trim();
        if (q.length() < 2 || agent == null || !searchSupported) {
            view.rootCandidates(new String[0]);
            return;
        }
        uiThread.postAfter(SEARCH_DEBOUNCE_MS, () -> {
            if (gen == searchGeneration) {
                runSearch(gen, q);
            }
        });
    }

    private void runSearch(final int gen, final String query) {
        final Agent c = agent;
        if (c == null) {
            return;
        }
        call(c, () -> c.searchMethods(query, SEARCH_MAX_RESULTS), found -> {
            if (gen == searchGeneration) {
                view.rootCandidates(found == null ? new String[0] : found);
            }
        }, msg -> {
            if (searchSupported) {
                searchSupported = false;
                view.rootCandidates(new String[0]);
                view.message("Method search is not available on this agent: " + msg);
            }
        });
    }

    void addRoot(final String spec) {
        final String[] arr = RootSpecs.with(appliedRoots, spec);
        replaceRoots(arr, true, "Applied " + Formats.plural(arr.length, "root"), "Failed to apply roots: ",
                view::rootAccepted);
    }

    void removeRoot(final String spec) {
        final String[] arr = RootSpecs.without(appliedRoots, spec);
        replaceRoots(arr, true, "Applied " + Formats.plural(arr.length, "root"), "Failed to apply roots: ", () -> {
        });
    }

    private void reapplySavedRoots() {
        final String[] arr = savedRoots.lines().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (arr.length == 0) {
            return;
        }
        replaceRoots(arr, false, "Re-applied " + Formats.plural(arr.length, "saved root"),
                "Could not re-apply the saved roots: ", () -> {
                });
    }

    private void replaceRoots(final String[] arr, final boolean remember, final String okMessage,
            final String failPrefix, final Runnable onSuccess) {
        final Agent c = agent;
        if (c == null) {
            return;
        }
        call(c, () -> {
            c.replaceRoots(arr);
            return c.status();
        }, st -> {
            if (remember) {
                savedRoots = String.join("\n", arr);
                settings.save(target, savedRoots);
            }
            onSuccess.run();
            view.message(okMessage);
            apply(AgentStatus.parse(st));
        }, msg -> view.message(failPrefix + msg));
    }

    void startRecording() {
        final Agent c = agent;
        if (c == null || startPending) {
            return;
        }
        startPending = true;
        renderPending();
        call(c, c::startRecording, id -> view.message("Started recording #" + id), msg -> {
            startPending = false;
            renderPending();
            view.message("Failed to start: " + msg);
        });
    }

    void stopRecording() {
        final Agent c = agent;
        if (c == null || stopPending) {
            return;
        }
        stopPending = true;
        renderPending();
        call(c, () -> {
            c.stopRecording();
            return null;
        }, v -> view.message("Stopped. Waiting for the transfer to complete…"), msg -> {
            stopPending = false;
            renderPending();
            view.message("Failed to stop: " + msg);
        });
    }

    private void renderPending() {
        lastState = lastState.withPending(startPending, stopPending);
        view.render(lastState);
    }

    private void resumeUndelivered(final AgentStatus st) {
        final long rid = st.lastRecordingId();
        if (transfer.transferringId() > 0 || rid <= 0 || rid == resumeTriedId || st.lastRecordingStartEpochMs() <= 0
                || st.lastRecordingBytes() < 0) {
            return;
        }
        final Path local = localFor(rid, st.lastRecordingName(), Long.toString(st.lastRecordingStartEpochMs()));
        final long have;
        try {
            have = Files.exists(local) ? Files.size(local) : 0;
        } catch (final IOException e) {
            resumeTriedId = rid;
            view.message("Cannot check the local copy of recording #" + rid + ": " + e);
            return;
        }
        if (have >= st.lastRecordingBytes()) {
            return;
        }
        resumeTriedId = rid;
        transferRecording(rid, local);
    }

    private Path localFor(final long recordingId, final String recordingName, final String startEpochMs) {
        return LocalRecordings.localFile(settings.recordingsDir(), connectionDir, recordingName, recordingId, startEpochMs);
    }

    private void transferRecording(final long recordingId, final String recordingName, final String startEpochMs) {
        transferRecording(recordingId, localFor(recordingId, recordingName, startEpochMs));
    }

    private void transferRecording(final long recordingId, final Path local) {
        final Agent c = agent;
        if (c == null) {
            return;
        }
        final Transfer old = transfer.current();
        if (old != null) {
            view.message("Transfer of " + old.file().getFileName() + " stopped at "
                    + Formats.fmtBytes(transfer.transferredBytes()) + ": recording #" + recordingId
                    + " started and the agent discarded the rest");
        }
        transfer.cancelCurrent();
        transfer.transferred(0);
        transfer.resetAgentRate();
        if (transfer.isStopping()) {
            transfer.defer(recordingId, local);
            view.message("Waiting for the previous transfer to stop before saving recording #" + recordingId);
            return;
        }
        startTransfer(c, recordingId, local);
    }

    private void startTransfer(final Agent c, final long recordingId, final Path local) {
        final long offset;
        try {
            offset = LocalRecordings.prepareResume(local);
        } catch (final IOException e) {
            view.message("Cannot prepare the destination directory: " + e);
            return;
        }
        transfer.transferred(offset);
        final Transfer[] self = new Transfer[1];
        final Transfer p = new Transfer(c, recordingId, local, offset, new Transfer.Listener() {
            @Override
            public void progress(final long bytes) {
                uiThread.post(() -> onTransferProgress(self[0], bytes));
            }

            @Override
            public void finished(final long bytes) {
                uiThread.post(() -> onTransferFinished(self[0], bytes));
            }

            @Override
            public void failed(final String message) {
                uiThread.post(() -> onTransferFailed(self[0], message));
            }

            @Override
            public void stopped() {
                uiThread.post(() -> onTransferStopped(self[0]));
            }
        });
        self[0] = p;
        transfer.started(p, scheduler.schedule(p));
        view.message("Saving recording #" + recordingId + " to " + local);
    }

    private void startDeferredTransfer() {
        if (disposed) {
            return;
        }
        final TransferState.Deferred p = transfer.takeDeferred();
        if (p != null) {
            transferRecording(p.recordingId(), p.file());
        }
    }

    private void onTransferStopped(final Transfer p) {
        if (transfer.clearStopping(p)) {
            startDeferredTransfer();
            return;
        }
        if (!transfer.clearCurrent(p) || disposed) {
            return;
        }
        transfer.resetAgentRate();
        view.message("Transfer of " + p.file().getFileName() + " stopped");
        view.recordingsChanged();
    }

    private void onTransferProgress(final Transfer p, final long bytes) {
        if (disposed || !transfer.isCurrent(p)) {
            return;
        }
        transfer.transferred(bytes);
        final EditorHandle editor = transfer.editor();
        if (editor == null || !editor.isOpen()) {
            transfer.editorOpened(view.openEditor(p.file()), nowMs.getAsLong());
            view.recordingsChanged();
            return;
        }
        final long now = nowMs.getAsLong();
        if (transfer.reloadDue(now) && !editor.isLoading()) {
            transfer.reloaded(now);
            editor.reload(true);
        }
    }

    private void onTransferFinished(final Transfer p, final long bytes) {
        transfer.clearStopping(p);
        final EditorHandle editor = transfer.editor();
        if (transfer.clearCurrent(p)) {
            transfer.resetAgentRate();
            scheduleFinalReload(editor);
        }
        if (disposed) {
            return;
        }
        view.message("Saved recording " + p.file().getFileName() + ", " + Formats.fmtBytes(bytes));
        view.recordingsChanged();
        startDeferredTransfer();
    }

    private void scheduleFinalReload(final EditorHandle editor) {
        if (disposed || editor == null || !editor.isOpen()) {
            return;
        }
        if (editor.isLoading()) {
            uiThread.postAfter(FINAL_RELOAD_RETRY_MS, () -> scheduleFinalReload(editor));
            return;
        }
        editor.reload(false);
    }

    private void onTransferFailed(final Transfer p, final String msg) {
        transfer.clearStopping(p);
        transfer.clearCurrent(p);
        if (disposed) {
            return;
        }
        transfer.resetAgentRate();
        final boolean superseded = msg != null
                && (msg.contains("unknown stream id") || msg.contains("unknown recording id"));
        view.message(superseded
                ? "Transfer of " + p.file().getFileName() + " stopped: a newer recording superseded it on the agent"
                : "Transfer of " + p.file().getFileName() + " failed: " + msg);
        view.recordingsChanged();
        startDeferredTransfer();
    }

    void dispose() {
        disposed = true;
        transfer.cancelCurrent();
        transfer.dropDeferred();
        releaseClient();
    }

    private void releaseClient() {
        final Agent c = agent;
        final ExecutorService bg = background;
        agent = null;
        background = null;
        statusInFlight.set(null);
        if (c != null && bg != null) {
            bg.execute(() -> closeQuietly(c));
            bg.shutdown();
        }
    }

    @SuppressWarnings("EmptyCatch")
    private static void closeQuietly(final Agent c) {
        try {
            c.close();
        } catch (final IOException ignored) {
        }
    }
}
