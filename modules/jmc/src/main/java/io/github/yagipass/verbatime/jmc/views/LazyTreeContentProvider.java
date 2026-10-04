package io.github.yagipass.verbatime.jmc.views;

import java.util.function.Supplier;

import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.jspecify.annotations.Nullable;

final class LazyTreeContentProvider<R> implements ILazyTreeContentProvider {

    interface Source<R> {

        int rootCount();

        R root(int i);

        boolean hasChildren(R r);

        int childCount(R r);

        R child(R r, int i);

        @Nullable
        R parent(R r);
    }

    private final TreeViewer viewer;

    private final Class<R> type;

    private final Supplier<@Nullable Source<R>> source;

    LazyTreeContentProvider(TreeViewer viewer, Class<R> type, Supplier<@Nullable Source<R>> source) {
        this.viewer = viewer;
        this.type = type;
        this.source = source;
    }

    @Override
    public void updateElement(Object parent, int index) {
        Source<R> src = source.get();
        if (src == null) {
            return;
        }
        R r = type.isInstance(parent) ? src.child(type.cast(parent), index) : src.root(index);
        viewer.replace(parent, index, r);
        viewer.setHasChildren(r, src.hasChildren(r));
    }

    @Override
    public void updateChildCount(Object element, int currentChildCount) {
        Source<R> src = source.get();
        int n = src == null ? 0 : type.isInstance(element) ? src.childCount(type.cast(element)) : src.rootCount();
        if (n != currentChildCount) {
            viewer.setChildCount(element, n);
        }
    }

    @Override
    public @Nullable Object getParent(Object element) {
        Source<R> src = source.get();
        if (src != null && type.isInstance(element)) {
            R p = src.parent(type.cast(element));
            return p != null ? p : src;
        }
        return src;
    }
}
