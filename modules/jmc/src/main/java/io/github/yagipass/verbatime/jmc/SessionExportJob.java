package io.github.yagipass.verbatime.jmc;

import java.nio.file.Path;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

import io.github.yagipass.verbatime.jmc.export.SessionExporter;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;

final class SessionExportJob {

    private SessionExportJob() {
    }

    static void schedule(final Shell parent, final TraceSnapshot data, final Session s, final long floorNs,
            final Path dest) {
        final UiThread ui = UiThread.of(parent.getDisplay());
        final Job job = Job.create("Exporting session #" + s.seq + " of " + data.path.getFileName(), monitor -> {
            monitor.beginTask(dest.getFileName().toString(), ProgressMonitors.TICKS);
            try {
                final SessionExporter.Result r = SessionExporter.export(data, s, floorNs, dest, ProgressMonitors.of(monitor));
                ui.post(() -> MessageDialog.openInformation(shellOrNull(parent), "Export session",
                        SessionExportTexts.summary(dest, s, floorNs, r)));
                return Status.OK_STATUS;
            } catch (final TraceIndexer.CancelledException c) {
                return Status.CANCEL_STATUS;
            } catch (final Exception e) {
                ui.post(() -> MessageDialog.openError(shellOrNull(parent), "Export session",
                        "Failed to export session #" + s.seq + " to " + dest + ":\n" + e));
                return Status.OK_STATUS;
            } finally {
                data.buffer.release();
                monitor.done();
            }
        });
        job.setUser(true);
        job.schedule();
    }

    private static Shell shellOrNull(final Shell s) {
        return s.isDisposed() ? null : s;
    }
}
