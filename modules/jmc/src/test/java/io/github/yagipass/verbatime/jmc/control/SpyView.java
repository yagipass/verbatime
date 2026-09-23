package io.github.yagipass.verbatime.jmc.control;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.yagipass.verbatime.jmc.control.ControlPresenter.EditorHandle;
import io.github.yagipass.verbatime.jmc.control.ControlPresenter.ViewState;

final class SpyView implements ControlPresenter.View {

    static final class FakeEditorHandle implements EditorHandle {

        final boolean open = true;

        boolean loading;

        final List<Boolean> reloads = new ArrayList<>();

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public boolean isLoading() {
            return loading;
        }

        @Override
        public void reload(final boolean recording) {
            reloads.add(recording);
        }
    }

    final List<ViewState> states = new ArrayList<>();

    final List<String> messages = new ArrayList<>();

    final List<String[]> candidates = new ArrayList<>();

    int rootAccepted;

    final List<Path> opened = new ArrayList<>();

    final FakeEditorHandle editor = new FakeEditorHandle();

    int recordingsChanged;

    ViewState last() {
        return states.get(states.size() - 1);
    }

    String lastMessage() {
        return messages.get(messages.size() - 1);
    }

    @Override
    public void render(final ViewState p) {
        states.add(p);
    }

    @Override
    public void message(final String text) {
        messages.add(text);
    }

    @Override
    public void rootCandidates(final String[] specs) {
        candidates.add(specs);
    }

    @Override
    public void rootAccepted() {
        rootAccepted++;
    }

    @Override
    public EditorHandle openEditor(final Path file) {
        opened.add(file);
        return editor;
    }

    @Override
    public void recordingsChanged() {
        recordingsChanged++;
    }
}
