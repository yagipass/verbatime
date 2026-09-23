package io.github.yagipass.verbatime.jmc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import io.github.yagipass.verbatime.jmc.control.ControlPresenter;

public final class Preferences extends ScopedPreferenceStore implements ControlPresenter.Settings {

    private static final String QUALIFIER = "io.github.yagipass.verbatime.jmc";

    public static final String AGENT_TARGET = "agentTarget";

    public static final String RECORDINGS_DIR = "recordingsDir";

    private static final String ROOTS = "roots";

    private static final Path RECORDINGS_SUBDIR = Path.of("verbatime", "recordings");

    private static Preferences instance;

    public static synchronized Preferences get() {
        if (instance == null) {
            instance = new Preferences();
        }
        return instance;
    }

    private Preferences() {
        super(InstanceScope.INSTANCE, QUALIFIER);
        setDefault(AGENT_TARGET, "localhost:7091");
        setDefault(RECORDINGS_DIR, recordingsDirIn(workspaceDir()).toString());
    }

    @Override
    public String roots() {
        return getString(ROOTS);
    }

    @Override
    public Path recordingsDir() {
        return resolveRecordingsDir(getString(RECORDINGS_DIR), recordingsDirIn(workspaceDir()));
    }

    @Override
    @SuppressWarnings("EmptyCatch")
    public void save(final String target, final String roots) {
        setValue(AGENT_TARGET, target);
        setValue(ROOTS, roots);
        try {
            save();
        } catch (final IOException ignored) {
        }
    }

    static Path resolveRecordingsDir(final String pref, final Path dflt) {
        final String s = pref == null ? "" : pref.trim();
        return s.isEmpty() ? dflt : Path.of(s);
    }

    static Path recordingsDirIn(final Path workspace) {
        return workspace.resolve(RECORDINGS_SUBDIR);
    }

    @SuppressWarnings("EmptyCatch")
    private static Path workspaceDir() {
        try {
            final IPath ws = Platform.getLocation();
            if (ws != null) {
                final Path p = ws.toFile().toPath();
                if (Files.isDirectory(p)) {
                    return p;
                }
            }
        } catch (final IllegalStateException unset) {
        }
        return Path.of(System.getProperty("user.home", "."));
    }
}
