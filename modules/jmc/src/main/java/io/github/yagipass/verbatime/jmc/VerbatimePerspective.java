package io.github.yagipass.verbatime.jmc;

import org.eclipse.core.runtime.ILog;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.WorkbenchException;

public final class VerbatimePerspective implements IPerspectiveFactory {

    public static final String ID = "io.github.yagipass.verbatime.jmc.perspective";

    private static final String CONTROL_VIEW = "io.github.yagipass.verbatime.jmc.controlView";

    private static final String RECORDINGS_VIEW = "io.github.yagipass.verbatime.jmc.recordingsView";

    private static final String CALL_DETAILS_VIEW = "io.github.yagipass.verbatime.jmc.frameDetailsView";

    private static final String TOP_DOWN_VIEW = "io.github.yagipass.verbatime.jmc.topDownView";

    private static final String BOTTOM_UP_VIEW = "io.github.yagipass.verbatime.jmc.bottomUpView";

    @Override
    public void createInitialLayout(final IPageLayout layout) {
        final String editorArea = layout.getEditorArea();

        final IFolderLayout left = layout.createFolder("left", IPageLayout.LEFT, 0.25f, editorArea);
        left.addView(CONTROL_VIEW);
        final IFolderLayout leftBottom = layout.createFolder("leftBottom", IPageLayout.BOTTOM, 0.6f, "left");
        leftBottom.addView(RECORDINGS_VIEW);

        final IFolderLayout bottom = layout.createFolder("bottom", IPageLayout.BOTTOM, 0.65f, editorArea);
        bottom.addView(CALL_DETAILS_VIEW);
        bottom.addView(TOP_DOWN_VIEW);
        bottom.addView(BOTTOM_UP_VIEW);

        for (final String id : new String[] { CONTROL_VIEW, RECORDINGS_VIEW, CALL_DETAILS_VIEW, TOP_DOWN_VIEW,
                BOTTOM_UP_VIEW }) {
            layout.addShowViewShortcut(id);
        }
    }

    public static void show(final IWorkbenchWindow window) {
        if (window == null) {
            return;
        }
        final IWorkbenchPage page = window.getActivePage();
        final IPerspectiveDescriptor current = page == null ? null : page.getPerspective();
        if (current != null && ID.equals(current.getId())) {
            return;
        }
        try {
            window.getWorkbench().showPerspective(ID, window);
        } catch (final WorkbenchException e) {
            ILog.get().warn("Cannot switch to the Verbatime perspective: " + e.getMessage());
        }
    }
}
