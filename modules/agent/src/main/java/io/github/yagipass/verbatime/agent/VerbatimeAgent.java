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

    public static void premain(String agentArgs, Instrumentation inst) {

        BootstrapInstaller.install(inst);

        Config cfg;
        String gateClass;
        try {
            cfg = Config.parse(agentArgs);
            gateClass = cfg.waitStartMs() > 0 ? StartupGateSetup.resolveGateClassInternal() : null;
        } catch (IllegalArgumentException e) {
            Log.warn("invalid agent arguments: " + e.getMessage());
            throw e;
        }
        warnIfOutUnwritable(cfg);
        if (gateClass != null) {
            StartupGateSetup.arm(gateClass, cfg.waitStartMs());
        }

        Roots roots = new Roots();
        Transformer transformer = new Transformer(cfg, roots, gateClass);
        Recorder recorder = new Recorder();
        boolean startup = cfg.recordStart() == RecordStart.STARTUP;
        if (!cfg.roots().isEmpty()) {
            roots.presetRoots(cfg.roots());
        }
        inst.addTransformer(transformer, false);

        Runnable closeAtShutdown = startup ? () -> recorder.stopIfRecording("closed at shutdown") : registerJmxControl(cfg, transformer, roots, recorder);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeAtShutdown.run();
            warnAboutUnresolvedRoots(cfg, roots);
            Log.info("shutdown: instrumented " + transformer.instrumentedClasses() + " classes / " + transformer.instrumentedMethods() + " methods, " + transformer.failedClasses() + " classes failed to transform, " + transformer.idLimitSkippedClasses() + " classes skipped at the method id limit, " + Log.plural(Probe.completedSessions(), "session") + " completed");
        }, "verbatime-shutdown"));

        Log.info("loaded: " + cfg.describe());

        if (startup) {
            startRecordingNow(cfg, recorder);
        }
    }

    private static Runnable registerJmxControl(Config cfg, Transformer transformer, Roots roots, Recorder recorder) {
        VerbatimeControl control = new VerbatimeControl(cfg, transformer, roots, recorder);
        try {
            control.registerMBean();
            Log.info("JMX control registered as " + VerbatimeControl.OBJECT_NAME);
        } catch (Throwable t) {
            Log.warn("JMX control unavailable: " + t);
        }
        return control::shutdown;
    }

    private static void startRecordingNow(Config cfg, Recorder recorder) {
        Path out = Path.of(cfg.out()).toAbsolutePath();
        Log.info("record=startup: recording until this JVM exits, without JMX -> " + out);
        try {
            recorder.start(id -> out, "", false);
        } catch (RuntimeException e) {
            Log.warn("record=startup: " + e.getMessage() + ", so this JVM runs unrecorded");
        }
    }

    private static void warnAboutUnresolvedRoots(Config cfg, Roots roots) {
        if (cfg.roots().isEmpty()) {
            return;
        }
        for (RootSpec spec : roots.unresolved()) {
            Log.warn("root " + spec + " never matched a loaded method, so nothing was recorded for it. Check the class and method name");
        }
    }

    private static void warnIfOutUnwritable(Config cfg) {
        if (cfg.out() == null) {
            return;
        }
        Path out = Path.of(cfg.out()).toAbsolutePath();
        Path dir = out.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            Log.warn("out=" + out + ": recordings cannot start until the parent directory exists");
        } else if (!Files.isWritable(dir) || (Files.exists(out) && !Files.isWritable(out))) {
            Log.warn("out=" + out + " is not writable, so recordings cannot start");
        }
    }
}
