package io.github.yagipass.verbatime.jmc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;

public final class RecordingEditorInput implements IPathEditorInput {

    public static final String EDITOR_ID = "io.github.yagipass.verbatime.jmc.editor";

    private final Path file;

    public RecordingEditorInput(final Path file) {
        this.file = file.toAbsolutePath();
    }

    public static RecordingEditor openOrReport(final IWorkbenchPage page, final Path file, final Consumer<String> report) {
        try {
            final IEditorPart part = page.openEditor(new RecordingEditorInput(file), EDITOR_ID);
            return part instanceof final RecordingEditor e ? e : null;
        } catch (final PartInitException e) {
            report.accept("Cannot open " + file.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public IPath getPath() {
        return new org.eclipse.core.runtime.Path(file.toString());
    }

    @Override
    public boolean exists() {
        return Files.exists(file);
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
        return ImageDescriptor.getMissingImageDescriptor();
    }

    @Override
    public String getName() {
        return file.getFileName().toString();
    }

    @Override
    public IPersistableElement getPersistable() {
        return null;
    }

    @Override
    public String getToolTipText() {
        return file.toString();
    }

    @Override
    public <T> T getAdapter(final Class<T> adapter) {
        return adapter.isInstance(this) ? adapter.cast(this) : null;
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof final RecordingEditorInput other && file.equals(other.file);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(file);
    }
}
