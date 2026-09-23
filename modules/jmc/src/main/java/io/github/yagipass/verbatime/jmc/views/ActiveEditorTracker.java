package io.github.yagipass.verbatime.jmc.views;

import java.util.function.Consumer;

import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;

import io.github.yagipass.verbatime.jmc.RecordingEditor;

final class ActiveEditorTracker implements IPartListener2 {

    private final IWorkbenchPage page;

    private final Consumer<RecordingEditor> onBind;

    private RecordingEditor bound;

    ActiveEditorTracker(final IWorkbenchPage page, final Consumer<RecordingEditor> onBind) {
        this.page = page;
        this.onBind = onBind;
    }

    void install() {
        page.addPartListener(this);
        bound = page.getActiveEditor() instanceof final RecordingEditor e ? e : null;
        onBind.accept(bound);
    }

    void dispose() {
        page.removePartListener(this);
        if (bound != null) {
            bind(null);
        }
    }

    private void bind(final RecordingEditor e) {
        if (e == bound) {
            return;
        }
        bound = e;
        onBind.accept(e);
    }

    private void follow(final IWorkbenchPartReference ref) {
        if (ref.getPart(false) instanceof final RecordingEditor e) {
            bind(e);
        }
    }

    @Override
    public void partActivated(final IWorkbenchPartReference ref) {
        follow(ref);
    }

    @Override
    public void partBroughtToTop(final IWorkbenchPartReference ref) {
        follow(ref);
    }

    @Override
    public void partClosed(final IWorkbenchPartReference ref) {
        final IWorkbenchPart part = ref.getPart(false);
        if (bound != null && (part == bound || bound.isDisposed())) {
            bind(null);
        }
    }
}
