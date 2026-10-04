package io.github.yagipass.verbatime.jmc.views;

import java.util.function.Consumer;

import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.jspecify.annotations.Nullable;

import io.github.yagipass.verbatime.jmc.RecordingEditor;

final class ActiveEditorTracker implements IPartListener2 {

    private final IWorkbenchPage page;

    private final Consumer<@Nullable RecordingEditor> onBind;

    private @Nullable RecordingEditor bound;

    ActiveEditorTracker(IWorkbenchPage page, Consumer<@Nullable RecordingEditor> onBind) {
        this.page = page;
        this.onBind = onBind;
    }

    void install() {
        page.addPartListener(this);
        bound = page.getActiveEditor() instanceof RecordingEditor e ? e : null;
        onBind.accept(bound);
    }

    void dispose() {
        page.removePartListener(this);
        if (bound != null) {
            bind(null);
        }
    }

    private void bind(@Nullable RecordingEditor e) {
        if (e == bound) {
            return;
        }
        bound = e;
        onBind.accept(e);
    }

    private void follow(IWorkbenchPartReference ref) {
        if (ref.getPart(false) instanceof RecordingEditor e) {
            bind(e);
        }
    }

    @Override
    public void partActivated(IWorkbenchPartReference ref) {
        follow(ref);
    }

    @Override
    public void partBroughtToTop(IWorkbenchPartReference ref) {
        follow(ref);
    }

    @Override
    public void partClosed(IWorkbenchPartReference ref) {
        IWorkbenchPart part = ref.getPart(false);
        if (bound != null && (part == bound || bound.isDisposed())) {
            bind(null);
        }
    }
}
