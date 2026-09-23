package io.github.yagipass.verbatime.jmc;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import io.github.yagipass.verbatime.jmc.views.Columns;

final class SessionExportDialog extends Dialog {

    private static final int[] FLOORS_US = { 0, 1, 10, 100 };

    private static final int DEFAULT_FLOOR_US = 10;

    private final TraceSnapshot data;

    private final Session preselected;

    private TableViewer viewer;

    private final Button[] radios = new Button[FLOORS_US.length];

    private Label fileLabel;

    private boolean scheduled;

    private SessionExportDialog(final Shell parent, final TraceSnapshot data, final Session preselected) {
        super(parent);
        this.data = data;
        this.preselected = preselected;
    }

    static boolean openAndSchedule(final Shell parent, final TraceSnapshot data, final SelectedCall selection) {
        final SessionExportDialog d = new SessionExportDialog(parent, data, SessionExportTexts.defaultSession(data, selection));
        d.open();
        return d.scheduled;
    }

    @Override
    protected void configureShell(final Shell shell) {
        super.configureShell(shell);
        shell.setText("Export session as text");
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(final Composite parent) {
        final Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));
        final Color dim = area.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND);

        final Label head = new Label(area, SWT.NONE);
        head.setText("Session of " + data.path.getFileName() + " to export");

        viewer = new TableViewer(area, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        final GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = viewer.getTable().getItemHeight() * 12;
        tableData.widthHint = 800;
        viewer.getTable().setLayoutData(tableData);
        viewer.getTable().setHeaderVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        ColumnViewerToolTipSupport.enableFor(viewer);
        column("#", 50, SWT.RIGHT, s -> Integer.toString(s.seq), true);
        column("thread", 150, SWT.LEFT, s -> data.threadName(s.tid), false);
        column("root", 260, SWT.LEFT, s -> Formats.shortName(SessionExportTexts.rootName(data, s)), false);
        column("start", 90, SWT.RIGHT, s -> Formats.fmtTs(s.startNs), true);
        column("duration", 90, SWT.RIGHT, s -> Formats.fmtDur(s.durNs()), true);
        column("calls", 90, SWT.RIGHT, s -> Formats.fmtInt(s.callCount), true);
        column("flags", 110, SWT.LEFT, SessionExportTexts::flags, false);
        viewer.setInput(data.sessions);
        viewer.addSelectionChangedListener(e -> updateState());
        viewer.addDoubleClickListener(e -> {
            final Session s = selected();
            if (s != null && SessionExportTexts.isExportable(s)) {
                okPressed();
            }
        });
        if (preselected != null) {
            viewer.setSelection(new StructuredSelection(preselected), true);
            UiThread.of(viewer.getControl()).post(() -> {
                viewer.reveal(preselected);
                viewer.getTable().showSelection();
                viewer.getTable().setFocus();
            });
        }

        final Group floor = new Group(area, SWT.NONE);
        floor.setText("Floor");
        floor.setLayout(new RowLayout());
        floor.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        for (int i = 0; i < FLOORS_US.length; i++) {
            final Button b = new Button(floor, SWT.RADIO);
            b.setText(FLOORS_US[i] > 0 ? FLOORS_US[i] + " µs" : "none");
            b.setData(Integer.valueOf(FLOORS_US[i]));
            b.setSelection(FLOORS_US[i] == DEFAULT_FLOOR_US);
            b.addListener(SWT.Selection, e -> updateState());
            radios[i] = b;
        }
        final Label hint = new Label(area, SWT.WRAP);
        hint.setText("Calls shorter than the floor are kept only as per-parent counts, so nothing is dropped. "
                + "With no floor every call gets a line of its own. For a 1.3 s request of 5 million calls the file "
                + "is about 15 MB at 10 µs, 45 MB at 1 µs, 4 MB at 100 µs and 290 MB with no floor, and a startup "
                + "session with no floor runs into gigabytes.");
        hint.setForeground(dim);
        hint.setLayoutData(wrapData());

        fileLabel = new Label(area, SWT.WRAP);
        fileLabel.setForeground(dim);
        fileLabel.setLayoutData(wrapData());
        return area;
    }

    private static GridData wrapData() {
        final GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.widthHint = 400;
        return gd;
    }

    private void column(final String title, final int width, final int style, final Function<Session, String> text,
            final boolean mono) {
        Columns.addTable(viewer, title, width, style,
                new Columns.ColumnLabels<>(Session.class, text, s -> SessionExportTexts.rootName(data, s), mono) {
                    @Override
                    public Color getForeground(final Object element) {
                        return element instanceof final Session s && !SessionExportTexts.isExportable(s)
                                ? viewer.getControl().getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND)
                                : null;
                    }
                });
    }

    @Override
    protected void createButtonsForButtonBar(final Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Export…", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        updateState();
    }

    private Session selected() {
        return viewer.getSelection() instanceof final IStructuredSelection sel
                && sel.getFirstElement() instanceof final Session s ? s : null;
    }

    private int floorUs() {
        for (final Button b : radios) {
            if (!b.isDisposed() && b.getSelection()) {
                return ((Integer) b.getData()).intValue();
            }
        }
        return DEFAULT_FLOOR_US;
    }

    private void updateState() {
        if (fileLabel == null || fileLabel.isDisposed()) {
            return;
        }
        final Session s = selected();
        final boolean can = s != null && SessionExportTexts.isExportable(s);
        final Button ok = getButton(IDialogConstants.OK_ID);
        if (ok != null) {
            ok.setEnabled(can);
        }
        if (data.sessions.isEmpty()) {
            fileLabel.setText("This recording has no sessions");
        } else if (s == null) {
            fileLabel.setText("Select a session");
        } else if (!can) {
            fileLabel.setText("Session #" + s.seq + " has no calls to export");
        } else {
            fileLabel.setText("The Save dialog will propose "
                    + SessionExportTexts.exportFileName(data.path.getFileName().toString(), s.seq, floorUs()));
        }
        fileLabel.getParent().layout();
    }

    @Override
    protected void okPressed() {
        final Session s = selected();
        if (s == null || !SessionExportTexts.isExportable(s)) {
            return;
        }
        final int floorUs = floorUs();
        final FileDialog fd = new FileDialog(getShell(), SWT.SAVE | SWT.SHEET);
        fd.setText("Export session #" + s.seq + " as text");
        fd.setOverwrite(true);
        final Path parentDir = data.path.toAbsolutePath().getParent();
        if (parentDir != null) {
            fd.setFilterPath(parentDir.toString());
        }
        fd.setFileName(SessionExportTexts.exportFileName(data.path.getFileName().toString(), s.seq, floorUs));
        fd.setFilterExtensions(new String[] { "*.txt", "*.*" });
        fd.setFilterNames(new String[] { "Text files", "All files" });
        final String chosen = fd.open();
        if (chosen == null) {
            return;
        }
        final Path dest = Path.of(chosen);
        if (dest.toAbsolutePath().equals(data.path.toAbsolutePath())
                || chosen.toLowerCase(Locale.ROOT).endsWith(".vbtm")) {
            MessageDialog.openError(getShell(), "Export session", "Refusing to overwrite a recording: " + dest);
            return;
        }
        final long floorNs = SessionExportTexts.floorNs(floorUs);
        super.okPressed();
        SessionExportJob.schedule(getParentShell(), data, s, floorNs, dest);
        scheduled = true;
    }
}
