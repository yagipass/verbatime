package io.github.yagipass.verbatime.jmc.control;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import io.github.yagipass.verbatime.jmc.Preferences;
import io.github.yagipass.verbatime.jmc.RecordingEditor;
import io.github.yagipass.verbatime.jmc.RecordingEditorInput;
import io.github.yagipass.verbatime.jmc.UiThread;
import io.github.yagipass.verbatime.jmc.control.ControlPresenter.State;
import io.github.yagipass.verbatime.jmc.control.ControlPresenter.ViewState;
import io.github.yagipass.verbatime.jmc.recordings.RecordingsView;

public final class ControlView extends ViewPart {

    private static final int POPUP_FOCUS_GRACE_MS = 150;

    private final Preferences settings = Preferences.get();

    private Display display;

    private UiThread ui;

    private ControlPresenter connection;

    private Label connectionDot;

    private Composite connStack;

    private StackLayout connStackLayout;

    private Composite disconnectedPage;

    private Composite connectedPage;

    private Text targetText;

    private Button connectBtn;

    private Label connInfoLabel;

    private Group rootsGroup;

    private Composite chipArea;

    private Text searchText;

    private Label countLabel;

    private Shell popup;

    private Table popupTable;

    private Label statusLabel;

    private Button startBtn;

    private Button stopBtn;

    private Label messageLabel;

    private List<RootEntry> renderedRoots = List.of();

    private State renderedState = State.DISCONNECTED;

    @Override
    public void createPartControl(final Composite parent) {
        display = parent.getDisplay();
        ui = UiThread.of(parent);

        final Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));

        createConnectionRow(root);
        createRootsGroup(root);
        createRecordingStrip(root);

        messageLabel = new Label(root, SWT.WRAP);
        messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        connection = new ControlPresenter(new PresenterView(), ui, ControlView::newJmxExecutor, JmxAgent::dial,
                settings, System::currentTimeMillis, p -> {
                    final TransferJob job = new TransferJob(p);
                    job.schedule();
                    return () -> job.cancel();
                });
        render(ViewState.disconnected(State.DISCONNECTED));
        setMessage("Not connected");
    }

    private void createConnectionRow(final Composite root) {
        final Composite conn = new Composite(root, SWT.NONE);
        conn.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        conn.setLayout(new GridLayout(2, false));

        connectionDot = new Label(conn, SWT.NONE);
        connectionDot.setText("●");
        connectionDot.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));

        connStack = new Composite(conn, SWT.NONE);
        connStack.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        connStackLayout = new StackLayout();
        connStack.setLayout(connStackLayout);

        disconnectedPage = new Composite(connStack, SWT.NONE);
        disconnectedPage.setLayout(zeroMargin(new GridLayout(2, false)));
        targetText = new Text(disconnectedPage, SWT.BORDER | SWT.SINGLE);
        targetText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        targetText.setText(settings.getString(Preferences.AGENT_TARGET));
        targetText.setToolTipText("host:port or a full JMX service URL");
        connectBtn = new Button(disconnectedPage, SWT.PUSH);
        connectBtn.setText("Connect");
        final Listener connect = e -> connection.connect(targetText.getText().trim());
        connectBtn.addListener(SWT.Selection, e -> {
            if (connection.state() == State.CONNECTING) {
                connection.cancelConnect();
            } else {
                connect.handleEvent(e);
            }
        });
        targetText.addListener(SWT.DefaultSelection, connect);

        connectedPage = new Composite(connStack, SWT.NONE);
        connectedPage.setLayout(zeroMargin(new GridLayout(2, false)));
        connInfoLabel = new Label(connectedPage, SWT.NONE);
        connInfoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        final Button disconnectBtn = new Button(connectedPage, SWT.PUSH);
        disconnectBtn.setText("Disconnect");
        disconnectBtn.addListener(SWT.Selection, e -> connection.disconnect());

        connStackLayout.topControl = disconnectedPage;
    }

    private static GridLayout zeroMargin(final GridLayout l) {
        l.marginWidth = 0;
        l.marginHeight = 0;
        return l;
    }

    @SuppressWarnings("ReferenceEquality")
    private void createRootsGroup(final Composite root) {
        rootsGroup = new Group(root, SWT.NONE);
        rootsGroup.setText("Instrumentation roots");
        rootsGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        rootsGroup.setLayout(new GridLayout(1, false));

        chipArea = new Composite(rootsGroup, SWT.NONE);
        final RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.wrap = true;
        rl.marginLeft = 0;
        rl.marginRight = 0;
        rl.marginTop = 0;
        rl.marginBottom = 0;
        chipArea.setLayout(rl);
        chipArea.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        rootsGroup.addListener(SWT.Resize, e -> {
            final GridData gd = (GridData) chipArea.getLayoutData();
            final int w = rootsGroup.getClientArea().width - 2 * ((GridLayout) rootsGroup.getLayout()).marginWidth;
            if (w > 0 && gd.widthHint != w) {
                gd.widthHint = w;
                capChipWidths();
                rootsGroup.layout();
                chipArea.layout();
            }
        });

        searchText = new Text(rootsGroup, SWT.BORDER | SWT.SINGLE);
        searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchText.setMessage("Search instrumented methods…");
        searchText.setEnabled(false);
        searchText.addModifyListener(e -> connection.search(searchText.getText()));
        searchText.addListener(SWT.DefaultSelection, e -> onSearchEnter());
        searchText.addListener(SWT.KeyDown, this::onSearchKey);
        searchText.addListener(SWT.FocusOut, e -> ui.postAfter(POPUP_FOCUS_GRACE_MS, () -> {
            if (popupVisible() && display.getFocusControl() != popupTable) {
                hidePopup();
            }
        }));

        countLabel = new Label(rootsGroup, SWT.NONE);
        countLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createRecordingStrip(final Composite root) {
        final Composite rec = new Composite(root, SWT.NONE);
        rec.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        rec.setLayout(new GridLayout(3, false));
        statusLabel = new Label(rec, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Not connected");
        startBtn = new Button(rec, SWT.PUSH);
        startBtn.setText("Start recording");
        startBtn.setEnabled(false);
        startBtn.addListener(SWT.Selection, e -> connection.startRecording());
        stopBtn = new Button(rec, SWT.PUSH);
        stopBtn.setText("Stop recording");
        stopBtn.setEnabled(false);
        stopBtn.addListener(SWT.Selection, e -> connection.stopRecording());
    }

    private void render(final ViewState p) {
        final boolean connected = p.state() == State.CONNECTED;
        connectionDot.setForeground(display.getSystemColor(
                connected ? (p.recording() ? SWT.COLOR_RED : SWT.COLOR_DARK_GREEN) : SWT.COLOR_DARK_GRAY));
        connStackLayout.topControl = connected ? connectedPage : disconnectedPage;
        connStack.requestLayout();
        connectBtn.setText(p.state() == State.CONNECTING ? "Cancel" : "Connect");
        if (p.connectionText() != null) {
            connInfoLabel.setText(p.connectionText());
        }
        updateChips(p.roots());
        countLabel.setText(p.instrumentedText());
        statusLabel.setText(p.statusText());
        startBtn.setEnabled(p.canStart());
        startBtn.setToolTipText(!connected ? null
                : p.transferring() ? "Wait for the transfer of the previous recording to finish"
                : p.roots().isEmpty() ? "Add an instrumentation root first" : null);
        stopBtn.setEnabled(p.canStop());
        searchText.setEnabled(connected && !p.rootsLocked());
        chipArea.setEnabled(!p.rootsLocked());
        final String lockHint = p.rootsLocked() ? "Roots are locked while recording" : null;
        searchText.setToolTipText(lockHint);
        chipArea.setToolTipText(lockHint);
        if (!connected || p.rootsLocked()) {
            hidePopup();
        }
        if (!connected && renderedState == State.CONNECTED) {
            searchText.setText("");
        }
        renderedState = p.state();
    }

    private void updateChips(final List<RootEntry> entries) {
        if (entries.equals(renderedRoots)) {
            return;
        }
        renderedRoots = entries;
        for (final Control ch : chipArea.getChildren()) {
            ch.dispose();
        }
        for (final RootEntry r : entries) {
            final Composite chip = new Composite(chipArea, SWT.BORDER);
            final GridLayout cl = new GridLayout(3, false);
            cl.marginWidth = 3;
            cl.marginHeight = 1;
            cl.horizontalSpacing = 3;
            chip.setLayout(cl);
            final Label dot = new Label(chip, SWT.NONE);
            dot.setText("●");
            dot.setForeground(display.getSystemColor(r.resolved() ? SWT.COLOR_DARK_GREEN : SWT.COLOR_DARK_YELLOW));
            dot.setToolTipText(r.resolved() ? "Instrumented" : "Waiting for the class to load");

            final CLabel spec = new CLabel(chip, SWT.NONE);
            spec.setText(r.spec());
            spec.setMargins(0, 0, 0, 0);
            spec.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            spec.setToolTipText(r.spec());
            final Label close = new Label(chip, SWT.NONE);
            close.setText("✕");
            close.setToolTipText("Remove this root");
            close.addListener(SWT.MouseUp, e -> connection.removeRoot(r.spec()));
        }
        capChipWidths();
        chipArea.requestLayout();
    }

    private void capChipWidths() {
        int avail = ((GridData) chipArea.getLayoutData()).widthHint;
        if (avail <= 0) {
            avail = chipArea.getClientArea().width;
        }
        if (avail <= 0) {
            return;
        }
        for (final Control chip : chipArea.getChildren()) {
            final RowData rd = new RowData();
            if (chip.computeSize(SWT.DEFAULT, SWT.DEFAULT).x > avail) {
                rd.width = avail;
            }
            chip.setLayoutData(rd);
        }
    }

    private void showCandidates(final String[] items) {
        if (popup == null || popup.isDisposed()) {
            popup = new Shell(searchText.getShell(), SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL);
            popup.setLayout(zeroMargin(new GridLayout(1, false)));
            popupTable = new Table(popup, SWT.SINGLE | SWT.FULL_SELECTION | SWT.NO_FOCUS);
            popupTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            popupTable.addListener(SWT.MouseDown, e -> {
                final TableItem it = popupTable.getItem(new Point(e.x, e.y));
                if (it != null) {
                    addRoot(it.getText());
                }
            });
        }
        popupTable.removeAll();
        for (final String s : items) {
            new TableItem(popupTable, SWT.NONE).setText(s);
        }
        popupTable.setSelection(0);
        final Point loc = searchText.toDisplay(0, searchText.getBounds().height);
        final int w = Math.max(searchText.getBounds().width, 200);
        final int h = Math.min(items.length, 10) * popupTable.getItemHeight() + 8;
        popup.setBounds(loc.x, loc.y, w, h);
        popup.setVisible(true);
    }

    private boolean popupVisible() {
        return popup != null && !popup.isDisposed() && popup.isVisible();
    }

    private void hidePopup() {
        if (popup != null && !popup.isDisposed()) {
            popup.setVisible(false);
        }
    }

    private void onSearchKey(final org.eclipse.swt.widgets.Event e) {
        if (!popupVisible()) {
            return;
        }
        if (e.keyCode == SWT.ARROW_DOWN || e.keyCode == SWT.ARROW_UP) {
            final int n = popupTable.getItemCount();
            final int i = popupTable.getSelectionIndex() + (e.keyCode == SWT.ARROW_DOWN ? 1 : -1);
            popupTable.setSelection(Math.max(0, Math.min(n - 1, i)));
            e.doit = false;
        } else if (e.keyCode == SWT.ESC) {
            hidePopup();
            e.doit = false;
        }
    }

    private void onSearchEnter() {
        if (popupVisible() && popupTable.getSelectionIndex() >= 0) {
            addRoot(popupTable.getSelection()[0].getText());
            return;
        }
        final String t = searchText.getText().trim();
        if (RootSpecs.looksLikeSpec(t)) {
            addRoot(t);
        } else if (!t.isEmpty()) {
            setMessage("Type pkg.Cls::method or pick a candidate");
        }
    }

    private void addRoot(final String spec) {
        hidePopup();
        connection.addRoot(spec);
    }

    private void setMessage(final String text) {
        if (!messageLabel.isDisposed()) {
            messageLabel.setText(text);
        }
    }

    private final class PresenterView implements ControlPresenter.View {

        @Override
        public void render(final ViewState p) {
            ControlView.this.render(p);
        }

        @Override
        public void message(final String text) {
            setMessage(text);
        }

        @Override
        public void rootCandidates(final String[] specs) {
            if (specs.length == 0) {
                hidePopup();
            } else {
                showCandidates(specs);
            }
        }

        @Override
        public void rootAccepted() {
            searchText.setText("");
            searchText.setFocus();
        }

        @Override
        public ControlPresenter.EditorHandle openEditor(final Path file) {
            final RecordingEditor editor = RecordingEditorInput.openOrReport(getSite().getPage(), file, ControlView.this::setMessage);
            return editor == null ? null : new WorkbenchEditorHandle(editor);
        }

        @Override
        public void recordingsChanged() {
            RecordingsView.refreshIn(getSite().getPage());
        }
    }

    private static final class WorkbenchEditorHandle implements ControlPresenter.EditorHandle {

        private final RecordingEditor editor;

        private WorkbenchEditorHandle(final RecordingEditor editor) {
            this.editor = editor;
        }

        @Override
        public boolean isOpen() {
            return !editor.isDisposed();
        }

        @Override
        public boolean isLoading() {
            return editor.isLoading();
        }

        @Override
        public void reload(final boolean live) {
            editor.reload(live);
        }
    }

    @Override
    public void setFocus() {
        if (connection != null && connection.state() == State.CONNECTED && searchText != null
                && !searchText.isDisposed()) {
            searchText.setFocus();
        } else if (targetText != null && !targetText.isDisposed()) {
            targetText.setFocus();
        }
    }

    @Override
    public void dispose() {
        if (connection != null) {
            connection.dispose();
        }
        if (popup != null && !popup.isDisposed()) {
            popup.dispose();
        }
        super.dispose();
    }

    private static ExecutorService newJmxExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "vbtm-control-jmx");
            t.setDaemon(true);
            return t;
        });
    }
}
