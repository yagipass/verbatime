package io.github.yagipass.verbatime.agent;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.StartupGate;

final class StartupGateSetup {

    private StartupGateSetup() {
    }

    static String resolveGateClassInternal() {
        final String main = resolveMainClass(System.getProperty("sun.java.command"), System.getProperty("java.class.path"), System.getProperty("jdk.module.main"), m -> ModuleLayer.boot().findModule(m).flatMap(x -> x.getDescriptor().mainClass()));
        final String internal = main.replace('.', '/');
        if (Transformer.isNeverInstrumented(internal)) {
            throw new IllegalArgumentException("waitstart: the main class " + main + " is a JDK or agent class and cannot carry the startup gate");
        }
        return internal;
    }

    static String resolveMainClass(final String javaCommand, final String classPath, final String mainModule, final Function<String, Optional<String>> moduleMainClass) {
        if (javaCommand == null || javaCommand.isBlank()) {
            throw new IllegalArgumentException("waitstart: cannot identify the main class because sun.java.command is not set. waitstart only works when the java launcher starts the JVM");
        }
        final int sp = javaCommand.indexOf(' ');
        final String head = sp < 0 ? javaCommand : javaCommand.substring(0, sp);
        if (mainModule != null) {
            final int slash = head.indexOf('/');
            if (slash >= 0) {
                return head.substring(slash + 1);
            }
            return moduleMainClass.apply(mainModule).orElseThrow(() -> new IllegalArgumentException("waitstart: module " + mainModule + " has no main class, so start it with -m " + mainModule + "/<main class>"));
        }
        if (classPath != null && classPath.endsWith(".jar") && (javaCommand.equals(classPath) || javaCommand.startsWith(classPath + " "))) {
            return mainClassFromManifest(classPath);
        }
        return head;
    }

    private static String mainClassFromManifest(final String jarPath) {
        try (JarFile jar = new JarFile(jarPath)) {
            final Manifest mf = jar.getManifest();
            final String mainClass = mf == null ? null : mf.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (mainClass == null || mainClass.isBlank()) {
                throw new IllegalArgumentException("waitstart: " + jarPath + " has no Main-Class in its manifest");
            }
            return mainClass.trim();
        } catch (final IOException e) {
            throw new IllegalArgumentException("waitstart: cannot read " + jarPath + ": " + e);
        }
    }

    static void arm(final String gateClass, final long waitStartMs) {
        StartupGate.arm(waitStartMs);
        startWatchdog(gateClass, waitStartMs);
        Log.info("waitstart: will pause at " + gateClass.replace('/', '.') + ".main for up to " + (waitStartMs / 1000) + " s until startRecording");
        if (System.getProperty("com.sun.management.jmxremote.port") == null) {
            Log.warn("waitstart is set but com.sun.management.jmxremote.port is not, so only local attach JMX clients can reach the gate in time");
        }
    }

    private static void startWatchdog(final String gateClass, final long waitStartMs) {
        final Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(waitStartMs);
            } catch (final InterruptedException e) {
                return;
            }
            final String warning = neverReachedWarning(StartupGate.stateName(), gateClass, waitStartMs);
            if (warning != null) {
                Log.warn(warning);
            }
        }, "verbatime-waitstart-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    static String neverReachedWarning(final String gateState, final String gateClass, final long waitStartMs) {
        if (!"armed".equals(gateState)) {
            return null;
        }
        return "waitstart: " + gateClass.replace('/', '.') + ".main was not entered within " + (waitStartMs / 1000) + " s, so this JVM was never paused. The main class may have been resolved incorrectly";
    }
}
