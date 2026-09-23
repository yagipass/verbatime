package io.github.yagipass.verbatime.jmc.views;

import java.util.List;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

final class AncestorTable extends Canvas {

    record Row(int depth, String shortName, String fullName, String total, String pct, boolean current) {
    }

    private static final int PAD_X = 6;

    private static final int PAD_Y = 2;

    private static final int GAP = 12;

    private static final String[] TITLES = { "depth", "method", "total", "% of session" };

    private List<Row> rows = List.of();

    private String hoverTip;

    private int paintedRowH;

    AncestorTable(final Composite parent) {
        super(parent, SWT.DOUBLE_BUFFERED);
        addListener(SWT.Paint, e -> paint(e.gc));
        addListener(SWT.MouseMove, e -> {
            final int i = rowAt(e.y);
            final String tip = i >= 0 ? rows.get(i).fullName() : null;
            if (tip == null ? hoverTip != null : !tip.equals(hoverTip)) {
                hoverTip = tip;
                setToolTipText(tip);
            }
        });
        addListener(SWT.MouseExit, e -> {
            hoverTip = null;
            setToolTipText(null);
        });
    }

    void setRows(final List<Row> rows) {
        this.rows = List.copyOf(rows);
        hoverTip = null;
        setToolTipText(null);
        redraw();
    }

    private Font mono() {
        return JFaceResources.getTextFont();
    }

    private Font boldMono() {
        return JFaceResources.getFontRegistry().getBold(JFaceResources.TEXT_FONT);
    }

    private Font bold() {
        return JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
    }

    private int rowHeight(final GC gc) {
        gc.setFont(mono());
        final int mono = gc.getFontMetrics().getHeight();
        gc.setFont(getFont());
        return Math.max(mono, gc.getFontMetrics().getHeight()) + 2 * PAD_Y;
    }

    private int rowAt(final int y) {
        final int h = paintedRowH;
        if (h <= 0) {
            return -1;
        }
        final int i = (y - h) / h;
        return y >= h && i < rows.size() ? i : -1;
    }

    @Override
    public Point computeSize(final int wHint, final int hHint, final boolean changed) {
        final GC gc = new GC(this);
        try {
            final int h = rowHeight(gc) * (rows.size() + 1);
            final int[] widths = columnWidths(gc);
            int w = 2 * PAD_X + 3 * GAP;
            for (final int cw : widths) {
                w += cw;
            }
            return new Point(wHint != SWT.DEFAULT ? wHint : w, hHint != SWT.DEFAULT ? hHint : h);
        } finally {
            gc.dispose();
        }
    }

    private int[] columnWidths(final GC gc) {
        final int[] w = new int[4];
        gc.setFont(getFont());
        for (int c = 0; c < 4; c++) {
            w[c] = gc.textExtent(TITLES[c]).x;
        }
        for (final Row r : rows) {
            gc.setFont(r.current() ? boldMono() : mono());
            w[0] = Math.max(w[0], gc.textExtent(Integer.toString(r.depth())).x);
            w[2] = Math.max(w[2], gc.textExtent(r.total()).x);
            w[3] = Math.max(w[3], gc.textExtent(r.pct()).x);
            gc.setFont(r.current() ? bold() : getFont());
            w[1] = Math.max(w[1], gc.textExtent(r.shortName()).x);
        }
        return w;
    }

    private void paint(final GC gc) {
        final Point size = getSize();
        gc.setBackground(getBackground());
        gc.fillRectangle(0, 0, size.x, size.y);
        final int h = rowHeight(gc);
        paintedRowH = h;
        final int[] w = columnWidths(gc);
        final int fixed = w[0] + w[2] + w[3] + 3 * GAP + 2 * PAD_X;
        final int methodW = Math.max(40, Math.min(w[1], size.x - fixed));
        final int xDepth = PAD_X;
        final int xMethod = xDepth + w[0] + GAP;
        final int xTotal = xMethod + methodW + GAP;
        final int xPct = xTotal + w[2] + GAP;

        gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
        gc.setFont(getFont());
        drawRight(gc, TITLES[0], xDepth, w[0], 0, h);
        gc.drawText(TITLES[1], xMethod, textY(gc, 0, h), true);
        drawRight(gc, TITLES[2], xTotal, w[2], 0, h);
        drawRight(gc, TITLES[3], xPct, w[3], 0, h);
        gc.drawLine(0, h - 1, size.x, h - 1);

        int y = h;
        for (final Row r : rows) {
            gc.setForeground(getForeground());
            gc.setFont(r.current() ? boldMono() : mono());
            drawRight(gc, Integer.toString(r.depth()), xDepth, w[0], y, h);
            drawRight(gc, r.total(), xTotal, w[2], y, h);
            drawRight(gc, r.pct(), xPct, w[3], y, h);
            gc.setFont(r.current() ? bold() : getFont());
            gc.setClipping(xMethod, y, methodW, h);
            gc.drawText(clip(gc, r.shortName(), methodW), xMethod, textY(gc, y, h), true);
            gc.setClipping((Rectangle) null);
            y += h;
        }
    }

    private static int textY(final GC gc, final int rowY, final int rowH) {
        return rowY + (rowH - gc.getFontMetrics().getHeight()) / 2;
    }

    private static void drawRight(final GC gc, final String s, final int x, final int w, final int rowY, final int rowH) {
        gc.drawText(s, x + w - gc.textExtent(s).x, textY(gc, rowY, rowH), true);
    }

    private static String clip(final GC gc, final String s, final int w) {
        if (gc.textExtent(s).x <= w) {
            return s;
        }
        int n = s.length();
        while (n > 1 && gc.textExtent(s.substring(0, n) + "…").x > w) {
            n--;
        }
        return s.substring(0, n) + "…";
    }
}
