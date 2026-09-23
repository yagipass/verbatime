package io.github.yagipass.verbatime.jmc.recordings;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.Preferences;
import io.github.yagipass.verbatime.jmc.RecordingEditorInput;
import io.github.yagipass.verbatime.jmc.control.ControlTexts;
import io.github.yagipass.verbatime.jmc.control.TransferJob;
import io.github.yagipass.verbatime.jmc.recordings.LocalRecordings.Entry;
import io.github.yagipass.verbatime.jmc.views.Columns;

public final class RecordingsView extends ViewPart {

    private static final String ID = "io.github.yagipass.verbatime.jmc.recordingsView";

    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final IPropertyChangeListener settingsListener = e -> {
        if (Preferences.RECORDINGS_DIR.equals(e.getProperty())) {
            refresh();
        }
    };

    private TableViewer viewer;

    public static void refreshIn(final IWorkbenchPage page) {
        if (page.findView(ID) instanceof final RecordingsView v) {
            v.refresh();
        }
    }

    @Override
    public void createPartControl(final Composite parent) {
        viewer = new TableViewer(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        viewer.getTable().setHeaderVisible(true);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        ColumnViewerToolTipSupport.enableFor(viewer);
        column("Connection", 130, SWT.LEFT, Entry::connectionDir);
        column("File", 240, SWT.LEFT, Entry::name);
        column("Size", 90, SWT.RIGHT, e -> ControlTexts.sizeOrUnknown(e.size()));
        column("Modified", 150, SWT.LEFT, e -> MODIFIED.format(Instant.ofEpochMilli(e.modifiedMs())));
        viewer.addDoubleClickListener(e -> {
            if (e.getSelection() instanceof final IStructuredSelection s
                    && s.getFirstElement() instanceof final Entry en) {
                open(en.file());
            }
        });

        final Action deleteAction = new Action("Delete") {
            @Override
            public void run() {
                deleteSelected();
            }
        };
        deleteAction.setToolTipText("Delete the selected recording file");
        final ISharedImages shared = PlatformUI.getWorkbench().getSharedImages();
        deleteAction.setImageDescriptor(shared.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE));
        deleteAction.setDisabledImageDescriptor(shared.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE_DISABLED));
        deleteAction.setEnabled(false);
        viewer.addSelectionChangedListener(e -> deleteAction.setEnabled(!selected().isEmpty()));
        viewer.getTable().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent e) {
                if (e.keyCode == SWT.DEL && deleteAction.isEnabled()) {
                    deleteAction.run();
                }
            }
        });
        getViewSite().getActionBars().getToolBarManager().add(deleteAction);
        final MenuManager menu = new MenuManager();
        menu.add(deleteAction);
        viewer.getControl().setMenu(menu.createContextMenu(viewer.getControl()));

        Preferences.get().addPropertyChangeListener(settingsListener);
        refresh();
    }

    private void column(final String title, final int width, final int style, final Function<Entry, String> text) {
        Columns.addTable(viewer, title, width, style,
                new Columns.ColumnLabels<>(Entry.class, text, e -> e.file().toString(), false));
    }

    private void refresh() {
        if (viewer == null || viewer.getControl().isDisposed()) {
            return;
        }
        final Path dir = Preferences.get().recordingsDir();
        final List<Entry> entries = LocalRecordings.scan(dir);
        viewer.setInput(entries);
        setContentDescription(Formats.plural(entries.size(), "recording") + " in " + dir);
    }

    private List<Entry> selected() {
        final List<Entry> out = new ArrayList<>();
        for (final Object o : viewer.getStructuredSelection()) {
            if (o instanceof final Entry e) {
                out.add(e);
            }
        }
        return out;
    }

    private void deleteSelected() {
        final List<Entry> chosen = selected();
        if (chosen.isEmpty()) {
            return;
        }
        final IWorkbenchPage page = getSite().getPage();
        final List<Entry> targets = LocalRecordings.deletable(chosen, TransferJob::isTransferring);
        final int skipped = chosen.size() - targets.size();
        if (targets.isEmpty()) {
            MessageDialog.openError(getSite().getShell(), "Delete recording",
                    ControlTexts.stillTransferringText(chosen.get(0)));
            return;
        }
        if (!MessageDialog.openConfirm(getSite().getShell(),
                targets.size() == 1 ? "Delete recording" : "Delete recordings",
                ControlTexts.deletePrompt(targets, skipped))) {
            return;
        }
        final List<IEditorReference> open = new ArrayList<>();
        for (final Entry e : targets) {
            open.addAll(List.of(page.findEditors(new RecordingEditorInput(e.file()), RecordingEditorInput.EDITOR_ID,
                    IWorkbenchPage.MATCH_INPUT | IWorkbenchPage.MATCH_ID)));
        }
        if (!open.isEmpty()) {
            page.closeEditors(open.toArray(IEditorReference[]::new), false);
        }
        final LocalRecordings.DeleteResult result = LocalRecordings.deleteAll(Preferences.get().recordingsDir(),
                targets);
        refresh();
        if (result.failed() > 0) {
            MessageDialog.openError(getSite().getShell(), "Delete recording",
                    ControlTexts.deleteFailedText(result.failed(), targets.size(), result.firstError()));
        }
    }

    private void open(final Path file) {
        RecordingEditorInput.openOrReport(getSite().getPage(), file, this::setContentDescription);
    }

    @Override
    public void setFocus() {
        if (viewer != null && !viewer.getControl().isDisposed()) {
            viewer.getControl().setFocus();
            refresh();
        }
    }

    @Override
    public void dispose() {
        Preferences.get().removePropertyChangeListener(settingsListener);
        super.dispose();
    }
}
