package io.github.yagipass.verbatime.jmc.views;

import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

final class Clipboards {

    private Clipboards() {
    }

    static void copyText(final Display display, final String text) {
        final Clipboard clipboard = new Clipboard(display);
        try {
            clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
        } finally {
            clipboard.dispose();
        }
    }
}
