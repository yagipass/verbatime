package io.github.yagipass.verbatime.jmc.views;

import java.util.function.Function;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TreeColumn;

public final class Columns {

    private Columns() {
    }

    static <T> TreeColumn addTree(final TreeViewer viewer, final String title, final int width,
            final int style, final ColumnLabels<T> labels) {
        final TreeViewerColumn col = new TreeViewerColumn(viewer, style);
        col.getColumn().setText(title);
        col.getColumn().setWidth(width);
        col.setLabelProvider(labels);
        return col.getColumn();
    }

    public static <T> TableColumn addTable(final TableViewer viewer, final String title, final int width,
            final int style, final ColumnLabels<T> labels) {
        final TableViewerColumn col = new TableViewerColumn(viewer, style);
        col.getColumn().setText(title);
        col.getColumn().setWidth(width);
        col.setLabelProvider(labels);
        return col.getColumn();
    }

    public static class ColumnLabels<T> extends ColumnLabelProvider {

        private final Class<T> type;

        private final Function<T, String> text;

        private final Function<T, String> tip;

        private final boolean mono;

        public ColumnLabels(final Class<T> type, final Function<T, String> text, final Function<T, String> tip,
                final boolean mono) {
            this.type = type;
            this.text = text;
            this.tip = tip;
            this.mono = mono;
        }

        @Override
        public String getText(final Object element) {
            return type.isInstance(element) ? text.apply(type.cast(element)) : "";
        }

        @Override
        public String getToolTipText(final Object element) {
            return type.isInstance(element) ? tip.apply(type.cast(element)) : null;
        }

        @Override
        public Font getFont(final Object element) {
            return mono ? JFaceResources.getTextFont() : null;
        }
    }
}
