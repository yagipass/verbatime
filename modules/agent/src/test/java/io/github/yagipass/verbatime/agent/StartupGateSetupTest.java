package io.github.yagipass.verbatime.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import io.github.yagipass.verbatime.agent.test.Check;

public final class StartupGateSetupTest {

    private StartupGateSetupTest() {
    }

    public static void run() throws Exception {
        Check.eq("com.example.Main", StartupGateSetup.resolveMainClass("com.example.Main", "classes", null, StartupGateSetupTest::noModules), "-cp form: the command is the main class");
        Check.eq("com.example.Main", StartupGateSetup.resolveMainClass("com.example.Main arg1 arg2", "classes", null, StartupGateSetupTest::noModules), "-cp form: arguments after the main class are not part of its name");

        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp")).resolve("waitstart");
        final Path spaced = tmp.resolve("dir with space");
        Files.createDirectories(spaced);
        final Path withMain = spaced.resolve("with-main.jar");
        writeJar(withMain, "com.example.JarMain");
        final String jar = withMain.toString();
        Check.eq("com.example.JarMain", StartupGateSetup.resolveMainClass(jar + " x y", jar, null, StartupGateSetupTest::noModules), "-jar form with a space in the jar path gates the manifest Main-Class, not the path fragment before the space");
        Check.eq("com.example.JarMain", StartupGateSetup.resolveMainClass(jar, jar, null, StartupGateSetupTest::noModules), "-jar form without arguments gates the manifest Main-Class");
        Check.eq("com.example.Main", StartupGateSetup.resolveMainClass("com.example.Main " + jar, jar, null, StartupGateSetupTest::noModules), "-cp app.jar Main gates the named class even when an argument repeats the jar path");

        Check.eq("com.example.Main", StartupGateSetup.resolveMainClass("app.mod/com.example.Main args", "", "app.mod", m -> Optional.of("com.example.Other")), "-m module/class gates the class named on the command line over the module's own main class");
        Check.eq("com.example.ModMain", StartupGateSetup.resolveMainClass("app.mod args", "", "app.mod", m -> "app.mod".equals(m) ? Optional.of("com.example.ModMain") : Optional.empty()), "-m module gates the module descriptor's main class, not the module name");

        final Path withoutMain = tmp.resolve("without-main.jar");
        writeJar(withoutMain, null);
        expectFailure(null, "", null, StartupGateSetupTest::noModules, "missing sun.java.command");
        expectFailure("   ", "", null, StartupGateSetupTest::noModules, "blank sun.java.command");
        expectFailure(withoutMain.toString(), withoutMain.toString(), null, StartupGateSetupTest::noModules, "jar without Main-Class");
        final String absent = tmp.resolve("absent.jar").toString();
        expectFailure(absent, absent, null, StartupGateSetupTest::noModules, "unreadable jar");
        expectFailure("app.mod", "", "app.mod", m -> Optional.empty(), "module without a main class");

        final String warning = StartupGateSetup.neverReachedWarning("armed", "com/example/Main", 60_000);
        Check.that(warning != null && warning.contains("com.example.Main.main") && warning.contains("60 s"), "a gate still armed after waitstart names the class it waited for, so a wrong resolution is visible: " + warning);
        for (final String state : new String[] { "waiting", "released", "expired" }) {
            Check.that(StartupGateSetup.neverReachedWarning(state, "com/example/Main", 60_000) == null, "no never-reached warning once the gate was entered, state " + state);
        }
    }

    private static Optional<String> noModules(final String module) {
        throw new AssertionError("module lookup must not be used for a class path launch, asked for " + module);
    }

    private static void writeJar(final Path path, final String mainClass) throws Exception {
        final Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        new JarOutputStream(Files.newOutputStream(path), mf).close();
    }

    private static void expectFailure(final String command, final String classPath, final String mainModule, final Function<String, Optional<String>> moduleMainClass, final String what) {
        Check.thrown(IllegalArgumentException.class, () -> StartupGateSetup.resolveMainClass(command, classPath, mainModule, moduleMainClass), "resolveMainClass rejects " + what + ": '" + command + "'");
    }
}
