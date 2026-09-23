package io.github.yagipass.verbatime.jmc.views;

import java.util.function.Predicate;

import org.eclipse.swt.SWT;

import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;
import io.github.yagipass.verbatime.jmc.views.TopDownModel.Row;

public final class TopDownView extends AggregateTreeView<TopDownModel, Row> {

    public TopDownView() {
        super(Row.class, "Copy the call tree as text, expanded rows included, with full method names",
                "The call tree under the selected call, heaviest child first");
    }

    @Override
    protected void createColumns() {
        column("self", 100, SWT.RIGHT, r -> Formats.fmtDur(r.selfNs()), true);
        column("total", 100, SWT.RIGHT, r -> Formats.fmtDur(r.totalNs()), true);
        column("%", 60, SWT.RIGHT, r -> Formats.fmtPct(model().pct(r.totalNs())), true);
        column("calls", 90, SWT.RIGHT, r -> Formats.fmtInt(r.calls()), true);
        column("Method", 600, SWT.LEFT, Row::name, false);
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
        return "Click a call in the chart to show its call tree";
    }

    @Override
    protected TopDownModel build(final TraceSnapshot d, final SubtreeAggregate agg) {
        return TopDownModel.of(d, agg);
    }

    @Override
    protected void setInput(final TopDownModel m) {
        viewer().setInput(m);
        if (m != null) {
            viewer().setChildCount(m, 1);
            viewer().expandToLevel(m.root(), 1);
        }
    }

    @Override
    protected String describe(final TraceSnapshot d, final SelectedCall f, final TopDownModel m) {
        String desc = Formats.shortName(d.methodName(f.methodId())) + ", total " + Formats.fmtDur(m.root().totalNs());
        if (m.truncated()) {
            desc += ", truncated at " + Formats.fmtInt(SubtreeAggregate.MAX_NODES) + " paths";
        }
        return desc;
    }

    @Override
    protected String copyText(final TopDownModel m, final Predicate<Row> expanded) {
        return CopyTexts.topDownText(m, expanded);
    }
}
