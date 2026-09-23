package io.github.yagipass.verbatime.jmc.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

public final class CallDetailsView extends EditorBoundView {

    private static final int WRAP_WIDTH_HINT = 120;

    private static final int WHEEL_STEP = 20;

    private Composite root;

    private StackLayout rootLayout;

    private Label emptyLabel;

    private ScrolledComposite factsScroller;

    private Composite facts;

    private Text sigText;

    private Text fullText;

    private Label row1Label;

    private Text atText;

    private Label row2Label;

    private Label gcLabel;

    private Label marksLabel;

    private Label ancestorsHead;

    private AncestorTable ancestors;

    @Override
    protected void createContent(final Composite parent) {
        root = new Composite(parent, SWT.NONE);
        rootLayout = new StackLayout();
        root.setLayout(rootLayout);
        emptyLabel = new Label(root, SWT.WRAP | SWT.CENTER);
        createFacts(root);
        rootLayout.topControl = emptyLabel;
    }

    private void createFacts(final Composite parent) {
        factsScroller = new ScrolledComposite(parent, SWT.V_SCROLL);
        factsScroller.setExpandHorizontal(true);
        factsScroller.setExpandVertical(true);
        factsScroller.addListener(SWT.Resize, e -> fitFacts());
        facts = new Composite(factsScroller, SWT.NONE);
        facts.setLayout(new GridLayout(1, false));
        factsScroller.setContent(facts);
        sigText = readOnlyText(facts);
        sigText.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
        fullText = readOnlyText(facts);
        fullText.setForeground(facts.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
        row1Label = wrapLabel(facts);
        atText = readOnlyText(facts);
        row2Label = wrapLabel(facts);
        row2Label.setForeground(facts.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
        gcLabel = wrapLabel(facts);
        gcLabel.setForeground(facts.getDisplay().getSystemColor(SWT.COLOR_DARK_MAGENTA));
        marksLabel = wrapLabel(facts);
        marksLabel.setForeground(facts.getDisplay().getSystemColor(SWT.COLOR_DARK_YELLOW));

        final Composite actions = new Composite(facts, SWT.NONE);
        actions.setLayout(new RowLayout());
        final Button zoom = new Button(actions, SWT.PUSH);
        zoom.setText("Zoom to this call");
        zoom.addListener(SWT.Selection, e -> {
            if (editor() != null) {
                editor().zoomTo(editor().selection());
            }
        });
        final Button highlight = new Button(actions, SWT.PUSH);
        highlight.setText("Highlight this method");
        highlight.addListener(SWT.Selection, e -> {
            final SelectedCall f = selection();
            if (f != null) {
                editor().searchFor(f.methodId());
            }
        });
        final Button export = new Button(actions, SWT.PUSH);
        export.setText("Export session…");
        export.addListener(SWT.Selection, e -> {
            if (editor() != null) {
                editor().openExportDialog();
            }
        });

        ancestorsHead = HeaderWithCopyButton.create(facts,
                "Copy the ancestor chain as text, one call per line with full method names", () -> {
                    final TraceSnapshot d = trace();
                    final SelectedCall f = selection();
                    if (d != null && f != null) {
                        Clipboards.copyText(facts.getDisplay(), CopyTexts.ancestorText(d, f));
                    }
                }).label();

        ancestors = new AncestorTable(facts);
        final GridData ancestorsData = new GridData(SWT.FILL, SWT.TOP, true, false);
        ancestorsData.widthHint = WRAP_WIDTH_HINT;
        ancestors.setLayoutData(ancestorsData);

        forwardWheel(sigText);
        forwardWheel(fullText);
        forwardWheel(atText);
    }

    private void forwardWheel(final Control c) {
        c.addListener(SWT.MouseWheel, e -> {
            if (facts.getSize().y <= factsScroller.getClientArea().height) {
                return;
            }
            final Point origin = factsScroller.getOrigin();
            factsScroller.setOrigin(origin.x, origin.y - e.count * WHEEL_STEP);
            e.doit = false;
        });
    }

    private static Text readOnlyText(final Composite parent) {
        final Text t = new Text(parent, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP);
        t.setBackground(parent.getBackground());
        t.setLayoutData(wrapData());
        return t;
    }

    private static Label wrapLabel(final Composite parent) {
        final Label l = new Label(parent, SWT.WRAP);
        l.setLayoutData(wrapData());
        return l;
    }

    private static GridData wrapData() {
        final GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.widthHint = WRAP_WIDTH_HINT;
        return gd;
    }

    @Override
    protected String selectHint() {
        return "Click a call in the chart to show its details";
    }

    @Override
    protected void refresh() {
        if (root == null || root.isDisposed()) {
            return;
        }
        final TraceSnapshot d = trace();
        final SelectedCall f = selection();
        final String empty = emptyReason(d, f);
        if (empty != null) {
            showEmpty(empty);
            return;
        }
        final String name = d.methodName(f.methodId());
        sigText.setText(Formats.signature(name));
        fullText.setText(name);
        row1Label.setText("total " + Formats.fmtDur(f.durNs()) + ", self " + Formats.fmtDur(f.effectiveSelfNs())
                + ", start " + Formats.fmtTs(f.startNs()));
        atText.setText("at " + Formats.fmtWall(d.wallClock(f.startNs())));
        row2Label.setText("depth " + f.depth() + ", on " + d.threadName(f.tid()) + ", "
                + CopyTexts.sessionText(d, f.tid(), f.startNs()));
        final List<String> marks = new ArrayList<>();
        if (f.thrown()) {
            marks.add("ended by throw: " + d.exceptionName(f.exceptionId()));
        }
        if (f.unclosed()) {
            marks.add("unclosed: the log ended before close");
        }
        marksLabel.setText(String.join("\n", marks));
        ((GridData) marksLabel.getLayoutData()).exclude = marks.isEmpty();
        marksLabel.setVisible(!marks.isEmpty());
        final String gc = CopyTexts.gcText(d.gc.overlap(f.startNs(), f.durNs()), f.durNs());
        gcLabel.setText(gc == null ? "" : gc);
        ((GridData) gcLabel.getLayoutData()).exclude = gc == null;
        gcLabel.setVisible(gc != null);

        final long sessionDurNs = CopyTexts.sessionDurNs(d, f.tid(), f.startNs());
        final List<SelectedCall.Ancestor> chain = f.pathFromRoot();
        final int top = chain.get(0).depth();
        ancestorsHead.setText(top == 0 ? "Ancestors, root first"
                : "Ancestors from depth " + top + ", the calls above are outside the loaded window");
        final List<AncestorTable.Row> rows = new ArrayList<>(chain.size());
        for (final SelectedCall.Ancestor a : chain) {
            final String full = d.methodName(a.methodId());
            rows.add(new AncestorTable.Row(a.depth(), Formats.shortName(full), full, Formats.fmtDur(a.durNs()),
                    CopyTexts.pctOfSession(a.durNs(), sessionDurNs), a.depth() == f.depth() && a.startNs() == f.startNs()));
        }
        ancestors.setRows(rows);

        rootLayout.topControl = factsScroller;
        facts.layout(true, true);
        root.layout();
        fitFacts();
    }

    private void fitFacts() {
        if (factsScroller == null || factsScroller.isDisposed()) {
            return;
        }
        final int width = factsScroller.getClientArea().width;
        factsScroller.setMinSize(facts.computeSize(width > 0 ? width : SWT.DEFAULT, SWT.DEFAULT));
    }

    private void showEmpty(final String text) {
        ancestors.setRows(List.of());
        emptyLabel.setText(text);
        rootLayout.topControl = emptyLabel;
        root.layout();
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public void setFocus() {
        if (root == null || root.isDisposed()) {
            return;
        }
        if (rootLayout.topControl == factsScroller) {
            factsScroller.setFocus();
        } else {
            root.setFocus();
        }
    }
}
