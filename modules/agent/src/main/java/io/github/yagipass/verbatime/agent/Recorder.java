package io.github.yagipass.verbatime.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.LongFunction;

import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.StartupGate;
import io.github.yagipass.verbatime.agent.probe.TraceFileWriter;
import io.github.yagipass.verbatime.agent.probe.Tracing;

public final class Recorder {

    private long nextRecordingId = 1;

    private Recording currentRecording;

    public synchronized Recording current() {
        return currentRecording;
    }

    public synchronized boolean isRecording() {
        return currentRecording != null;
    }

    public synchronized Recording start(final LongFunction<Path> pathForId, final String name, final boolean spooled) {
        if (currentRecording != null) {
            throw new IllegalStateException("already recording #" + currentRecording.id());
        }
        final Path path = pathForId.apply(nextRecordingId);
        final TraceFileWriter w;
        try {
            w = TraceFileWriter.open(path);
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot open the trace file " + path, e);
        }
        final Recording r = new Recording(nextRecordingId, name, w, spooled);
        nextRecordingId++;
        currentRecording = r;
        Tracing.start(w);
        StartupGate.release();
        Log.info("recording #" + r.id() + (name.isEmpty() ? "" : " name=\"" + name + "\"") + " -> " + w.path());
        return r;
    }

    public synchronized Recording stop(final String reason) {
        final Recording r = currentRecording;
        if (r == null) {
            throw new IllegalStateException("not recording");
        }
        currentRecording = null;
        final int flushed = Tracing.stop();
        r.writer().close();
        r.markClosed();
        Log.info("recording #" + r.id() + " " + reason + ": " + r.writer().committedBytes() + " bytes" + (flushed > 0 ? ", " + Log.plural(flushed, "unclosed session") + " flushed" : ""));
        return r;
    }

    public synchronized Recording stopIfRecording(final String reason) {
        return currentRecording == null ? null : stop(reason);
    }
}
