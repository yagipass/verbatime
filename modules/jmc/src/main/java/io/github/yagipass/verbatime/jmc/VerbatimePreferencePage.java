package io.github.yagipass.verbatime.jmc;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public final class VerbatimePreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public VerbatimePreferencePage() {
        super(GRID);
        setPreferenceStore(Preferences.get());
        setDescription("Recordings transferred from an agent by the Verbatime Control view are saved under "
                + "<recordings directory>/<host_port>/ and listed in the Verbatime Recordings view. "
                + "The default is verbatime/recordings in the workspace, next to persisted_jmx_data. "
                + "In the JMC application the workspace is ~/.jmc/<version>.");
    }

    @Override
    public void init(final IWorkbench workbench) {
    }

    @Override
    protected void createFieldEditors() {
        addField(new RecordingsDirEditor(Preferences.RECORDINGS_DIR, "&Recordings directory:", getFieldEditorParent()));
    }

    private static final class RecordingsDirEditor extends DirectoryFieldEditor {

        private RecordingsDirEditor(final String name, final String label, final Composite parent) {
            super(name, label, parent);
            setErrorMessage("Not a directory");
        }

        @Override
        protected boolean doCheckState() {
            try {
                final Path p = Path.of(getTextControl().getText().trim());
                return !Files.exists(p) || Files.isDirectory(p);
            } catch (final InvalidPathException e) {
                return false;
            }
        }
    }
}
