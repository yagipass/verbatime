package io.github.yagipass.verbatime.jmc.control;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.UiThread;
import io.github.yagipass.verbatime.jmc.recordings.LocalRecordings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class ControlPresenter {

  enum State {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOST
  }

  record ViewState(
      State state,
      @Nullable String connectionText,
      boolean recording,
      List<RootEntry> roots,
      String instrumentedText,
      String statusText,
      boolean startPending,
      boolean stopPending,
      boolean transferring) {

    static ViewState disconnected(State state) {
      return new ViewState(state, null, false, List.of(), "", "Not connected", false, false, false);
    }

    static ViewState connected(
        String connectionText,
        boolean recording,
        List<RootEntry> roots,
        String instrumentedText,
        String statusText,
        boolean startPending,
        boolean stopPending,
        boolean transferring) {
      return new ViewState(
          State.CONNECTED,
          connectionText,
          recording,
          roots,
          instrumentedText,
          statusText,
          startPending,
          stopPending,
          transferring);
    }

    ViewState withPending(boolean startPending, boolean stopPending) {
      return new ViewState(
          state,
          connectionText,
          recording,
          roots,
          instrumentedText,
          statusText,
          startPending,
          stopPending,
          transferring);
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

    @Nullable EditorHandle openEditor(Path file);

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

  private record Connection(Agent agent, ExecutorService background, String target, String dir) {}

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

  private final AtomicInteger attempt = new AtomicInteger();

  private volatile @Nullable Connection connection;

  private String savedRoots;

  private List<RootEntry> appliedRoots = List.of();

  private boolean rootsReapplied;

  private boolean searchSupported;

  private int searchGeneration;

  private boolean startPending;

  private boolean stopPending;

  private long resumeTriedId;

  private final TransferState transfer = new TransferState();

  private final AtomicReference<@Nullable Agent> statusInFlight = new AtomicReference<>();

  private long pollStartedMs;

  private boolean pollChainActive;

  private volatile boolean disposed;

  ControlPresenter(
      View view,
      UiThread uiThread,
      Supplier<ExecutorService> newExecutor,
      Agent.Dialer dialer,
      Settings settings,
      LongSupplier nowMs,
      TransferScheduler scheduler) {
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

  void connect(String target) {
    if (connection != null || state == State.CONNECTING || target.isEmpty()) {
      return;
    }
    state = State.CONNECTING;
    int a = attempt.incrementAndGet();
    ExecutorService bg = newExecutor.get();
    view.render(ViewState.disconnected(State.CONNECTING));
    view.message("Connecting: " + target);
    uiThread.postAfter(
        CONNECT_TIMEOUT_MS,
        () -> {
          if (isAttempt(a)) {
            abandonAttempt(
                "Connection timed out after " + CONNECT_TIMEOUT_MS / 1000 + " s: " + target);
          }
        });
    bg.execute(
        () -> {
          @Var Agent c = null;
          try {
            c = dialer.dial(target);
            Map<String, String> st = c.status();
            if (!isAttempt(a)) {
              closeQuietly(c);
              bg.shutdown();
              return;
            }
            Agent ok = c;
            uiThread.post(() -> connected(a, target, bg, ok, st));
          } catch (Exception e) {
            if (c != null) {
              closeQuietly(c);
            }
            bg.shutdown();
            String msg = String.valueOf(e.getMessage());
            uiThread.post(() -> isAttempt(a), () -> abandonAttempt("Connection failed: " + msg));
          }
        });
  }

  void cancelConnect() {
    if (state == State.CONNECTING) {
      abandonAttempt("Connection cancelled");
    }
  }

  void disconnect() {
    teardown(State.DISCONNECTED, "Disconnected");
  }

  void search(String query) {
    searchGeneration++;
    int gen = searchGeneration;
    String q = query.trim();
    if (q.length() < 2 || connection == null || !searchSupported) {
      view.rootCandidates(new String[0]);
      return;
    }
    uiThread.postAfter(
        SEARCH_DEBOUNCE_MS,
        () -> {
          if (gen == searchGeneration) {
            runSearch(gen, q);
          }
        });
  }

  void addRoot(String spec) {
    String[] arr = RootSpecs.with(appliedRoots, spec);
    replaceRoots(
        arr,
        true,
        "Applied " + Formats.plural(arr.length, "root"),
        "Failed to apply roots: ",
        view::rootAccepted);
  }

  void removeRoot(String spec) {
    String[] arr = RootSpecs.without(appliedRoots, spec);
    replaceRoots(
        arr,
        true,
        "Applied " + Formats.plural(arr.length, "root"),
        "Failed to apply roots: ",
        () -> {});
  }

  void startRecording() {
    Connection conn = connection;
    if (conn == null || startPending) {
      return;
    }
    startPending = true;
    renderPending();
    call(
        conn,
        conn.agent()::startRecording,
        id -> view.message("Started recording #" + id),
        msg -> {
          startPending = false;
          renderPending();
          view.message("Failed to start: " + msg);
        });
  }

  void stopRecording() {
    Connection conn = connection;
    if (conn == null || stopPending) {
      return;
    }
    stopPending = true;
    renderPending();
    call(
        conn,
        () -> {
          conn.agent().stopRecording();
          return null;
        },
        v -> view.message("Stopped. Waiting for the transfer to complete…"),
        msg -> {
          stopPending = false;
          renderPending();
          view.message("Failed to stop: " + msg);
        });
  }

  void dispose() {
    disposed = true;
    transfer.cancelCurrent();
    transfer.dropDeferred();
    releaseClient();
  }

  @SuppressWarnings("EmptyCatch")
  private static void closeQuietly(Agent c) {
    try {
      c.close();
    } catch (IOException ignored) {
    }
  }

  private boolean isAttempt(int a) {
    return !disposed && state == State.CONNECTING && a == attempt.get();
  }

  private void abandonAttempt(String message) {
    state = State.DISCONNECTED;
    view.render(ViewState.disconnected(State.DISCONNECTED));
    view.message(message);
  }

  private void connected(
      int a, String target, ExecutorService bg, Agent c, Map<String, String> st) {
    if (!isAttempt(a)) {
      bg.execute(() -> closeQuietly(c));
      bg.shutdown();
      return;
    }
    Connection conn = new Connection(c, bg, target, LocalRecordings.fileSafe(target));
    connection = conn;
    settings.save(target, savedRoots);
    state = State.CONNECTED;
    AgentStatus status = AgentStatus.parse(st);
    searchSupported = status.protocol() >= SEARCH_MIN_PROTOCOL;
    view.message("Connected to agent pid " + status.pid());
    apply(conn, status);
    startPolling();
  }

  private <T> void call(
      Connection conn, Callable<T> op, Consumer<T> onOk, Consumer<String> onFail) {
    if (!isCurrent(conn)) {
      return;
    }
    conn.background()
        .execute(
            () -> {
              T result;
              try {
                result = op.call();
              } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                uiThread.post(() -> isCurrent(conn), () -> onFail.accept(msg));
                return;
              }
              uiThread.post(() -> isCurrent(conn), () -> onOk.accept(result));
            });
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isCurrent(Connection conn) {
    return connection == conn;
  }

  private void lost(String msg) {
    teardown(State.LOST, "Connection lost: " + msg);
  }

  private void teardown(State next, String message) {
    transfer.cancelCurrent();
    transfer.dropDeferred();
    releaseClient();
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
    Connection conn = connection;
    if (disposed || conn == null) {
      pollChainActive = false;
      return;
    }
    Agent c = conn.agent();
    long now = nowMs.getAsLong();
    if (statusInFlight.get() == c && now - pollStartedMs >= STALL_TIMEOUT_MS) {
      lost("the agent has not answered for " + STALL_TIMEOUT_MS / 1000 + " s");
      pollChainActive = false;
      return;
    }
    if (statusInFlight.compareAndSet(null, c)) {
      pollStartedMs = now;
      call(
          conn,
          () -> {
            try {
              return c.status();
            } finally {
              statusInFlight.compareAndSet(c, null);
            }
          },
          st -> apply(conn, AgentStatus.parse(st)),
          this::lost);
    }
    uiThread.postAfter(POLL_MS, this::pollTick);
  }

  private void apply(Connection conn, AgentStatus st) {
    appliedRoots = st.roots();
    if (st.recording()) {
      startPending = false;
      long rid = st.recordingId();
      if (rid > 0 && transfer.transferringId() != rid) {
        transferRecording(
            rid,
            localFor(conn, rid, st.recordingName(), Long.toString(st.recordingStartEpochMs())));
      }
    } else {
      stopPending = false;
      resumeUndelivered(conn, st);
    }
    lastState = viewStateFor(conn, st);
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

  private ViewState viewStateFor(Connection conn, AgentStatus st) {
    String countText = ControlTexts.countText(st.instrumentedClasses(), st.instrumentedMethods());
    boolean transferring = !st.recording() && transfer.isActive();
    String recordingText;
    if (st.recording()) {
      long now = nowMs.getAsLong();
      long agentBytes = st.recordingBytes();
      double rate = agentBytes < 0 ? 0 : transfer.sampleAgentRate(now, agentBytes);
      recordingText =
          ControlTexts.recordingText(
              st.recordingId(),
              now - st.recordingStartEpochMs(),
              transfer.transferredBytes(),
              agentBytes,
              rate);
    } else if (transfer.deferred() != null) {
      recordingText = ControlTexts.waitingText(transfer.deferred().recordingId());
    } else if (transferring) {
      recordingText =
          ControlTexts.transferringText(
              Objects.requireNonNull(transfer.current()).recordingId(),
              transfer.transferredBytes(),
              st.lastRecordingBytes());
    } else {
      recordingText = ControlTexts.idleText(!st.roots().isEmpty());
    }
    return ViewState.connected(
        ControlTexts.connectionText(conn.target(), st.pid()),
        st.recording(),
        st.roots(),
        countText,
        recordingText,
        startPending,
        stopPending,
        transferring);
  }

  private void runSearch(int gen, String query) {
    Connection conn = connection;
    if (conn == null) {
      return;
    }
    call(
        conn,
        () -> conn.agent().searchMethods(query, SEARCH_MAX_RESULTS),
        found -> {
          if (gen == searchGeneration) {
            view.rootCandidates(found == null ? new String[0] : found);
          }
        },
        msg -> {
          if (searchSupported) {
            searchSupported = false;
            view.rootCandidates(new String[0]);
            view.message("Method search is not available on this agent: " + msg);
          }
        });
  }

  private void reapplySavedRoots() {
    String[] arr =
        savedRoots.lines().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    if (arr.length == 0) {
      return;
    }
    replaceRoots(
        arr,
        false,
        "Re-applied " + Formats.plural(arr.length, "saved root"),
        "Could not re-apply the saved roots: ",
        () -> {});
  }

  private void replaceRoots(
      String[] arr, boolean remember, String okMessage, String failPrefix, Runnable onSuccess) {
    Connection conn = connection;
    if (conn == null) {
      return;
    }
    call(
        conn,
        () -> {
          conn.agent().replaceRoots(arr);
          return conn.agent().status();
        },
        st -> {
          if (remember) {
            savedRoots = String.join("\n", arr);
            settings.save(conn.target(), savedRoots);
          }
          onSuccess.run();
          view.message(okMessage);
          apply(conn, AgentStatus.parse(st));
        },
        msg -> view.message(failPrefix + msg));
  }

  private void renderPending() {
    lastState = lastState.withPending(startPending, stopPending);
    view.render(lastState);
  }

  private void resumeUndelivered(Connection conn, AgentStatus st) {
    long rid = st.lastRecordingId();
    if (transfer.transferringId() > 0
        || rid <= 0
        || rid == resumeTriedId
        || st.lastRecordingStartEpochMs() <= 0
        || st.lastRecordingBytes() < 0) {
      return;
    }
    Path local =
        localFor(conn, rid, st.lastRecordingName(), Long.toString(st.lastRecordingStartEpochMs()));
    long have;
    try {
      have = Files.exists(local) ? Files.size(local) : 0;
    } catch (IOException e) {
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

  private Path localFor(
      Connection conn, long recordingId, String recordingName, String startEpochMs) {
    return LocalRecordings.localFile(
        settings.recordingsDir(), conn.dir(), recordingName, recordingId, startEpochMs);
  }

  private void transferRecording(long recordingId, Path local) {
    Connection conn = connection;
    if (conn == null) {
      return;
    }
    Transfer old = transfer.current();
    if (old != null) {
      view.message(
          "Transfer of "
              + old.file().getFileName()
              + " stopped at "
              + Formats.fmtBytes(transfer.transferredBytes())
              + ": recording #"
              + recordingId
              + " started and the agent discarded the rest");
    }
    transfer.cancelCurrent();
    transfer.transferred(0);
    transfer.resetAgentRate();
    if (transfer.isStopping()) {
      transfer.defer(recordingId, local);
      view.message(
          "Waiting for the previous transfer to stop before saving recording #" + recordingId);
      return;
    }
    startTransfer(conn.agent(), recordingId, local);
  }

  private void startTransfer(Agent c, long recordingId, Path local) {
    long offset;
    try {
      offset = LocalRecordings.prepareResume(local);
    } catch (IOException e) {
      view.message("Cannot prepare the destination directory: " + e);
      return;
    }
    transfer.transferred(offset);
    Transfer[] self = new Transfer[1];
    Transfer p =
        new Transfer(
            c,
            recordingId,
            local,
            offset,
            new Transfer.Listener() {
              @Override
              public void progress(long bytes) {
                uiThread.post(() -> onTransferProgress(self[0], bytes));
              }

              @Override
              public void finished(long bytes) {
                uiThread.post(() -> onTransferFinished(self[0], bytes));
              }

              @Override
              public void failed(String message) {
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
    TransferState.Deferred p = transfer.takeDeferred();
    if (p != null) {
      transferRecording(p.recordingId(), p.file());
    }
  }

  private void onTransferStopped(Transfer p) {
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

  private void onTransferProgress(Transfer p, long bytes) {
    if (disposed || !transfer.isCurrent(p)) {
      return;
    }
    transfer.transferred(bytes);
    EditorHandle editor = transfer.editor();
    if (editor == null || !editor.isOpen()) {
      transfer.editorOpened(view.openEditor(p.file()), nowMs.getAsLong());
      view.recordingsChanged();
      return;
    }
    long now = nowMs.getAsLong();
    if (transfer.reloadDue(now) && !editor.isLoading()) {
      transfer.reloaded(now);
      editor.reload(true);
    }
  }

  private void onTransferFinished(Transfer p, long bytes) {
    transfer.clearStopping(p);
    EditorHandle editor = transfer.editor();
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

  private void scheduleFinalReload(@Nullable EditorHandle editor) {
    if (disposed || editor == null || !editor.isOpen()) {
      return;
    }
    if (editor.isLoading()) {
      uiThread.postAfter(FINAL_RELOAD_RETRY_MS, () -> scheduleFinalReload(editor));
      return;
    }
    editor.reload(false);
  }

  private void onTransferFailed(Transfer p, String msg) {
    transfer.clearStopping(p);
    transfer.clearCurrent(p);
    if (disposed) {
      return;
    }
    transfer.resetAgentRate();
    boolean superseded =
        msg != null && (msg.contains("unknown stream id") || msg.contains("unknown recording id"));
    view.message(
        superseded
            ? "Transfer of "
                + p.file().getFileName()
                + " stopped: a newer recording superseded it on the agent"
            : "Transfer of " + p.file().getFileName() + " failed: " + msg);
    view.recordingsChanged();
    startDeferredTransfer();
  }

  private void releaseClient() {
    Connection conn = connection;
    connection = null;
    statusInFlight.set(null);
    if (conn != null) {
      conn.background().execute(() -> closeQuietly(conn.agent()));
      conn.background().shutdown();
    }
  }
}
