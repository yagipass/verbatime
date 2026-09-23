package io.github.yagipass.verbatime.jmc.views;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import io.github.yagipass.verbatime.jmc.RecordingEditor;
import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

abstract class EditorBoundView extends ViewPart implements RecordingEditor.Listener {

    private ActiveEditorTracker tracker;

    private RecordingEditor editor;

    @Override
    public final void createPartControl(final Composite parent) {
        createContent(parent);
        tracker = new ActiveEditorTracker(getSite().getPage(), this::bind);
        tracker.install();
    }

    abstract void createContent(Composite parent);

    abstract void refresh();

    abstract String selectHint();

    final RecordingEditor editor() {
        return editor;
    }

    final TraceSnapshot trace() {
        return editor != null ? editor.trace() : null;
    }

    final SelectedCall selection() {
        return editor != null ? editor.selection() : null;
    }

    final String emptyReason(final TraceSnapshot d, final SelectedCall f) {
        if (editor == null) {
            return "No Verbatime recording editor is active";
        }
        if (d == null) {
            return editor.isLoading() ? "Loading …" : "No recording loaded";
        }
        if (f == null) {
            return selectHint();
        }
        return null;
    }

    private void bind(final RecordingEditor e) {
        if (editor != null) {
            editor.removeListener(this);
        }
        editor = e;
        if (editor != null) {
            editor.addListener(this);
        }
        refresh();
    }

    @Override
    public final void selectionChanged() {
        refresh();
    }

    @Override
    public final void traceChanged() {
        refresh();
    }

    @Override
    public void dispose() {
        if (tracker != null) {
            tracker.dispose();
        }
        super.dispose();
    }
}
