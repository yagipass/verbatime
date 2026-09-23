package io.github.yagipass.verbatime.jmc.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

record HeaderWithCopyButton(Label label, Button copy) {

    static HeaderWithCopyButton create(final Composite parent, final String copyTip, final Runnable onCopy) {
        final Composite head = new Composite(parent, SWT.NONE);
        head.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        final GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        head.setLayout(layout);
        final Label label = new Label(head, SWT.WRAP);
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        final Button copy = new Button(head, SWT.PUSH);
        copy.setText("Copy");
        copy.setToolTipText(copyTip);
        copy.addListener(SWT.Selection, e -> onCopy.run());
        return new HeaderWithCopyButton(label, copy);
    }
}
