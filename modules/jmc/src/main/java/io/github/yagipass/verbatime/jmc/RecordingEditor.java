package io.github.yagipass.verbatime.jmc;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;

import io.github.yagipass.verbatime.jmc.index.MappedTrace;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.MatchSearch;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor.Window;

public final class RecordingEditor extends EditorPart {

    public interface Listener {
        void selectionChanged();

        void traceChanged();
    }

    private static final AtomicBoolean UNMAP_WARNED = new AtomicBoolean();

    private final Function<Display, UiThread> uiThreads;

    private Path file;

    private Composite container;

    private UiThread ui;

    private ViewerBridge bridge;

    private Label statusLabel;

    private volatile TraceSnapshot trace;

    private TraceIndexer indexer;

    private Job loadJob;

    private ExecutorService queryWorker;

    private volatile ViewerJson.SentNames sentNames = new ViewerJson.SentNames();

    private ViewerJson.SentSessions sessionCursor = new ViewerJson.SentSessions();

    private volatile boolean loadRunning;

    private BitSet matchedMethodIds = new BitSet();

    private final ListenerList<Listener> listeners = new ListenerList<>();

    private SelectedCall selection;

    private long selectionSeq;

    public RecordingEditor() {
        this(UiThread::of);
    }

    private RecordingEditor(final Function<Display, UiThread> uiThreads) {
        this.uiThreads = uiThreads;
    }

    @Override
    public void init(final IEditorSite site, final IEditorInput input) throws PartInitException {
        final IPathEditorInput pathInput = input instanceof final IPathEditorInput p ? p
                : input.getAdapter(IPathEditorInput.class);
        if (pathInput == null || pathInput.getPath() == null) {
            throw new PartInitException("Cannot open " + input.getName() + ": no file path in editor input");
        }
        file = pathInput.getPath().toFile().toPath();
        setSite(site);
        setInput(input);
        setPartName(file.getFileName().toString());
    }

    @Override
    public void createPartControl(final Composite parent) {
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new FillLayout());
        ui = uiThreads.apply(container.getDisplay());
        bridge = new ViewerBridge(ui, new PageHost());
        ui.post(() -> VerbatimePerspective.show(getSite().getWorkbenchWindow()));
        if (!MappedTrace.unmapSupported() && UNMAP_WARNED.compareAndSet(false, true)) {
            ILog.get().warn("Verbatime cannot unmap recordings eagerly on this JVM, so "
                    + "a closed recording stays mapped until garbage collection, which blocks deleting it on Windows");
        }
        queryWorker = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "vbtm-query-worker");
            t.setDaemon(true);
            return t;
        });
        startLoad(false);
    }

    private void startLoad(final boolean live) {
        if (loadRunning) {
            return;
        }
        loadRunning = true;

        final boolean inPlace = bridge.isOpen();
        if (!inPlace) {
            closeViewerAndShow("Loading: " + file.getFileName() + " …");
        }
        final long gen = bridge.generation();
        final ViewerJson.SentNames names = inPlace ? sentNames.copy() : new ViewerJson.SentNames();
        final ViewerJson.SentSessions cursor = inPlace ? sessionCursor.copy() : new ViewerJson.SentSessions();

        if (indexer == null) {
            indexer = TraceIndexer.open(file, TraceIndexer.DEFAULT_OVERVIEW_BUDGET);
        }
        final TraceIndexer ix = indexer;
        final boolean[] applied = { false };
        final Job job = Job.create("Loading recording: " + file.getFileName(), monitor -> {
            monitor.beginTask(file.getFileName().toString(), ProgressMonitors.TICKS);
            TraceSnapshot data = null;
            boolean handedOff = false;
            try {
                ix.advance(ProgressMonitors.of(monitor));
                data = ix.snapshot();
                final TraceSnapshot snapshot = data;
                final String payload = inPlace ? ViewerJson.metaJson(snapshot, names, cursor)
                        : ViewerHtml.page(snapshot, names, cursor);
                applied[0] = true;
                handedOff = true;
                bridge.postIfCurrent(gen, () -> {
                    final TraceSnapshot prev = trace;
                    trace = snapshot;
                    sessionCursor = cursor;
                    if (inPlace) {
                        loadRunning = false;
                        final ViewerJson.SentNames merged = sentNames.copy();
                        merged.or(names);
                        if (cursor.lastReset()) {
                            bridge.dropPendingReplies();
                            sentNames = names;
                        } else {
                            sentNames = merged;
                        }
                        bridge.update(payload, live);
                    } else {
                        sentNames = names;
                        showBrowser(payload);
                        selectionSeq++;
                        selection = null;
                    }
                    fireTraceChanged();
                    if (prev != null) {
                        queryWorker.execute(prev::release);
                    }
                }, snapshot::release);
                return Status.OK_STATUS;
            } catch (final TraceIndexer.CancelledException c) {
                if (!inPlace) {
                    bridge.postIfCurrent(gen, () -> closeViewerAndShow("Loading cancelled. Reopen the editor to retry."));
                }
                return Status.CANCEL_STATUS;
            } catch (final TraceIndexer.NotTraceFormatException e) {
                final String msg = e.getMessage();
                bridge.postIfCurrent(gen, () -> closeViewerAndShow(msg));
                return Status.OK_STATUS;
            } catch (final Exception e) {
                final String msg = "Failed to load: " + e;
                bridge.postIfCurrent(gen, () -> {
                    if (indexer == ix) {
                        indexer = null;
                        ix.close();
                    }
                    closeViewerAndShow(msg);
                });
                return Status.OK_STATUS;
            } finally {
                if (data != null && !handedOff) {
                    data.release();
                }
                monitor.done();
                if (!applied[0]) {
                    loadRunning = false;
                }
            }
        });

        job.setUser(!inPlace);
        loadJob = job;
        job.schedule();
    }

    public boolean isLoading() {
        return loadRunning;
    }

    public boolean isDisposed() {
        return container == null || container.isDisposed();
    }

    public void reload(final boolean live) {
        if (!isDisposed() && !loadRunning) {
            startLoad(live);
        }
    }

    public TraceSnapshot trace() {
        return trace;
    }

    public SelectedCall selection() {
        return selection;
    }

    public void addListener(final Listener l) {
        listeners.add(l);
    }

    public void removeListener(final Listener l) {
        listeners.remove(l);
    }

    public void zoomTo(final SelectedCall f) {
        if (f != null && trace != null) {
            bridge.zoomTo(f);
        }
    }

    public void searchFor(final int methodId) {
        final TraceSnapshot data = trace;
        if (data != null) {
            bridge.searchFor(methodId, data.methodName(methodId));
        }
    }

    public void openExportDialog() {
        final TraceSnapshot data = trace;
        if (data == null || isDisposed()) {
            return;
        }
        data.buffer.retain();
        boolean scheduled = false;
        try {
            scheduled = SessionExportDialog.openAndSchedule(getSite().getShell(), data, selection);
        } finally {
            if (!scheduled) {
                data.buffer.release();
            }
        }
    }

    private void closeViewerAndShow(final String text) {
        bridge.close();
        if (statusLabel == null || statusLabel.isDisposed()) {
            statusLabel = new Label(container, SWT.WRAP);
        }
        statusLabel.setText(text);
        container.layout(true);
    }

    private void showBrowser(final String html) {
        if (statusLabel != null && !statusLabel.isDisposed()) {
            statusLabel.dispose();
            statusLabel = null;
        }
        bridge.close();
        bridge.open(BrowserPage.create(container), html);
        container.layout(true);
    }

    private final class PageHost implements ViewerBridge.Host {

        @Override
        public void ready() {
            loadRunning = false;
        }

        @Override
        public void requestWindow(final long reqId, final long t0Ns, final long t1Ns, final int px) {
            serveWindow(reqId, t0Ns, t1Ns, px);
        }

        @Override
        public void reload() {
            bridge.postIfCurrent(bridge.generation(), () -> startLoad(false));
        }

        @Override
        public void select(final SelectedCall frame) {
            onSelect(frame);
        }

        @Override
        public void requestSearch(final long reqId, final String query) {
            serveSearch(reqId, query);
        }

        @Override
        public void requestMatch(final long reqId, final boolean forward, final long posNs) {
            serveMatch(reqId, forward, posNs);
        }

        @Override
        public void exportSession() {
            bridge.postIfCurrent(bridge.generation(), RecordingEditor.this::openExportDialog);
        }
    }

    private void serveWindow(final long reqId, final long t0, final long t1, final int px) {
        final TraceSnapshot data = trace;
        if (data == null) {
            return;
        }
        final long gen = bridge.generation();
        queryWorker.execute(() -> {
            if (bridge.generation() != gen) {
                return;
            }
            try {
                int budget = WindowExtractor.DEFAULT_CALL_BUDGET;
                Window r = WindowExtractor.extract(data, t0, t1, px, budget);
                ViewerJson.SentNames trial = sentNames.copy();
                String json = ViewerJson.windowJson(data, r, reqId, trial);
                while (json.length() > ViewerBridge.MAX_RESPONSE_CHARS && budget > 1000) {
                    budget /= 2;
                    r = WindowExtractor.extract(data, t0, t1, px, budget);
                    trial = sentNames.copy();
                    json = ViewerJson.windowJson(data, r, reqId, trial);
                }
                final ViewerJson.SentNames committed = trial;
                final String reply = json;
                bridge.postIfCurrent(gen, () -> {
                    sentNames = committed;
                    bridge.windowReply(reply);
                });
            } catch (final Exception e) {
                final String msg = String.valueOf(e);
                bridge.postIfCurrent(gen, () -> bridge.windowError(reqId, msg));
            }
        });
    }

    private void onSelect(final SelectedCall f) {
        final long seq = ++selectionSeq;
        if (f == null) {
            setSelection(null);
            return;
        }
        setSelection(f);
        final TraceSnapshot data = trace;
        if (data == null) {
            return;
        }
        final long gen = bridge.generation();
        queryWorker.execute(() -> {
            if (bridge.generation() != gen) {
                return;
            }
            final SubtreeAggregate agg;
            try {
                agg = SubtreeAggregate.compute(data, f.tid(), f.startNs(), f.durNs(), f.depth(), f.methodId());
            } catch (final Exception ex) {
                ILog.get().error("Subtree aggregation failed for tid=" + f.tid()
                        + " start=" + f.startNs() + " dur=" + f.durNs() + " depth=" + f.depth() + " methodId="
                        + f.methodId() + " in " + file, ex);
                final String msg = "Aggregation failed: " + ex;
                bridge.postIfCurrent(gen, () -> {
                    if (selectionSeq == seq && selection != null) {
                        setSelection(selection.withSubtreeError(msg));
                    }
                });
                return;
            }
            bridge.postIfCurrent(gen, () -> {
                if (selectionSeq == seq && selection != null) {
                    setSelection(selection.withSubtree(agg));
                }
            });
        });
    }

    private void setSelection(final SelectedCall f) {
        selection = f;
        for (final Listener l : listeners) {
            l.selectionChanged();
        }
    }

    private void fireTraceChanged() {
        for (final Listener l : listeners) {
            l.traceChanged();
        }
    }

    private void serveSearch(final long reqId, final String rawQuery) {
        final TraceSnapshot data = trace;
        if (data == null) {
            return;
        }
        final String query = rawQuery.toLowerCase(Locale.ROOT);
        final long gen = bridge.generation();
        queryWorker.execute(() -> {
            if (bridge.generation() != gen) {
                return;
            }
            final BitSet ids = new BitSet();
            long calls = 0;
            if (!query.isEmpty()) {
                final String[] names = data.methodNames;
                for (int id = 0; id < names.length; id++) {
                    if (names[id] != null && names[id].toLowerCase(Locale.ROOT).contains(query)) {
                        ids.set(id);
                        if (id < data.callsByMethod.length) {
                            calls += data.callsByMethod[id];
                        }
                    }
                }
            }
            matchedMethodIds = ids;
            final String reply = ViewerJson.searchJson(reqId, ids, calls);
            bridge.postIfCurrent(gen, () -> bridge.searchReply(reply));
        });
    }

    private void serveMatch(final long reqId, final boolean forward, final long posNs) {
        final TraceSnapshot data = trace;
        if (data == null) {
            return;
        }
        final long gen = bridge.generation();
        queryWorker.execute(() -> {
            if (bridge.generation() != gen) {
                return;
            }
            final BitSet ids = matchedMethodIds;
            MatchSearch.Match m = null;
            if (!ids.isEmpty()) {
                m = forward ? MatchSearch.nextMatch(data, ids, posNs) : MatchSearch.prevMatch(data, ids, posNs);
                if (m == null) {
                    m = forward ? MatchSearch.nextMatch(data, ids, Long.MIN_VALUE)
                            : MatchSearch.prevMatch(data, ids, Long.MAX_VALUE);
                }
            }
            final String reply = ViewerJson.matchJson(reqId, m);
            bridge.postIfCurrent(gen, () -> bridge.matchReply(reply));
        });
    }

    @Override
    public void setFocus() {
        if (!bridge.focus()) {
            container.setFocus();
        }
    }

    @Override
    public void dispose() {
        if (bridge != null) {
            bridge.close();
        }
        loadRunning = false;
        if (loadJob != null) {
            loadJob.cancel();
        }
        final TraceIndexer ix = indexer;
        indexer = null;
        if (ix != null) {
            ix.close();
        }
        final TraceSnapshot t = trace;
        trace = null;
        if (queryWorker != null) {
            if (t != null) {
                queryWorker.execute(t::release);
            }
            queryWorker.shutdown();
        } else if (t != null) {
            t.release();
        }
        selectionSeq++;
        selection = null;
        fireTraceChanged();
        listeners.clear();
        super.dispose();
    }

    @Override
    public void doSave(final IProgressMonitor monitor) {
    }

    @Override
    public void doSaveAs() {
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }
}
