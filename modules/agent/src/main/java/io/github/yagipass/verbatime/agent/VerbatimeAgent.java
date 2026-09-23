package io.github.yagipass.verbatime.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.yagipass.verbatime.agent.jmx.VerbatimeControl;
import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.Probe;

public final class VerbatimeAgent {

    private VerbatimeAgent() {
    }

    public static void premain(final String agentArgs, final Instrumentation inst) {

        BootstrapInstaller.install(inst);

        final Config cfg;
        final String gateClass;
        try {
            cfg = Config.parse(agentArgs);
            gateClass = cfg.waitStartMs() > 0 ? StartupGateSetup.resolveGateClassInternal() : null;
        } catch (final IllegalArgumentException e) {
            Log.warn("invalid agent arguments: " + e.getMessage());
            throw e;
        }
        warnIfOutUnwritable(cfg);
        if (gateClass != null) {
            StartupGateSetup.arm(gateClass, cfg.waitStartMs());
        }

        final Roots roots = new Roots();
        final Transformer transformer = new Transformer(cfg, roots, gateClass);
        final Recorder recorder = new Recorder();
        final boolean startup = cfg.recordStart() == RecordStart.STARTUP;
        if (!cfg.roots().isEmpty()) {
            roots.presetRoots(cfg.roots());
        }
        inst.addTransformer(transformer, false);

        final Runnable closeAtShutdown = startup ? () -> recorder.stopIfRecording("closed at shutdown") : registerJmxControl(cfg, transformer, roots, recorder);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeAtShutdown.run();
            warnAboutUnresolvedRoots(cfg, roots);
            Log.info("shutdown: instrumented " + transformer.instrumentedClasses() + " classes / " + transformer.instrumentedMethods() + " methods, " + transformer.failedClasses() + " classes failed to transform, " + transformer.idLimitSkippedClasses() + " classes skipped at the method id limit, " + Log.plural(Probe.endedSessions(), "session") + " completed");
        }, "verbatime-shutdown"));

        Log.info("loaded: " + cfg.describe());

        if (startup) {
            startRecordingNow(cfg, recorder);
        }
    }

    private static Runnable registerJmxControl(final Config cfg, final Transformer transformer, final Roots roots, final Recorder recorder) {
        final VerbatimeControl control = new VerbatimeControl(cfg, transformer, roots, recorder);
        try {
            control.registerMBean();
            Log.info("JMX control registered as " + VerbatimeControl.OBJECT_NAME);
        } catch (final Throwable t) {
            Log.warn("JMX control unavailable: " + t);
        }
        return control::shutdown;
    }

    private static void startRecordingNow(final Config cfg, final Recorder recorder) {
        final Path out = Path.of(cfg.out()).toAbsolutePath();
        Log.info("record=startup: recording until this JVM exits, without JMX -> " + out);
        try {
            recorder.start(id -> out, "", false);
        } catch (final RuntimeException e) {
            Log.warn("record=startup: " + e.getMessage() + ", so this JVM runs unrecorded");
        }
    }

    private static void warnAboutUnresolvedRoots(final Config cfg, final Roots roots) {
        if (cfg.roots().isEmpty()) {
            return;
        }
        for (final RootSpec spec : roots.unresolved()) {
            Log.warn("root " + spec + " never matched a loaded method, so nothing was recorded for it. Check the class and method name");
        }
    }

    private static void warnIfOutUnwritable(final Config cfg) {
        if (cfg.out() == null) {
            return;
        }
        final Path out = Path.of(cfg.out()).toAbsolutePath();
        final Path dir = out.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            Log.warn("out=" + out + ": recordings cannot start until the parent directory exists");
        } else if (!Files.isWritable(dir) || (Files.exists(out) && !Files.isWritable(out))) {
            Log.warn("out=" + out + " is not writable, so recordings cannot start");
        }
    }
}
