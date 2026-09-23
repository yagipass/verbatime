package io.github.yagipass.verbatime.jmc;

import org.eclipse.core.runtime.IProgressMonitor;

import io.github.yagipass.verbatime.jmc.index.TraceIndexer;

final class ProgressMonitors {

    static final int TICKS = 1000;

    private ProgressMonitors() {
    }

    static TraceIndexer.ProgressListener of(final IProgressMonitor monitor) {
        final int[] reported = { 0 };
        return (done, total) -> {
            final long raw = total > 0 ? done * TICKS / total : 0;
            final int target = (int) Math.max(0, Math.min(raw, TICKS));
            if (target > reported[0]) {
                monitor.worked(target - reported[0]);
                reported[0] = target;
            }
            return monitor.isCanceled();
        };
    }
}
