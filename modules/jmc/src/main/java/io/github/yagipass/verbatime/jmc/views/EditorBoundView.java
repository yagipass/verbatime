package io.github.yagipass.verbatime.jmc.views;

import io.github.yagipass.verbatime.jmc.RecordingEditor;
import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.jspecify.annotations.Nullable;

abstract class EditorBoundView extends ViewPart implements RecordingEditor.Listener {

  private ActiveEditorTracker tracker;

  private @Nullable RecordingEditor editor;

  @Override
  public final void createPartControl(Composite parent) {
    createContent(parent);
    tracker = new ActiveEditorTracker(getSite().getPage(), this::bind);
    tracker.install();
  }

  abstract void createContent(Composite parent);

  abstract void refresh();

  abstract String selectHint();

  final @Nullable RecordingEditor editor() {
    return editor;
  }

  final @Nullable TraceSnapshot trace() {
    return editor != null ? editor.trace() : null;
  }

  final @Nullable SelectedCall selection() {
    return editor != null ? editor.selection() : null;
  }

  final @Nullable String emptyReason(@Nullable TraceSnapshot d, @Nullable SelectedCall f) {
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

  private void bind(@Nullable RecordingEditor e) {
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
