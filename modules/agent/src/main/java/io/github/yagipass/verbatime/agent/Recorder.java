package io.github.yagipass.verbatime.agent;

import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.StartupGate;
import io.github.yagipass.verbatime.agent.probe.TraceFileWriter;
import io.github.yagipass.verbatime.agent.probe.Tracing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.LongFunction;
import org.jspecify.annotations.Nullable;

public final class Recorder {

  private final long vmInitUptimeMs = TraceFileWriter.vmInitUptimeMs();

  private long nextRecordingId = 1;

  private @Nullable Recording currentRecording;

  public synchronized @Nullable Recording current() {
    return currentRecording;
  }

  public synchronized boolean isRecording() {
    return currentRecording != null;
  }

  public synchronized Recording start(LongFunction<Path> pathForId, String name, boolean spooled) {
    if (currentRecording != null) {
      throw new IllegalStateException("already recording #" + currentRecording.id());
    }
    Path path = pathForId.apply(nextRecordingId);
    TraceFileWriter w;
    try {
      w = TraceFileWriter.open(path, vmInitUptimeMs);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot open the trace file " + path, e);
    }
    Recording r = new Recording(nextRecordingId, name, w, spooled);
    nextRecordingId++;
    currentRecording = r;
    Tracing.start(w);
    StartupGate.release();
    Log.info(
        "recording #"
            + r.id()
            + (name.isEmpty() ? "" : " name=\"" + name + "\"")
            + " -> "
            + w.path());
    return r;
  }

  public synchronized Recording stop(String reason) {
    Recording r = currentRecording;
    if (r == null) {
      throw new IllegalStateException("not recording");
    }
    currentRecording = null;
    int flushed = Tracing.stop();
    r.writer().close();
    r.markClosed();
    Log.info(
        "recording #"
            + r.id()
            + " "
            + reason
            + ": "
            + r.writer().committedBytes()
            + " bytes"
            + (flushed > 0 ? ", " + Log.plural(flushed, "unclosed session") + " flushed" : ""));
    return r;
  }

  public synchronized @Nullable Recording stopIfRecording(String reason) {
    return currentRecording == null ? null : stop(reason);
  }
}
