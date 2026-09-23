package io.github.yagipass.verbatime.jmc.control;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.core.runtime.IProgressMonitor;

import io.github.yagipass.verbatime.jmc.Formats;

final class Transfer {

    interface Listener {

        void progress(long bytes);

        void finished(long bytes);

        void failed(String message);

        void stopped();
    }

    private static final long IDLE_MS = 1_000;

    private final Agent agent;

    private final long recordingId;

    private final Path file;

    private final long offset;

    private final Listener listener;

    private final long idleMs;

    Transfer(final Agent agent, final long recordingId, final Path file, final long offset, final Listener listener) {
        this(agent, recordingId, file, offset, listener, IDLE_MS);
    }

    Transfer(final Agent agent, final long recordingId, final Path file, final long offset, final Listener listener,
            final long idleMs) {
        this.agent = agent;
        this.recordingId = recordingId;
        this.file = file;
        this.offset = offset;
        this.listener = listener;
        this.idleMs = idleMs;
    }

    long recordingId() {
        return recordingId;
    }

    Path file() {
        return file;
    }

    void run(final IProgressMonitor monitor) {
        long sid = -1;
        long bytes = offset;
        try {
            sid = agent.openStream(recordingId, offset);
            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                while (!monitor.isCanceled()) {
                    final byte[] b = agent.readStream(sid);
                    if (b == null) {
                        break;
                    }
                    if (b.length == 0) {
                        Thread.sleep(idleMs);
                        continue;
                    }
                    out.write(b);
                    out.flush();
                    bytes += b.length;
                    monitor.subTask(Formats.fmtBytes(bytes) + " transferred");
                    listener.progress(bytes);
                }
            }
            if (monitor.isCanceled()) {
                listener.stopped();
            } else {
                listener.finished(bytes);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.stopped();
        } catch (final Exception e) {
            if (monitor.isCanceled()) {
                listener.stopped();
            } else {
                listener.failed(String.valueOf(e.getMessage()));
            }
        } finally {
            if (sid >= 0) {
                closeStreamQuietly(sid);
            }
        }
    }

    @SuppressWarnings("EmptyCatch")
    private void closeStreamQuietly(final long sid) {
        try {
            agent.closeStream(sid);
        } catch (final IOException ignored) {
        }
    }
}
