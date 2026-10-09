package io.github.yagipass.verbatime.jmc.control;

import java.nio.file.Path;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public final class TransferJob extends Job {

  private static final Object FAMILY = new Object();

  private final Transfer pull;

  TransferJob(Transfer pull) {
    super("Transferring recording #" + pull.recordingId());
    this.pull = pull;
    setUser(false);
    setPriority(LONG);
  }

  public static boolean isTransferring(Path file) {
    Path wanted = file.toAbsolutePath();
    for (Job j : Job.getJobManager().find(FAMILY)) {
      if (j instanceof TransferJob p && p.file().toAbsolutePath().equals(wanted)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean belongsTo(Object family) {
    return FAMILY.equals(family);
  }

  @Override
  protected IStatus run(IProgressMonitor monitor) {
    monitor.beginTask(getName(), IProgressMonitor.UNKNOWN);
    try {
      pull.run(monitor);
      return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
    } finally {
      monitor.done();
    }
  }

  @Override
  protected void canceling() {
    Thread t = getThread();
    if (t != null) {
      t.interrupt();
    }
  }

  private Path file() {
    return pull.file();
  }
}
