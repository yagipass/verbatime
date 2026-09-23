package io.github.yagipass.verbatime.jmc;

import java.util.function.BooleanSupplier;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;

public interface UiThread {

    void post(Runnable r);

    void postAfter(int delayMs, Runnable r);

    default void post(final BooleanSupplier stillWanted, final Runnable r) {
        post(() -> {
            if (stillWanted.getAsBoolean()) {
                r.run();
            }
        });
    }

    static UiThread of(final Widget anchor) {
        return new Guarded(anchor.getDisplay(), () -> !anchor.isDisposed());
    }

    static UiThread of(final Display display) {
        return new Guarded(display, () -> !display.isDisposed());
    }

    final class Guarded implements UiThread {

        private final Display display;

        private final BooleanSupplier alive;

        private Guarded(final Display display, final BooleanSupplier alive) {
            this.display = display;
            this.alive = alive;
        }

        @Override
        public void post(final Runnable r) {
            if (display.isDisposed()) {
                return;
            }
            display.asyncExec(() -> {
                if (alive.getAsBoolean()) {
                    r.run();
                }
            });
        }

        @Override
        public void postAfter(final int delayMs, final Runnable r) {
            if (display.isDisposed()) {
                return;
            }
            final Runnable guarded = () -> {
                if (alive.getAsBoolean()) {
                    r.run();
                }
            };
            if (Thread.currentThread().equals(display.getThread())) {
                display.timerExec(delayMs, guarded);
            } else {
                display.asyncExec(() -> {
                    if (!display.isDisposed()) {
                        display.timerExec(delayMs, guarded);
                    }
                });
            }
        }
    }
}
