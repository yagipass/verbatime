package io.github.yagipass.verbatime.agent.jmx;

import io.github.yagipass.verbatime.agent.Config;
import io.github.yagipass.verbatime.agent.Recorder;
import io.github.yagipass.verbatime.agent.Recording;
import io.github.yagipass.verbatime.agent.RootSpec;
import io.github.yagipass.verbatime.agent.Roots;
import io.github.yagipass.verbatime.agent.Transformer;
import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.StartupGate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.management.ObjectName;
import org.jspecify.annotations.Nullable;

public final class VerbatimeControl implements VerbatimeControlMBean {

  public static final String OBJECT_NAME = "verbatime:type=Control";

  private static final int PROTOCOL_VERSION = 4;

  private static final int MAX_SEARCH_RESULTS = 1000;

  private static final DateTimeFormatter FILE_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault());

  private final Config config;

  private final Transformer transformer;

  private final Roots roots;

  private final Recorder recorder;

  private final @Nullable Path outPath;

  private final @Nullable Path configuredSpoolDir;

  private final RecordingStreams streams = new RecordingStreams(this::discardIfDelivered);

  private @Nullable Path spoolDir;

  private boolean spoolDirIsTemp;

  private @Nullable Recording lastRecording;

  public VerbatimeControl(Config config, Transformer transformer, Roots roots, Recorder recorder) {
    this.config = config;
    this.transformer = transformer;
    this.roots = roots;
    this.recorder = recorder;
    this.outPath = config.out() == null ? null : Path.of(config.out()).toAbsolutePath();
    this.configuredSpoolDir =
        config.spoolDir() == null ? null : Path.of(config.spoolDir()).toAbsolutePath();
  }

  @Override
  public synchronized long startRecording(@Nullable String name) {
    Recording current = recorder.current();
    if (current != null) {
      throw new IllegalStateException("already recording #" + current.id());
    }
    if (roots.specs().isEmpty()) {
      throw new IllegalStateException("no roots are set. Apply roots before starting a recording");
    }
    discardLast();
    String cleaned = name == null ? "" : name.replaceAll("[\\r\\n]", " ").trim();
    boolean spooled = outPath == null;
    return recorder.start(id -> spooled ? spoolFileFor(id) : outPath, cleaned, spooled).id();
  }

  @Override
  public synchronized void stopRecording() {
    if (!recorder.isRecording()) {
      throw new IllegalStateException("not recording");
    }
    lastRecording = recorder.stop("stopped");
  }

  public synchronized void shutdown() {
    Recording stopped = recorder.stopIfRecording("closed at shutdown");
    if (stopped != null) {
      lastRecording = stopped;
    }
    streams.closeAll();
    discardLast();
    if (spoolDirIsTemp && spoolDir != null) {
      try {
        Files.deleteIfExists(spoolDir);
      } catch (IOException e) {
        Log.warn("could not remove the spool directory " + spoolDir + ": " + e);
      }
    }
  }

  @Override
  public String[] searchMethods(@Nullable String query, int max) {
    String q = query == null ? "" : query.trim();
    if (q.isEmpty() || max <= 0) {
      return new String[0];
    }
    return MethodRegistry.search(q, Math.min(max, MAX_SEARCH_RESULTS));
  }

  @Override
  public synchronized void replaceRoots(@Nullable String @Nullable [] specs) {
    Recording current = recorder.current();
    if (current != null) {
      throw new IllegalStateException(
          "cannot change roots while recording #" + current.id() + " is running");
    }
    List<RootSpec> parsed = new ArrayList<>();
    if (specs != null) {
      for (String s : specs) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) {
          continue;
        }
        RootSpec spec = RootSpec.parse(t);
        config.requireInstrumentable(spec);
        parsed.add(spec);
      }
    }
    roots.replaceRoots(parsed);
  }

  @Override
  public synchronized String[] status() {
    List<String> l = new ArrayList<>();
    Recording currentRecording = recorder.current();
    l.add("v=" + PROTOCOL_VERSION);
    l.add("pid=" + ProcessHandle.current().pid());
    l.add("state=" + (currentRecording != null ? "recording" : "idle"));
    String gateState = StartupGate.stateName();
    if (gateState != null) {
      l.add("waitstart.state=" + gateState);
    }
    if (outPath != null) {
      l.add("out=" + outPath);
    }
    if (spoolDir != null) {
      l.add("spoolDir=" + spoolDir);
    }
    List<RootSpec> specs = roots.specs();
    l.add("roots=" + specs.size());
    for (int i = 0; i < specs.size(); i++) {
      RootSpec s = specs.get(i);
      l.add("root." + i + "=" + (roots.isResolved(s) ? "ok" : "pending") + " " + s);
    }
    l.add("include=" + config.includeText());
    l.add("exclude=" + config.excludeText());
    l.add("instrumentedClasses=" + transformer.instrumentedClasses());
    l.add("instrumentedMethods=" + transformer.instrumentedMethods());
    l.add("failedClasses=" + transformer.failedClasses());
    l.add("idLimitSkippedClasses=" + transformer.idLimitSkippedClasses());
    if (currentRecording != null) {
      l.add("recording.id=" + currentRecording.id());
      l.add("recording.name=" + currentRecording.name());
      l.add("recording.file=" + currentRecording.writer().path().getFileName());
      l.add("recording.startEpochMs=" + currentRecording.writer().startEpochMs());
      l.add("recording.bytes=" + currentRecording.writer().committedBytes());
      if (currentRecording.writer().hasFailed()) {
        l.add("recording.truncated=true");
      }
    }
    if (lastRecording != null) {
      l.add("lastRecording.id=" + lastRecording.id());
      l.add("lastRecording.name=" + lastRecording.name());
      l.add("lastRecording.file=" + lastRecording.writer().path().getFileName());
      l.add("lastRecording.startEpochMs=" + lastRecording.writer().startEpochMs());
      l.add("lastRecording.bytes=" + lastRecording.writer().committedBytes());
      if (lastRecording.writer().hasFailed()) {
        l.add("lastRecording.truncated=true");
      }
    }
    return l.toArray(new String[0]);
  }

  @Override
  public synchronized long openStream(long recordingId, long fromOffset) {
    return streams.open(recordingById(recordingId), fromOffset);
  }

  @Override
  public byte @Nullable [] readStream(long streamId) {
    return streams.read(streamId);
  }

  @Override
  public void closeStream(long streamId) {
    streams.close(streamId);
  }

  public void registerMBean() throws Exception {
    ManagementFactory.getPlatformMBeanServer().registerMBean(this, new ObjectName(OBJECT_NAME));
  }

  private static void deleteSpoolFile(Recording r) {
    try {
      Files.deleteIfExists(r.writer().path());
      Log.info(
          "recording #"
              + r.id()
              + (r.delivered() ? " delivered, spool file removed" : ": spool file removed"));
    } catch (IOException e) {
      Log.warn("could not remove the spool file " + r.writer().path() + ": " + e);
    }
  }

  private Path spoolFileFor(long id) {
    return spoolDir().resolve("rec-" + id + "-" + FILE_STAMP.format(Instant.now()) + ".vbtm");
  }

  private Path spoolDir() {
    if (spoolDir == null) {
      try {
        if (configuredSpoolDir != null) {
          Files.createDirectories(configuredSpoolDir);
          spoolDir = configuredSpoolDir;
        } else {
          spoolDir = Files.createTempDirectory("vbtm-" + ProcessHandle.current().pid() + "-");
          spoolDirIsTemp = true;
        }
      } catch (IOException e) {
        throw new UncheckedIOException("cannot create the spool directory", e);
      }
    }
    return spoolDir;
  }

  private void discardLast() {
    Recording r = lastRecording;
    if (r == null) {
      return;
    }
    streams.closeAllOf(r);
    lastRecording = null;
    if (r.spooled()) {
      deleteSpoolFile(r);
    }
  }

  private synchronized void discardIfDelivered(Recording r) {
    if (!r.spooled() || !r.closed() || !r.delivered() || streams.hasStreamsOf(r)) {
      return;
    }
    if (lastRecording == r) {
      lastRecording = null;
    }
    deleteSpoolFile(r);
  }

  private Recording recordingById(long recordingId) {
    Recording current = recorder.current();
    if (current != null && current.id() == recordingId) {
      return current;
    }
    if (lastRecording != null && lastRecording.id() == recordingId) {
      return lastRecording;
    }
    throw new IllegalArgumentException("unknown recording id " + recordingId);
  }
}
