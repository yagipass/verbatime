package io.github.yagipass.verbatime.jmc.control;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.jmc.Preferences;
import io.github.yagipass.verbatime.jmc.RecordingEditor;
import io.github.yagipass.verbatime.jmc.RecordingEditorInput;
import io.github.yagipass.verbatime.jmc.UiThread;
import io.github.yagipass.verbatime.jmc.control.ControlPresenter.State;
import io.github.yagipass.verbatime.jmc.control.ControlPresenter.ViewState;
import io.github.yagipass.verbatime.jmc.recordings.RecordingsView;
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
import org.jspecify.annotations.Nullable;

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

  private @Nullable SearchPopup popup;

  private Label statusLabel;

  private Button startBtn;

  private Button stopBtn;

  private Label messageLabel;

  private List<RootEntry> renderedRoots = List.of();

  private State renderedState = State.DISCONNECTED;

  @Override
  public void createPartControl(Composite parent) {
    display = parent.getDisplay();
    ui = UiThread.of(parent);

    Composite root = new Composite(parent, SWT.NONE);
    root.setLayout(new GridLayout(1, false));

    createConnectionRow(root);
    createRootsGroup(root);
    createRecordingStrip(root);

    messageLabel = new Label(root, SWT.WRAP);
    messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    connection =
        new ControlPresenter(
            new PresenterView(),
            ui,
            ControlView::newJmxExecutor,
            JmxAgent::dial,
            settings,
            System::currentTimeMillis,
            p -> {
              TransferJob job = new TransferJob(p);
              job.schedule();
              return () -> job.cancel();
            });
    render(ViewState.disconnected(State.DISCONNECTED));
    setMessage("Not connected");
  }

  private void createConnectionRow(Composite root) {
    Composite conn = new Composite(root, SWT.NONE);
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
    Listener connect = e -> connection.connect(targetText.getText().trim());
    connectBtn.addListener(
        SWT.Selection,
        e -> {
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
    Button disconnectBtn = new Button(connectedPage, SWT.PUSH);
    disconnectBtn.setText("Disconnect");
    disconnectBtn.addListener(SWT.Selection, e -> connection.disconnect());

    connStackLayout.topControl = disconnectedPage;
  }

  private static GridLayout zeroMargin(GridLayout l) {
    l.marginWidth = 0;
    l.marginHeight = 0;
    return l;
  }

  @SuppressWarnings("ReferenceEquality")
  private void createRootsGroup(Composite root) {
    rootsGroup = new Group(root, SWT.NONE);
    rootsGroup.setText("Instrumentation roots");
    rootsGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    rootsGroup.setLayout(new GridLayout(1, false));

    chipArea = new Composite(rootsGroup, SWT.NONE);
    RowLayout rl = new RowLayout(SWT.HORIZONTAL);
    rl.wrap = true;
    rl.marginLeft = 0;
    rl.marginRight = 0;
    rl.marginTop = 0;
    rl.marginBottom = 0;
    chipArea.setLayout(rl);
    chipArea.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

    rootsGroup.addListener(
        SWT.Resize,
        e -> {
          GridData gd = (GridData) chipArea.getLayoutData();
          int w =
              rootsGroup.getClientArea().width
                  - 2 * ((GridLayout) rootsGroup.getLayout()).marginWidth;
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
    searchText.addListener(
        SWT.FocusOut,
        e ->
            ui.postAfter(
                POPUP_FOCUS_GRACE_MS,
                () -> {
                  SearchPopup p = visiblePopup();
                  if (p != null && display.getFocusControl() != p.table()) {
                    hidePopup();
                  }
                }));

    countLabel = new Label(rootsGroup, SWT.NONE);
    countLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
  }

  private void createRecordingStrip(Composite root) {
    Composite rec = new Composite(root, SWT.NONE);
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

  private void render(ViewState p) {
    boolean connected = p.state() == State.CONNECTED;
    connectionDot.setForeground(
        display.getSystemColor(
            connected
                ? (p.recording() ? SWT.COLOR_RED : SWT.COLOR_DARK_GREEN)
                : SWT.COLOR_DARK_GRAY));
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
    startBtn.setToolTipText(
        !connected
            ? null
            : p.transferring()
                ? "Wait for the transfer of the previous recording to finish"
                : p.roots().isEmpty() ? "Add an instrumentation root first" : null);
    stopBtn.setEnabled(p.canStop());
    searchText.setEnabled(connected && !p.rootsLocked());
    chipArea.setEnabled(!p.rootsLocked());
    String lockHint = p.rootsLocked() ? "Roots are locked while recording" : null;
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

  private void updateChips(List<RootEntry> entries) {
    if (entries.equals(renderedRoots)) {
      return;
    }
    renderedRoots = entries;
    for (Control ch : chipArea.getChildren()) {
      ch.dispose();
    }
    for (RootEntry r : entries) {
      Composite chip = new Composite(chipArea, SWT.BORDER);
      GridLayout cl = new GridLayout(3, false);
      cl.marginWidth = 3;
      cl.marginHeight = 1;
      cl.horizontalSpacing = 3;
      chip.setLayout(cl);
      Label dot = new Label(chip, SWT.NONE);
      dot.setText("●");
      dot.setForeground(
          display.getSystemColor(r.resolved() ? SWT.COLOR_DARK_GREEN : SWT.COLOR_DARK_YELLOW));
      dot.setToolTipText(r.resolved() ? "Instrumented" : "Waiting for the class to load");

      CLabel spec = new CLabel(chip, SWT.NONE);
      spec.setText(r.spec());
      spec.setMargins(0, 0, 0, 0);
      spec.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
      spec.setToolTipText(r.spec());
      Label close = new Label(chip, SWT.NONE);
      close.setText("✕");
      close.setToolTipText("Remove this root");
      close.addListener(SWT.MouseUp, e -> connection.removeRoot(r.spec()));
    }
    capChipWidths();
    chipArea.requestLayout();
  }

  private void capChipWidths() {
    @Var int avail = ((GridData) chipArea.getLayoutData()).widthHint;
    if (avail <= 0) {
      avail = chipArea.getClientArea().width;
    }
    if (avail <= 0) {
      return;
    }
    for (Control chip : chipArea.getChildren()) {
      RowData rd = new RowData();
      if (chip.computeSize(SWT.DEFAULT, SWT.DEFAULT).x > avail) {
        rd.width = avail;
      }
      chip.setLayoutData(rd);
    }
  }

  private void showCandidates(String[] items) {
    @Var SearchPopup p = popup;
    if (p == null || p.shell().isDisposed()) {
      Shell shell = new Shell(searchText.getShell(), SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL);
      shell.setLayout(zeroMargin(new GridLayout(1, false)));
      Table table = new Table(shell, SWT.SINGLE | SWT.FULL_SELECTION | SWT.NO_FOCUS);
      table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      table.addListener(
          SWT.MouseDown,
          e -> {
            TableItem it = table.getItem(new Point(e.x, e.y));
            if (it != null) {
              addRoot(it.getText());
            }
          });
      p = new SearchPopup(shell, table);
      popup = p;
    }
    p.table().removeAll();
    for (String s : items) {
      new TableItem(p.table(), SWT.NONE).setText(s);
    }
    p.table().setSelection(0);
    Point loc = searchText.toDisplay(0, searchText.getBounds().height);
    int w = Math.max(searchText.getBounds().width, 200);
    int h = Math.min(items.length, 10) * p.table().getItemHeight() + 8;
    p.shell().setBounds(loc.x, loc.y, w, h);
    p.shell().setVisible(true);
  }

  private @Nullable SearchPopup visiblePopup() {
    SearchPopup p = popup;
    return p != null && !p.shell().isDisposed() && p.shell().isVisible() ? p : null;
  }

  private void hidePopup() {
    SearchPopup p = popup;
    if (p != null && !p.shell().isDisposed()) {
      p.shell().setVisible(false);
    }
  }

  private void onSearchKey(org.eclipse.swt.widgets.Event e) {
    SearchPopup p = visiblePopup();
    if (p == null) {
      return;
    }
    if (e.keyCode == SWT.ARROW_DOWN || e.keyCode == SWT.ARROW_UP) {
      int n = p.table().getItemCount();
      int i = p.table().getSelectionIndex() + (e.keyCode == SWT.ARROW_DOWN ? 1 : -1);
      p.table().setSelection(Math.max(0, Math.min(n - 1, i)));
      e.doit = false;
    } else if (e.keyCode == SWT.ESC) {
      hidePopup();
      e.doit = false;
    }
  }

  private void onSearchEnter() {
    SearchPopup p = visiblePopup();
    if (p != null && p.table().getSelectionIndex() >= 0) {
      addRoot(p.table().getSelection()[0].getText());
      return;
    }
    String t = searchText.getText().trim();
    if (RootSpecs.looksLikeSpec(t)) {
      addRoot(t);
    } else if (!t.isEmpty()) {
      setMessage("Type pkg.Cls::method or pick a candidate");
    }
  }

  private void addRoot(String spec) {
    hidePopup();
    connection.addRoot(spec);
  }

  private void setMessage(String text) {
    if (!messageLabel.isDisposed()) {
      messageLabel.setText(text);
    }
  }

  private record SearchPopup(Shell shell, Table table) {}

  private final class PresenterView implements ControlPresenter.View {

    @Override
    public void render(ViewState p) {
      ControlView.this.render(p);
    }

    @Override
    public void message(String text) {
      setMessage(text);
    }

    @Override
    public void rootCandidates(String[] specs) {
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
    public ControlPresenter.@Nullable EditorHandle openEditor(Path file) {
      RecordingEditor editor =
          RecordingEditorInput.openOrReport(
              getSite().getPage(), file, ControlView.this::setMessage);
      return editor == null ? null : new WorkbenchEditorHandle(editor);
    }

    @Override
    public void recordingsChanged() {
      RecordingsView.refreshIn(getSite().getPage());
    }
  }

  private static final class WorkbenchEditorHandle implements ControlPresenter.EditorHandle {

    private final RecordingEditor editor;

    private WorkbenchEditorHandle(RecordingEditor editor) {
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
    public void reload(boolean live) {
      editor.reload(live);
    }
  }

  @Override
  public void setFocus() {
    if (connection != null
        && connection.state() == State.CONNECTED
        && searchText != null
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
    SearchPopup p = popup;
    if (p != null && !p.shell().isDisposed()) {
      p.shell().dispose();
    }
    super.dispose();
  }

  private static ExecutorService newJmxExecutor() {
    return Executors.newSingleThreadExecutor(
        r -> {
          Thread t = new Thread(r, "vbtm-control-jmx");
          t.setDaemon(true);
          return t;
        });
  }
}
