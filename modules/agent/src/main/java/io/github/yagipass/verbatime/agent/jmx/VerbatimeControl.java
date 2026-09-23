package io.github.yagipass.verbatime.agent.jmx;

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

import io.github.yagipass.verbatime.agent.Config;
import io.github.yagipass.verbatime.agent.Recorder;
import io.github.yagipass.verbatime.agent.Recording;
import io.github.yagipass.verbatime.agent.RootSpec;
import io.github.yagipass.verbatime.agent.Roots;
import io.github.yagipass.verbatime.agent.Transformer;
import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.StartupGate;

public final class VerbatimeControl implements VerbatimeControlMBean {

    public static final String OBJECT_NAME = "verbatime:type=Control";

    private static final int PROTOCOL_VERSION = 4;

    private static final int MAX_SEARCH_RESULTS = 1000;

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final Config config;

    private final Transformer transformer;

    private final Roots roots;

    private final Recorder recorder;

    private final Path outPath;

    private final Path configuredSpoolDir;

    private final RecordingStreams streams = new RecordingStreams(this::discardIfDelivered);

    private Path spoolDir;

    private boolean spoolDirIsTemp;

    private Recording lastRecording;

    public VerbatimeControl(final Config config, final Transformer transformer, final Roots roots, final Recorder recorder) {
        this.config = config;
        this.transformer = transformer;
        this.roots = roots;
        this.recorder = recorder;
        this.outPath = config.out() == null ? null : Path.of(config.out()).toAbsolutePath();
        this.configuredSpoolDir = config.spoolDir() == null ? null : Path.of(config.spoolDir()).toAbsolutePath();
    }

    @Override
    public synchronized long startRecording(final String name) {
        if (recorder.isRecording()) {
            throw new IllegalStateException("already recording #" + recorder.current().id());
        }
        if (roots.specs().isEmpty()) {
            throw new IllegalStateException("no roots are set. Apply roots before starting a recording");
        }
        discardLast();
        final String cleaned = name == null ? "" : name.replaceAll("[\\r\\n]", " ").trim();
        final boolean spooled = outPath == null;
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
        final Recording stopped = recorder.stopIfRecording("closed at shutdown");
        if (stopped != null) {
            lastRecording = stopped;
        }
        streams.closeAll();
        discardLast();
        if (spoolDirIsTemp && spoolDir != null) {
            try {
                Files.deleteIfExists(spoolDir);
            } catch (final IOException e) {
                Log.warn("could not remove the spool directory " + spoolDir + ": " + e);
            }
        }
    }

    @Override
    public String[] searchMethods(final String query, final int max) {
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty() || max <= 0) {
            return new String[0];
        }
        return MethodRegistry.search(q, Math.min(max, MAX_SEARCH_RESULTS));
    }

    @Override
    public synchronized void replaceRoots(final String[] specs) {
        if (recorder.isRecording()) {
            throw new IllegalStateException("cannot change roots while recording #" + recorder.current().id() + " is running");
        }
        final List<RootSpec> parsed = new ArrayList<>();
        if (specs != null) {
            for (final String s : specs) {
                final String t = s == null ? "" : s.trim();
                if (t.isEmpty()) {
                    continue;
                }
                final RootSpec spec = RootSpec.parse(t);
                config.requireInstrumentable(spec);
                parsed.add(spec);
            }
        }
        roots.replaceRoots(parsed);
    }

    private Path spoolFileFor(final long id) {
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
            } catch (final IOException e) {
                throw new UncheckedIOException("cannot create the spool directory", e);
            }
        }
        return spoolDir;
    }

    private void discardLast() {
        final Recording r = lastRecording;
        if (r == null) {
            return;
        }
        streams.closeAllOf(r);
        lastRecording = null;
        if (r.spooled()) {
            deleteSpoolFile(r);
        }
    }

    private synchronized void discardIfDelivered(final Recording r) {
        if (!r.spooled() || !r.closed() || !r.delivered() || streams.hasStreamsOf(r)) {
            return;
        }
        if (lastRecording == r) {
            lastRecording = null;
        }
        deleteSpoolFile(r);
    }

    @Override
    public synchronized String[] status() {
        final List<String> l = new ArrayList<>();
        final Recording currentRecording = recorder.current();
        l.add("v=" + PROTOCOL_VERSION);
        l.add("pid=" + ProcessHandle.current().pid());
        l.add("state=" + (currentRecording != null ? "recording" : "idle"));
        final String gateState = StartupGate.stateName();
        if (gateState != null) {
            l.add("waitstart.state=" + gateState);
        }
        if (outPath != null) {
            l.add("out=" + outPath);
        }
        if (spoolDir != null) {
            l.add("spoolDir=" + spoolDir);
        }
        final List<RootSpec> specs = roots.specs();
        l.add("roots=" + specs.size());
        for (int i = 0; i < specs.size(); i++) {
            final RootSpec s = specs.get(i);
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
    public synchronized long openStream(final long recordingId, final long fromOffset) {
        return streams.open(recordingById(recordingId), fromOffset);
    }

    private Recording recordingById(final long recordingId) {
        final Recording current = recorder.current();
        if (current != null && current.id() == recordingId) {
            return current;
        }
        if (lastRecording != null && lastRecording.id() == recordingId) {
            return lastRecording;
        }
        throw new IllegalArgumentException("unknown recording id " + recordingId);
    }

    @Override
    public byte[] readStream(final long streamId) {
        return streams.read(streamId);
    }

    @Override
    public void closeStream(final long streamId) {
        streams.close(streamId);
    }

    private static void deleteSpoolFile(final Recording r) {
        try {
            Files.deleteIfExists(r.writer().path());
            Log.info("recording #" + r.id() + (r.delivered() ? " delivered, spool file removed" : ": spool file removed"));
        } catch (final IOException e) {
            Log.warn("could not remove the spool file " + r.writer().path() + ": " + e);
        }
    }

    public void registerMBean() throws Exception {
        ManagementFactory.getPlatformMBeanServer().registerMBean(this, new ObjectName(OBJECT_NAME));
    }
}
