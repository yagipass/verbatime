package io.github.yagipass.verbatime.jmc.views;

import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TreeColumn;
import org.jspecify.annotations.Nullable;

abstract class AggregateTreeView<M extends AggregateTreeView.AggregateTreeModel<R>, R>
    extends EditorBoundView {

  interface AggregateTreeModel<R> extends LazyTreeContentProvider.Source<R> {

    SubtreeAggregate aggregate();
  }

  private final Class<R> rowType;

  private final String copyTip;

  private final String heading;

  private TreeViewer viewer;

  private @Nullable M model;

  private HeaderWithCopyButton copyHead;

  AggregateTreeView(Class<R> rowType, String copyTip, String heading) {
    this.rowType = rowType;
    this.copyTip = copyTip;
    this.heading = heading;
  }

  @Override
  protected final void createContent(Composite parent) {
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 4;
    layout.marginHeight = 4;
    parent.setLayout(layout);
    copyHead =
        HeaderWithCopyButton.create(
            parent,
            copyTip,
            () -> {
              if (model != null) {
                Clipboards.copyText(
                    viewer.getControl().getDisplay(),
                    copyText(model, r -> viewer.getExpandedState(r)));
              }
            });
    copyHead.label().setText(heading);
    viewer =
        new TreeViewer(
            parent, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    viewer.getTree().setHeaderVisible(true);
    viewer.setUseHashlookup(true);
    viewer.setContentProvider(new LazyTreeContentProvider<>(viewer, rowType, () -> model));
    ColumnViewerToolTipSupport.enableFor(viewer);
    createColumns();
    viewer.addPostSelectionChangedListener(
        e -> {
          Object first = e.getStructuredSelection().getFirstElement();
          if (editor() != null && rowType.isInstance(first)) {
            editor().searchFor(methodId(rowType.cast(first)));
          }
        });
  }

  final TreeViewer viewer() {
    return viewer;
  }

  final @Nullable M model() {
    return model;
  }

  final TreeColumn column(
      String title, int width, int style, Function<R, String> text, boolean mono) {
    return Columns.addTree(
        viewer, title, width, style, new Columns.ColumnLabels<>(rowType, text, this::name, mono));
  }

  abstract void createColumns();

  abstract int methodId(R row);

  abstract String name(R row);

  abstract M build(TraceSnapshot d, SubtreeAggregate agg);

  abstract void setInput(@Nullable M m);

  abstract String describe(TraceSnapshot d, SelectedCall f, M m);

  abstract String copyText(M m, Predicate<R> expanded);

  @Override
  protected final void refresh() {
    if (viewer == null || viewer.getControl().isDisposed()) {
      return;
    }
    TraceSnapshot d = trace();
    SelectedCall f = selection();
    SubtreeAggregate agg = f != null ? f.subtree() : null;
    if (d != null && agg != null && model != null && model.aggregate() == agg) {
      return;
    }
    model = d == null || agg == null ? null : build(d, agg);
    copyHead.copy().setEnabled(model != null);
    setInput(model);
    String empty = emptyReason(d, f);
    setContentDescription(
        empty != null
            ? empty
            : Objects.requireNonNull(f).subtreeError() != null
                ? f.subtreeError()
                : model == null ? "Aggregating…" : describe(Objects.requireNonNull(d), f, model));
  }

  @Override
  public final void setFocus() {
    if (viewer != null && !viewer.getControl().isDisposed()) {
      viewer.getControl().setFocus();
    }
  }
}
