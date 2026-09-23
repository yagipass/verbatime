package io.github.yagipass.verbatime.jmc.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yagipass.verbatime.jmc.FakeUiThread;
import io.github.yagipass.verbatime.jmc.control.ControlPresenter.State;
import io.github.yagipass.verbatime.jmc.recordings.LocalRecordings;

final class ControlPresenterTest {

    private static final String TARGET = "localhost:7091";

    @TempDir
    Path recordings;

    final FakeAgent agent = new FakeAgent();

    final FakeUiThread uiThread = new FakeUiThread();

    final List<ManualExecutor> executors = new ArrayList<>();

    ManualExecutor bg;

    final SpyView view = new SpyView();

    final Map<String, String> saved = new HashMap<>();

    String initialRoots = "";

    IOException dialError;

    final Deque<Agent> reconnectAgents = new ArrayDeque<>();

    final List<Transfer> pulls = new ArrayList<>();

    final List<Transfer> cancelled = new ArrayList<>();

    final long[] now = { 1_700_000_000_000L };

    private ControlPresenter connection() {
        final ControlPresenter.Settings settings = new ControlPresenter.Settings() {
            @Override
            public String roots() {
                return initialRoots;
            }

            @Override
            public Path recordingsDir() {
                return recordings;
            }

            @Override
            public void save(final String target, final String roots) {
                saved.put("target", target);
                saved.put("roots", roots);
            }
        };
        return new ControlPresenter(view, uiThread, () -> {
            bg = new ManualExecutor();
            executors.add(bg);
            return bg;
        }, target -> {
            if (dialError != null) {
                throw dialError;
            }
            return reconnectAgents.isEmpty() ? agent : reconnectAgents.poll();
        }, settings, () -> now[0], p -> {
            pulls.add(p);
            return () -> cancelled.add(p);
        });
    }

    private ControlPresenter connected() {
        final ControlPresenter c = connection();
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        return c;
    }

    private static Map<String, String> idleWithRoot() {
        return Map.of("v", "4", "pid", "7", "state", "idle", "roots", "1", "root.0", "ok a.B::m");
    }

    private static Map<String, String> recording(final long id, final long startEpochMs, final long agentBytes) {
        final Map<String, String> m = new HashMap<>(Map.of("v", "4", "pid", "7", "state", "recording", "roots", "1",
                "root.0", "ok a.B::m", "recording.id", Long.toString(id), "recording.name", "run",
                "recording.startEpochMs", Long.toString(startEpochMs)));
        if (agentBytes >= 0) {
            m.put("recording.bytes", Long.toString(agentBytes));
        }
        return m;
    }

    private static Map<String, String> stopped(final long lastBytes) {
        return new HashMap<>(Map.of("v", "4", "pid", "7", "state", "idle", "roots", "1", "root.0", "ok a.B::m",
                "lastRecording.bytes", Long.toString(lastBytes)));
    }

    private static Map<String, String> stoppedAfter(final long id, final long startEpochMs, final long lastBytes) {
        final Map<String, String> m = stopped(lastBytes);
        m.put("lastRecording.id", Long.toString(id));
        m.put("lastRecording.name", "run");
        m.put("lastRecording.startEpochMs", Long.toString(startEpochMs));
        return m;
    }

    private Path localCopy(final long id, final int bytes) throws IOException {
        final Path local = LocalRecordings.localFile(recordings, LocalRecordings.fileSafe(TARGET), "run", id,
                Long.toString(now[0]));
        Files.createDirectories(local.getParent());
        Files.write(local, new byte[bytes]);
        return local;
    }

    private void poll() {
        uiThread.advance(ControlPresenter.POLL_MS);
        bg.runAll();
        uiThread.runPosted();
    }

    private void runCancelledAfterOneChunk(final Transfer p) {
        final NullProgressMonitor monitor = new NullProgressMonitor();
        agent.onRead = () -> monitor.setCanceled(true);
        agent.chunks.add(new byte[3]);
        p.run(monitor);
        agent.onRead = () -> {
        };
        uiThread.runPosted();
    }

    @Test
    void connectSavesTheTargetOnlyAfterTheAgentAnsweredStatus() {
        final ControlPresenter c = connection();
        c.connect(TARGET);
        assertEquals(State.CONNECTING, c.state());
        assertTrue(saved.isEmpty(), "a target that never answered is not remembered as the last good one");
        bg.runAll();
        uiThread.runPosted();
        assertEquals(State.CONNECTED, c.state());
        assertEquals(TARGET, saved.get("target"));
        assertEquals(State.CONNECTED, view.last().state());
        assertEquals("Connected to agent pid 7", view.messages.get(1));
    }

    @Test
    void connectionFailureReturnsToDisconnectedWithTheReasonAndSavesNothing() {
        dialError = new IOException("Connection refused");
        final ControlPresenter c = connection();
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        assertEquals(State.DISCONNECTED, c.state());
        assertEquals(State.DISCONNECTED, view.last().state());
        assertEquals("Connection failed: Connection refused", view.lastMessage());
        assertTrue(saved.isEmpty());
    }

    @Test
    void aStatusFailureWhileConnectedBecomesLostNotADisconnect() {
        final ControlPresenter c = connected();
        agent.statusError = new IOException("broken pipe");
        poll();
        assertEquals(State.LOST, c.state());
        assertEquals(State.LOST, view.last().state());
        assertEquals("Connection lost: broken pipe", view.lastMessage());
        bg.runAll();
        assertTrue(agent.closed, "the dead client is still closed so the connector thread goes away");
    }

    @Test
    void userDisconnectClearsRootsAndSaysDisconnectedWhileLostKeepsTheUrlAndSaysWhy() {
        agent.steady = idleWithRoot();
        final ControlPresenter c = connected();
        assertEquals(1, view.last().roots().size());
        c.disconnect();
        assertEquals(State.DISCONNECTED, c.state());
        assertTrue(view.last().roots().isEmpty());
        assertEquals("Disconnected", view.lastMessage());
        bg.runAll();
        assertTrue(agent.closed);
    }

    @Test
    void pollDoesNotOverlapWhileAStatusCallIsInFlight() {
        connected();
        uiThread.advance(ControlPresenter.POLL_MS);
        assertEquals(1, bg.deferred());
        uiThread.advance(ControlPresenter.POLL_MS);
        assertEquals(1, bg.deferred(), "a slow agent gets one status call at a time, never a pile-up");
        bg.runAll();
        uiThread.runPosted();
        uiThread.advance(ControlPresenter.POLL_MS);
        assertEquals(1, bg.deferred());
    }

    @Test
    void aLateReplyFromAPreviousClientIsIgnoredAfterDisconnect() {
        final ControlPresenter c = connected();
        uiThread.advance(ControlPresenter.POLL_MS);
        c.disconnect();
        bg.runAll();
        uiThread.runPosted();
        assertEquals(State.DISCONNECTED, c.state());
        assertEquals(State.DISCONNECTED, view.last().state(), "the stale status must not repaint a connected panel");
    }

    @Test
    void aDialThatNeverAnswersTimesOutAndTheNextConnectDoesNotQueueBehindIt() {
        final ControlPresenter c = connection();
        c.connect(TARGET);
        uiThread.advance(ControlPresenter.CONNECT_TIMEOUT_MS);
        assertEquals(State.DISCONNECTED, c.state());
        assertEquals(State.DISCONNECTED, view.last().state());
        assertEquals("Connection timed out after 30 s: " + TARGET, view.lastMessage());
        assertTrue(saved.isEmpty());
        final ManualExecutor stuck = executors.get(0);
        assertEquals(1, stuck.deferred(), "the dial is still blocked on its own thread");
        c.connect(TARGET);
        assertEquals(2, executors.size(), "a reconnect gets a fresh thread instead of waiting for the stuck dial");
        bg.runAll();
        uiThread.runPosted();
        assertEquals(State.CONNECTED, c.state());
        assertEquals(1, stuck.deferred());
    }

    @Test
    void aDialThatAnswersAfterTheTimeoutIsClosedInsteadOfTakingOverTheView() {
        final ControlPresenter c = connection();
        c.connect(TARGET);
        uiThread.advance(ControlPresenter.CONNECT_TIMEOUT_MS);
        final ManualExecutor stuck = executors.get(0);
        stuck.runAll();
        uiThread.runPosted();
        assertEquals(State.DISCONNECTED, c.state());
        assertEquals(State.DISCONNECTED, view.last().state());
        assertTrue(saved.isEmpty(), "a target that only answered after the timeout is not remembered");
        assertTrue(stuck.isShutdown(), "the abandoned thread is released once its dial came back");
        stuck.runAll();
        assertTrue(agent.closed, "the late connector is closed so it does not leak");
    }

    @Test
    void cancellingAConnectReturnsToDisconnectedAndTheLateDialIsDropped() {
        final ControlPresenter c = connection();
        c.connect(TARGET);
        c.cancelConnect();
        assertEquals(State.DISCONNECTED, c.state());
        assertEquals(State.DISCONNECTED, view.last().state());
        assertEquals("Connection cancelled", view.lastMessage());
        bg.runAll();
        uiThread.runPosted();
        assertEquals(State.DISCONNECTED, c.state());
        bg.runAll();
        assertTrue(agent.closed);
        uiThread.advance(ControlPresenter.CONNECT_TIMEOUT_MS);
        assertEquals("Connection cancelled", view.lastMessage(), "the timeout of a cancelled attempt stays silent");
    }

    @Test
    void aStatusCallThatNeverReturnsBecomesLostAndAReconnectDoesNotWaitForIt() {
        final ControlPresenter c = connected();
        uiThread.advance(ControlPresenter.POLL_MS);
        final ManualExecutor stuck = bg;
        assertEquals(1, stuck.deferred());
        now[0] += ControlPresenter.STALL_TIMEOUT_MS;
        uiThread.advance(ControlPresenter.POLL_MS);
        assertEquals(State.LOST, c.state());
        assertEquals(State.LOST, view.last().state());
        assertEquals("Connection lost: the agent has not answered for 15 s", view.lastMessage());
        assertTrue(stuck.isShutdown());

        final FakeAgent second = new FakeAgent();
        reconnectAgents.add(second);
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        assertEquals(State.CONNECTED, c.state());
        uiThread.advance(ControlPresenter.POLL_MS);
        assertEquals(1, bg.deferred(), "polling resumes although the old status call never came back");

        stuck.runAll();
        uiThread.runPosted();
        assertEquals(State.CONNECTED, c.state(), "the old call finally returning must not disturb the new session");
        assertTrue(agent.closed);
        assertFalse(second.closed);
    }

    @Test
    void disconnectingWhileACallIsStuckLetsTheUserReconnectAtOnce() {
        final ControlPresenter c = connected();
        c.startRecording();
        final ManualExecutor stuck = bg;
        assertEquals(1, stuck.deferred());
        c.disconnect();
        assertEquals(State.DISCONNECTED, c.state());
        assertTrue(stuck.isShutdown());
        assertEquals(2, stuck.deferred(), "the close waits behind the stuck call instead of blocking the view");

        final FakeAgent second = new FakeAgent();
        reconnectAgents.add(second);
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        assertEquals(State.CONNECTED, c.state());
        assertFalse(agent.closed);
        stuck.runAll();
        uiThread.runPosted();
        assertTrue(agent.closed);
        assertFalse(second.closed);
        assertEquals(State.CONNECTED, c.state());
    }

    @Test
    void recordingLocksRootsAndSwapsStartForStop() {
        agent.steady = recording(5, now[0], 0);
        connected();
        final ControlPresenter.ViewState p = view.last();
        assertTrue(p.recording());
        assertTrue(p.rootsLocked());
        assertFalse(p.canStart());
        assertTrue(p.canStop());
        assertEquals(1, pulls.size(), "a recording that is already running is transferred right away");
    }

    @Test
    void startNeedsARootSoAnEmptyAgentGetsAHintInsteadOfAButton() {
        connected();
        final ControlPresenter.ViewState p = view.last();
        assertFalse(p.canStart());
        assertEquals("Idle. Add a root to start recording", p.statusText());
    }

    @Test
    void startAndStopAreSentOnceAndTheirButtonsStayOffUntilAPollConfirms() {
        agent.steady = idleWithRoot();
        final ControlPresenter c = connected();
        assertTrue(view.last().canStart());
        c.startRecording();
        c.startRecording();
        assertFalse(view.last().canStart(), "Start goes off before the agent answers, so a double click cannot start two");
        bg.runAll();
        uiThread.runPosted();
        assertEquals(1, agent.starts);
        assertEquals("Started recording #1", view.lastMessage());
        assertFalse(view.last().canStart(), "Start stays off until a poll reports state=recording");
        agent.steady = recording(1, now[0], 0);
        poll();
        assertTrue(view.last().canStop());
        c.stopRecording();
        c.stopRecording();
        assertFalse(view.last().canStop(), "Stop goes off before the agent answers");
        bg.runAll();
        uiThread.runPosted();
        assertEquals(1, agent.stops);
        assertEquals("Stopped. Waiting for the transfer to complete…", view.lastMessage());
        assertFalse(view.last().canStop(), "Stop stays off until a poll reports the recording ended");
    }

    @Test
    void aStartOrStopTheAgentRefusesReenablesItsButtonAndSaysWhy() {
        agent.steady = idleWithRoot();
        final ControlPresenter c = connected();
        agent.startError = new IOException("output directory is not writable");
        c.startRecording();
        bg.runAll();
        uiThread.runPosted();
        assertEquals("Failed to start: output directory is not writable", view.lastMessage());
        assertTrue(view.last().canStart(), "the user can try again right away instead of waiting for a poll");
        agent.steady = recording(5, now[0], 0);
        poll();
        agent.stopError = new IOException("not recording");
        c.stopRecording();
        bg.runAll();
        uiThread.runPosted();
        assertEquals("Failed to stop: not recording", view.lastMessage());
        assertTrue(view.last().canStop());
    }

    @Test
    void savedRootsAreReappliedOnceOnlyWhenTheAgentIsIdleAndHasNone() {
        initialRoots = "a.B::m\nc.D::x\n";
        connected();
        bg.runAll();
        uiThread.runPosted();
        assertEquals(1, agent.replacedRoots.size());
        assertArrayEquals(new String[] { "a.B::m", "c.D::x" }, agent.replacedRoots.get(0));
        assertEquals("Re-applied 2 saved roots", view.messages.get(2));
        poll();
        poll();
        assertEquals(1, agent.replacedRoots.size(), "re-applying happens once per connection, not per poll");
    }

    @Test
    void addingARootPersistsTheJoinedListAndClearsTheSearchBox() {
        final ControlPresenter c = connected();
        c.addRoot("a.B::m");
        bg.runAll();
        uiThread.runPosted();
        assertArrayEquals(new String[] { "a.B::m" }, agent.replacedRoots.get(0));
        assertEquals("a.B::m", saved.get("roots"));
        assertEquals(1, view.rootAccepted);
        assertEquals("Applied 1 root", view.lastMessage());
    }

    @Test
    void aNewRecordingIdStartsOnePullAndRepeatedPollsDoNotStartAnother() {
        agent.steady = recording(5, now[0], 0);
        connected();
        poll();
        poll();
        assertEquals(1, pulls.size());
        assertEquals(5, pulls.get(0).recordingId());
    }

    @Test
    void resumesPullFromLocalFileSizeSoAReconnectDoesNotRedownload() throws IOException {
        final Path local = LocalRecordings.localFile(recordings, LocalRecordings.fileSafe(TARGET), "run", 5,
                Long.toString(now[0]));
        Files.createDirectories(local.getParent());
        Files.write(local, new byte[10]);
        agent.steady = recording(5, now[0], 0);
        connected();
        pulls.get(0).run(new NullProgressMonitor());
        assertEquals(List.of(10L), agent.openedAt);
        assertEquals(local, pulls.get(0).file());
    }

    @Test
    void recordingTextShowsElapsedPulledAndLagFromTheInjectedClock() {
        final long start = now[0];
        now[0] = start + 61_000;
        agent.steady = recording(5, start, 2L << 20);
        connected();
        assertEquals("Recording #5, 1:01 elapsed, 0 B transferred, 2.0 MB behind", view.last().statusText());
    }

    @Test
    void writeRateAveragesSuccessivePollsSoTheEstimateDoesNotJitter() {
        final long start = now[0];
        agent.statuses.add(recording(5, start, 0));
        agent.statuses.add(recording(5, start, 1L << 20));
        agent.statuses.add(recording(5, start, 2L << 20));
        agent.statuses.add(recording(5, start, 4L << 20));
        connected();
        now[0] += 1_000;
        poll();
        assertTrue(view.last().statusText().endsWith("1.0 MB behind, about 1 s"),
                "the connect-time status is the first sample, so one poll later there is a rate: "
                        + view.last().statusText());
        now[0] += 1_000;
        poll();
        assertTrue(view.last().statusText().endsWith("2.0 MB behind, about 2 s"), view.last().statusText());
        now[0] += 1_000;
        poll();
        assertTrue(view.last().statusText().endsWith("4.0 MB behind, about 3 s"),
                "the rate is the mean of the previous estimate and the last interval, 1.5 MB/s, so 4 MB reads as 3 s: "
                        + view.last().statusText());
    }

    @Test
    void writeRateResetsWhenANewPullStartsSoAnOldRecordingDoesNotSkewIt() {
        final long start = now[0];
        agent.statuses.add(recording(5, start, 0));
        agent.statuses.add(recording(5, start, 2L << 20));
        agent.statuses.add(recording(5, start, 4L << 20));
        agent.statuses.add(recording(6, start, 1L << 20));
        connected();
        now[0] += 1_000;
        poll();
        now[0] += 1_000;
        poll();
        assertTrue(view.last().statusText().contains("about"), view.last().statusText());
        now[0] += 1_000;
        poll();
        assertFalse(view.last().statusText().contains("about"),
                "the first sample of a new recording has no rate yet: " + view.last().statusText());
        runCancelledAfterOneChunk(pulls.get(0));
        assertEquals(2, pulls.size());
    }

    @Test
    void truncatedRecordingsAreReportedAsAMessageNotHidden() {
        agent.steady = Map.of("v", "4", "pid", "7", "state", "idle", "roots", "0", "lastRecording.truncated", "true");
        connected();
        assertTrue(view.lastMessage().contains("truncated"), view.lastMessage());
    }

    @Test
    void searchWaitsForTypingToPauseAndDropsResultsOfASupersededQuery() {
        final ControlPresenter c = connected();
        agent.searchResults = new String[] { "a.B::abc" };
        c.search("ab");
        c.search("abc");
        assertEquals(0, bg.deferred(), "nothing is sent while the user is still typing");
        uiThread.advance(ControlPresenter.SEARCH_DEBOUNCE_MS);
        assertEquals(1, bg.deferred());
        bg.runAll();
        uiThread.runPosted();
        assertEquals(List.of("abc"), agent.searchQueries);
        assertArrayEquals(new String[] { "a.B::abc" }, view.candidates.get(view.candidates.size() - 1));
    }

    @Test
    void searchIsSkippedBelowTwoCharsAndOnProtocolsBeforeFour() {
        final ControlPresenter c = connected();
        final int timers = uiThread.pendingTimers();
        c.search("a");
        assertEquals(0, view.candidates.get(view.candidates.size() - 1).length);
        assertEquals(timers, uiThread.pendingTimers(), "a one-letter query does not even start the debounce timer");

        agent.steady = Map.of("v", "3", "pid", "7", "state", "idle", "roots", "0");
        final ControlPresenter old = connected();
        old.search("abc");
        assertEquals(0, view.candidates.get(view.candidates.size() - 1).length);
        assertEquals(0, agent.searchQueries.size(), "an agent without searchMethods is never asked");
    }

    @Test
    void searchFailureTellsTheUserOnceInsteadOfSilentlyGoingDark() {
        final ControlPresenter c = connected();
        agent.searchError = new IOException("no such operation");
        c.search("abc");
        uiThread.advance(ControlPresenter.SEARCH_DEBOUNCE_MS);
        bg.runAll();
        uiThread.runPosted();
        assertEquals("Method search is not available on this agent: no such operation", view.lastMessage());
        final int messages = view.messages.size();
        c.search("abcd");
        uiThread.advance(ControlPresenter.SEARCH_DEBOUNCE_MS);
        assertEquals(0, bg.deferred());
        assertEquals(messages, view.messages.size());
    }

    @Test
    void firstPulledBytesOpenTheEditorAndRefreshTheRecordingsList() throws IOException {
        agent.steady = recording(5, now[0], 0);
        connected();
        agent.chunks.add(new byte[5]);
        pulls.get(0).run(new NullProgressMonitor());
        uiThread.runPosted();
        assertEquals(List.of(pulls.get(0).file()), view.opened);
        assertEquals(2, view.recordingsChanged, "once when the file appeared, once when the transfer ended");
        assertEquals(5, Files.size(pulls.get(0).file()));
    }

    @Test
    void aNewRecordingOpensItsOwnEditorEvenThoughThePreviousOneIsStillOpen() {
        agent.statuses.add(recording(5, now[0], 0));
        agent.statuses.add(recording(6, now[0], 0));
        connected();
        agent.chunks.add(new byte[1]);
        agent.readError = new IOException("readStream: connection reset");
        pulls.get(0).run(new NullProgressMonitor());
        uiThread.runPosted();
        assertEquals(List.of(pulls.get(0).file()), view.opened);
        assertTrue(view.editor.open, "the first recording's editor stays open");
        agent.readError = null;
        poll();
        assertEquals(2, pulls.size(), "recording #6 started, so it is transferred");
        agent.chunks.add(new byte[1]);
        pulls.get(1).run(new NullProgressMonitor());
        uiThread.runPosted();
        assertEquals(List.of(pulls.get(0).file(), pulls.get(1).file()), view.opened,
                "the second recording's file is opened, not shown in the first one's editor");
    }

    @Test
    void aRecordingThatSupersedesOneStillTransferringOpensItsOwnEditor() throws Exception {
        agent.statuses.add(recording(5, now[0], 0));
        agent.statuses.add(recording(6, now[0], 0));
        connected();
        final CountDownLatch parked = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        final NullProgressMonitor monitor = new NullProgressMonitor();
        final int[] reads = { 0 };
        agent.onRead = () -> {
            if (reads[0]++ == 1) {
                parked.countDown();
                try {
                    released.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        agent.chunks.add(new byte[1]);
        final Transfer first = pulls.get(0);
        final Thread job = new Thread(() -> first.run(monitor));
        job.start();
        assertTrue(parked.await(5, TimeUnit.SECONDS));
        uiThread.runPosted();
        assertEquals(List.of(first.file()), view.opened);
        poll();
        assertEquals(List.of(first), cancelled, "recording #6 started, so #5's transfer is cancelled");
        assertEquals(1, pulls.size(), "#6 is not transferred until #5's job has really returned");
        monitor.setCanceled(true);
        released.countDown();
        job.join(5_000);
        assertFalse(job.isAlive());
        agent.onRead = () -> {
        };
        uiThread.runPosted();
        assertEquals(2, pulls.size());
        agent.chunks.add(new byte[1]);
        pulls.get(1).run(new NullProgressMonitor());
        uiThread.runPosted();
        assertEquals(List.of(first.file(), pulls.get(1).file()), view.opened,
                "the superseding recording's file is opened, not shown in the abandoned one's editor");
    }

    @Test
    void liveReloadIsThrottledToOnePerTwoSecondsAndSkippedWhileLoading() {
        agent.steady = recording(5, now[0], 0);
        connected();
        agent.chunks.add(new byte[1]);
        agent.chunks.add(new byte[1]);
        agent.chunks.add(new byte[1]);
        agent.chunks.add(new byte[1]);
        pulls.get(0).run(new NullProgressMonitor());
        uiThread.runOne();
        assertEquals(1, view.opened.size());
        now[0] += 1_000;
        uiThread.runOne();
        assertTrue(view.editor.reloads.isEmpty(), "one second after opening is too soon");
        now[0] += 1_000;
        uiThread.runOne();
        assertEquals(List.of(true), view.editor.reloads);
        now[0] += 2_000;
        view.editor.loading = true;
        uiThread.runOne();
        assertEquals(List.of(true), view.editor.reloads, "a reload is never queued behind one still running");
    }

    @Test
    void stopWaitsForTheTransferThenReloadsTheEditorOnceItIsIdle() {
        agent.steady = recording(5, now[0], 0);
        connected();
        agent.chunks.add(new byte[1]);
        view.editor.loading = true;
        pulls.get(0).run(new NullProgressMonitor());
        uiThread.runPosted();
        assertTrue(view.lastMessage().startsWith("Saved recording "), view.lastMessage());
        assertTrue(view.editor.reloads.isEmpty());
        uiThread.advance(ControlPresenter.FINAL_RELOAD_RETRY_MS);
        assertTrue(view.editor.reloads.isEmpty(), "still loading: try again later");
        view.editor.loading = false;
        uiThread.advance(ControlPresenter.FINAL_RELOAD_RETRY_MS);
        assertEquals(List.of(false), view.editor.reloads, "the final reload is a full, non-live one");
    }

    @Test
    void aSupersededStreamIsReportedAsSupersededNotAsAFailure() {
        agent.steady = recording(5, now[0], 0);
        connected();
        agent.readError = new IOException("readStream: unknown stream id 100");
        pulls.get(0).run(new NullProgressMonitor());
        uiThread.runPosted();
        assertTrue(view.lastMessage().contains("superseded"), view.lastMessage());
        assertFalse(view.lastMessage().contains("failed"));
    }

    @Test
    void disposeCancelsThePullAndClosesTheClient() {
        agent.steady = recording(5, now[0], 0);
        final ControlPresenter c = connected();
        c.dispose();
        assertEquals(pulls, cancelled);
        bg.runAll();
        assertTrue(agent.closed);
    }

    @Test
    void startStaysOffWhileTheStoppedRecordingIsStillTransferring() {
        agent.steady = recording(5, now[0], 0);
        connected();
        agent.steady = stopped(500);
        poll();
        ControlPresenter.ViewState p = view.last();
        assertTrue(p.statusText().startsWith("Transferring #5"), p.statusText());
        assertFalse(p.canStart(), "a new start would make the agent discard the undelivered rest of #5");
        assertFalse(p.canStop());
        assertTrue(p.transferring());
        pulls.get(0).run(new NullProgressMonitor());
        uiThread.runPosted();
        poll();
        p = view.last();
        assertEquals("Idle", p.statusText());
        assertTrue(p.canStart(), "once the transfer is complete there is nothing left to lose");
    }

    @Test
    void aRecordingStartedElsewhereDuringTheTransferIsReportedNotSilentlyDropped() {
        agent.steady = recording(5, now[0], 0);
        connected();
        agent.steady = stopped(500);
        poll();
        final Transfer first = pulls.get(0);
        agent.steady = recording(6, now[0], 0);
        poll();
        assertEquals(List.of(first), cancelled);
        runCancelledAfterOneChunk(first);
        assertEquals(2, pulls.size());
        final String stoppedMsg = view.messages.stream().filter(m -> m.startsWith("Transfer of ")).reduce((a, b) -> b)
                .orElse("");
        assertTrue(stoppedMsg.contains("stopped at") && stoppedMsg.contains("recording #6"), stoppedMsg);
    }

    @Test
    void aReconnectFetchesTheRestOfAStoppedRecordingTheAgentStillHolds() throws IOException {
        final Path local = localCopy(5, 10);
        agent.steady = stoppedAfter(5, now[0], 500);
        connected();
        assertEquals(1, pulls.size(), "the agent keeps the spool until it is delivered, so the tail is still there");
        assertEquals(5, pulls.get(0).recordingId());
        assertEquals(local, pulls.get(0).file());
        final ControlPresenter.ViewState p = view.last();
        assertTrue(p.statusText().startsWith("Transferring #5"), p.statusText());
        assertFalse(p.canStart(), "Start would make the agent discard what the local copy is still missing");
        pulls.get(0).run(new NullProgressMonitor());
        assertEquals(List.of(10L), agent.openedAt, "only the missing tail is fetched");
    }

    @Test
    void aStopThatFellBetweenTwoPollsIsStillFetched() {
        connected();
        agent.steady = stoppedAfter(5, now[0], 300);
        poll();
        assertEquals(1, pulls.size(), "the client never saw state=recording, but the spool is complete and waiting");
        pulls.get(0).run(new NullProgressMonitor());
        assertEquals(List.of(0L), agent.openedAt);
    }

    @Test
    void aFullyDeliveredLastRecordingIsNotFetchedAgain() throws IOException {
        localCopy(5, 500);
        agent.steady = stoppedAfter(5, now[0], 500);
        connected();
        poll();
        assertTrue(pulls.isEmpty(), "nothing is missing locally, so there is nothing to fetch");
        assertEquals("Idle", view.last().statusText());
        assertTrue(view.last().canStart());
    }

    @Test
    void aFailedResumeIsNotRetriedEveryPollButAReconnectTriesAgain() throws IOException {
        localCopy(5, 10);
        agent.steady = stoppedAfter(5, now[0], 500);
        agent.readError = new IOException("cannot read the spool");
        final ControlPresenter c = connected();
        pulls.get(0).run(new NullProgressMonitor());
        uiThread.runPosted();
        assertTrue(view.lastMessage().contains("failed"), view.lastMessage());
        poll();
        poll();
        assertEquals(1, pulls.size(), "a persistent failure must not turn into one transfer per second");
        c.disconnect();
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        assertEquals(2, pulls.size(), "a fresh connection is allowed one more attempt");
    }

    @Test
    void anAgentThatDoesNotReportTheStartTimeIsNotResumed() throws IOException {
        localCopy(5, 10);
        final Map<String, String> old = stopped(500);
        old.put("lastRecording.id", "5");
        old.put("lastRecording.name", "run");
        agent.steady = old;
        connected();
        poll();
        assertTrue(pulls.isEmpty(), "without the start time the local file could belong to a previous agent process");
    }

    @Test
    void aReconnectDuringATransferWaitsForTheOldPullToStopBeforeMeasuringTheFile() throws IOException {
        agent.steady = recording(5, now[0], 0);
        final ControlPresenter c = connected();
        final Transfer first = pulls.get(0);
        c.disconnect();
        assertEquals(List.of(first), cancelled);
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        assertEquals(1, pulls.size(), "the old job may still be inside readStream and append once more on return");
        assertTrue(view.lastMessage().startsWith("Waiting for the previous transfer"), view.lastMessage());
        poll();
        assertEquals(1, pulls.size(), "polling while waiting does not queue more pulls");
        runCancelledAfterOneChunk(first);
        assertEquals(2, pulls.size());
        assertEquals(3, Files.size(first.file()));
        pulls.get(1).run(new NullProgressMonitor());
        assertEquals(List.of(0L, 3L), agent.openedAt,
                "the resumed stream starts after the chunk the old job appended, so nothing is written twice");
    }

    @Test
    void waitingForTheOldPullCountsAsTransferringSoStartCannotDiscardTheRest() {
        agent.steady = recording(5, now[0], 0);
        final ControlPresenter c = connected();
        c.disconnect();
        agent.steady = stoppedAfter(5, now[0], 500);
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        final ControlPresenter.ViewState p = view.last();
        assertTrue(p.transferring());
        assertFalse(p.canStart(), "a new start would make the agent discard the undelivered rest of #5");
        assertTrue(p.statusText().startsWith("Stopping the previous transfer before saving #5"), p.statusText());
    }

    @Test
    void aDisconnectWhileWaitingDropsThePendingAttach() {
        agent.steady = recording(5, now[0], 0);
        final ControlPresenter c = connected();
        final Transfer first = pulls.get(0);
        c.disconnect();
        c.connect(TARGET);
        bg.runAll();
        uiThread.runPosted();
        c.disconnect();
        runCancelledAfterOneChunk(first);
        assertEquals(1, pulls.size(), "there is no connection to transfer over, so the next connect decides what to fetch");
    }

    @Test
    void aPullCancelledFromOutsideIsResumedOnTheNextPoll() {
        agent.steady = recording(5, now[0], 0);
        connected();
        final Transfer first = pulls.get(0);
        runCancelledAfterOneChunk(first);
        assertTrue(cancelled.isEmpty(), "the Progress view cancelled it, not the connection");
        assertTrue(view.lastMessage().startsWith("Transfer of ") && view.lastMessage().endsWith(" stopped"), view.lastMessage());
        poll();
        assertEquals(2, pulls.size(), "the recording is still running, so the transfer picks up from the file size");
        assertEquals(5, pulls.get(1).recordingId());
    }

    @Test
    void aPendingRedrawKeepsStartOffWhileTransferring() {
        final List<RootEntry> roots = List.of(new RootEntry("a.B::m", true));
        final ControlPresenter.ViewState p = ControlPresenter.ViewState.connected("x", false, roots, "", "Transferring", false,
                false, true);
        assertTrue(p.withPending(false, false).withPending(false, false).transferring());
        assertFalse(p.withPending(false, false).canStart(), "renderPending must not re-enable Start mid-transfer");
        assertTrue(ControlPresenter.ViewState.connected("x", false, roots, "", "Idle", false, false, false).canStart(),
                "the same panel without a transfer in flight is startable, so the flag is what turned it off");
    }
}
