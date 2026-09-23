package io.github.yagipass.verbatime.jmc.views;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;
import io.github.yagipass.verbatime.jmc.views.BottomUpModel.Row;
import io.github.yagipass.verbatime.jmc.views.BottomUpModel.SortKey;

public final class BottomUpView extends AggregateTreeView<BottomUpModel, Row> {

    private final Map<SortKey, TreeColumn> columns = new EnumMap<>(SortKey.class);

    public BottomUpView() {
        super(Row.class,
                "Copy the table as text in the current sort order, expanded rows included, with full method names",
                "Callers of every method in the selected call's subtree, expand a row to walk up");
    }

    @Override
    protected void createColumns() {
        column(SortKey.SELF, "self", 100, SWT.RIGHT, r -> Formats.fmtDur(r.selfNs()), true);
        column(SortKey.TOTAL, "total", 100, SWT.RIGHT, r -> Formats.fmtDur(r.totalNs()), true);
        column(null, "%", 60, SWT.RIGHT, r -> Formats.fmtPct(model().pct(r.selfNs())), true);
        column(SortKey.CALLS, "calls", 90, SWT.RIGHT, r -> Formats.fmtInt(r.calls()), true);
        column(SortKey.METHOD, "Method", 600, SWT.LEFT, Row::name, false);
    }

    private void column(final SortKey key, final String title, final int width, final int style,
            final Function<Row, String> text, final boolean mono) {
        final TreeColumn tc = column(title, width, style, text, mono);
        if (key != null) {
            tc.addListener(SWT.Selection, e -> toggleSort(key));
            columns.put(key, tc);
        }
    }

    private void toggleSort(final SortKey key) {
        final BottomUpModel m = model();
        if (m == null) {
            return;
        }
        m.toggleSort(key);
        applySortIndicator();
        viewer().refresh();
    }

    private void applySortIndicator() {
        final Tree tree = viewer().getTree();
        final BottomUpModel m = model();
        if (m == null) {
            tree.setSortColumn(null);
            tree.setSortDirection(SWT.NONE);
            return;
        }
        tree.setSortColumn(columns.get(m.sortKey()));
        tree.setSortDirection(m.descending() ? SWT.DOWN : SWT.UP);
    }

    @Override
    protected int methodId(final Row row) {
        return row.methodId();
    }

    @Override
    protected String name(final Row row) {
        return row.name();
    }

    @Override
    protected String selectHint() {
        return "Click a call in the chart to aggregate its subtree";
    }

    @Override
    protected BottomUpModel build(final TraceSnapshot d, final SubtreeAggregate agg) {
        final BottomUpModel previous = model();
        final SortKey sortKey = previous != null ? previous.sortKey() : SortKey.SELF;
        final boolean descending = previous == null || previous.descending();
        return BottomUpModel.of(d, agg, sortKey, descending);
    }

    @Override
    protected void setInput(final BottomUpModel m) {
        viewer().setInput(m);
        if (m != null) {
            viewer().setChildCount(m, m.size());
        }
        applySortIndicator();
    }

    @Override
    protected String describe(final TraceSnapshot d, final SelectedCall f, final BottomUpModel m) {
        final String where = Formats.shortName(d.methodName(f.methodId()));
        String desc = Formats.fmtInt(m.size()) + " methods in " + where + ", total " + Formats.fmtDur(m.rootTotalNs());
        if (m.truncated()) {
            desc += ", call tree truncated at " + Formats.fmtInt(SubtreeAggregate.MAX_NODES) + " paths";
        }
        return desc;
    }

    @Override
    protected String copyText(final BottomUpModel m, final Predicate<Row> expanded) {
        return CopyTexts.bottomUpText(m, expanded);
    }
}
