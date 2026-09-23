package io.github.yagipass.verbatime.jmc;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.widgets.Composite;

final class BrowserPage implements ViewerBridge.Page {

    private final Browser browser;

    private BrowserPage(final Browser browser) {
        this.browser = browser;
    }

    static BrowserPage create(final Composite parent) {
        final Browser b = new Browser(parent, SWT.NONE);
        b.addMenuDetectListener(e -> e.doit = false);
        return new BrowserPage(b);
    }

    @Override
    public void define(final String name, final Consumer<Object[]> body) {
        new BrowserFunction(browser, name) {
            @Override
            public Object function(final Object[] args) {
                body.accept(args);
                return null;
            }
        };
    }

    @Override
    public void load(final String html) {
        browser.setText(html, true);
    }

    @Override
    public void execute(final String js) {
        if (!browser.isDisposed()) {
            browser.execute(js);
        }
    }

    @Override
    public boolean focus() {
        return !browser.isDisposed() && browser.setFocus();
    }

    @Override
    public boolean isDisposed() {
        return browser.isDisposed();
    }

    @Override
    public void dispose() {
        if (!browser.isDisposed()) {
            browser.dispose();
        }
    }
}
