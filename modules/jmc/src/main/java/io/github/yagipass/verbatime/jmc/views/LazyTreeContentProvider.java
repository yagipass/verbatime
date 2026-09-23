package io.github.yagipass.verbatime.jmc.views;

import java.util.function.Supplier;

import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;

final class LazyTreeContentProvider<R> implements ILazyTreeContentProvider {

    interface Source<R> {

        int rootCount();

        R root(int i);

        boolean hasChildren(R r);

        int childCount(R r);

        R child(R r, int i);

        R parent(R r);
    }

    private final TreeViewer viewer;

    private final Class<R> type;

    private final Supplier<Source<R>> source;

    LazyTreeContentProvider(final TreeViewer viewer, final Class<R> type, final Supplier<Source<R>> source) {
        this.viewer = viewer;
        this.type = type;
        this.source = source;
    }

    @Override
    public void updateElement(final Object parent, final int index) {
        final Source<R> src = source.get();
        if (src == null) {
            return;
        }
        final R r = type.isInstance(parent) ? src.child(type.cast(parent), index) : src.root(index);
        viewer.replace(parent, index, r);
        viewer.setHasChildren(r, src.hasChildren(r));
    }

    @Override
    public void updateChildCount(final Object element, final int currentChildCount) {
        final Source<R> src = source.get();
        final int n = src == null ? 0 : type.isInstance(element) ? src.childCount(type.cast(element)) : src.rootCount();
        if (n != currentChildCount) {
            viewer.setChildCount(element, n);
        }
    }

    @Override
    public Object getParent(final Object element) {
        final Source<R> src = source.get();
        if (src != null && type.isInstance(element)) {
            final R p = src.parent(type.cast(element));
            return p != null ? p : src;
        }
        return src;
    }
}
