package io.github.yagipass.verbatime.jmc.views;

import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TreeColumn;

import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;

abstract class AggregateTreeView<M extends AggregateTreeView.AggregateTreeModel<R>, R> extends EditorBoundView {

    interface AggregateTreeModel<R> extends LazyTreeContentProvider.Source<R> {

        SubtreeAggregate aggregate();
    }

    private final Class<R> rowType;

    private final String copyTip;

    private final String heading;

    private TreeViewer viewer;

    private M model;

    private HeaderWithCopyButton copyHead;

    AggregateTreeView(final Class<R> rowType, final String copyTip, final String heading) {
        this.rowType = rowType;
        this.copyTip = copyTip;
        this.heading = heading;
    }

    @Override
    protected final void createContent(final Composite parent) {
        final GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 4;
        layout.marginHeight = 4;
        parent.setLayout(layout);
        copyHead = HeaderWithCopyButton.create(parent, copyTip, () -> {
            if (model != null) {
                Clipboards.copyText(viewer.getControl().getDisplay(), copyText(model, r -> viewer.getExpandedState(r)));
            }
        });
        copyHead.label().setText(heading);
        viewer = new TreeViewer(parent, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        viewer.getTree().setHeaderVisible(true);
        viewer.setUseHashlookup(true);
        viewer.setContentProvider(new LazyTreeContentProvider<>(viewer, rowType, () -> model));
        ColumnViewerToolTipSupport.enableFor(viewer);
        createColumns();
        viewer.addPostSelectionChangedListener(e -> {
            final Object first = e.getStructuredSelection().getFirstElement();
            if (editor() != null && rowType.isInstance(first)) {
                editor().searchFor(methodId(rowType.cast(first)));
            }
        });
    }

    final TreeViewer viewer() {
        return viewer;
    }

    final M model() {
        return model;
    }

    final TreeColumn column(final String title, final int width, final int style,
            final Function<R, String> text, final boolean mono) {
        return Columns.addTree(viewer, title, width, style, new Columns.ColumnLabels<>(rowType, text, this::name, mono));
    }

    abstract void createColumns();

    abstract int methodId(R row);

    abstract String name(R row);

    abstract M build(TraceSnapshot d, SubtreeAggregate agg);

    abstract void setInput(M m);

    abstract String describe(TraceSnapshot d, SelectedCall f, M m);

    abstract String copyText(M m, Predicate<R> expanded);

    @Override
    protected final void refresh() {
        if (viewer == null || viewer.getControl().isDisposed()) {
            return;
        }
        final TraceSnapshot d = trace();
        final SelectedCall f = selection();
        final SubtreeAggregate agg = f != null ? f.subtree() : null;
        if (d != null && agg != null && model != null && model.aggregate() == agg) {
            return;
        }
        model = d == null || agg == null ? null : build(d, agg);
        copyHead.copy().setEnabled(model != null);
        setInput(model);
        final String empty = emptyReason(d, f);
        setContentDescription(empty != null ? empty
                : f.subtreeError() != null ? f.subtreeError() : model == null ? "Aggregating…" : describe(d, f, model));
    }

    @Override
    public final void setFocus() {
        if (viewer != null && !viewer.getControl().isDisposed()) {
            viewer.getControl().setFocus();
        }
    }
}
