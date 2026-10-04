package io.github.yagipass.verbatime.jmc;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.widgets.Composite;
import org.jspecify.annotations.Nullable;

final class BrowserPage implements ViewerBridge.Page {

    private final Browser browser;

    private BrowserPage(Browser browser) {
        this.browser = browser;
    }

    static BrowserPage create(Composite parent) {
        Browser b = new Browser(parent, SWT.NONE);
        b.addMenuDetectListener(e -> e.doit = false);
        return new BrowserPage(b);
    }

    @Override
    public void define(String name, Consumer<Object[]> body) {
        new BrowserFunction(browser, name) {
            @Override
            public @Nullable Object function(Object[] args) {
                body.accept(args);
                return null;
            }
        };
    }

    @Override
    public void load(String html) {
        browser.setText(html, true);
    }

    @Override
    public void execute(String js) {
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
