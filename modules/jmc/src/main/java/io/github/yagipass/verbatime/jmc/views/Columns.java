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
import org.jspecify.annotations.Nullable;

public final class Columns {

  private Columns() {}

  static <T> TreeColumn addTree(
      TreeViewer viewer, String title, int width, int style, ColumnLabels<T> labels) {
    TreeViewerColumn col = new TreeViewerColumn(viewer, style);
    col.getColumn().setText(title);
    col.getColumn().setWidth(width);
    col.setLabelProvider(labels);
    return col.getColumn();
  }

  public static <T> TableColumn addTable(
      TableViewer viewer, String title, int width, int style, ColumnLabels<T> labels) {
    TableViewerColumn col = new TableViewerColumn(viewer, style);
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

    public ColumnLabels(
        Class<T> type, Function<T, String> text, Function<T, String> tip, boolean mono) {
      this.type = type;
      this.text = text;
      this.tip = tip;
      this.mono = mono;
    }

    @Override
    public String getText(Object element) {
      return type.isInstance(element) ? text.apply(type.cast(element)) : "";
    }

    @Override
    public @Nullable String getToolTipText(Object element) {
      return type.isInstance(element) ? tip.apply(type.cast(element)) : null;
    }

    @Override
    public @Nullable Font getFont(Object element) {
      return mono ? JFaceResources.getTextFont() : null;
    }
  }
}
