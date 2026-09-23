package io.github.yagipass.verbatime.agent.test;

public final class TestMain {

    private TestMain() {
    }

    public static void main(final String[] args) {
        run("ConfigTest", io.github.yagipass.verbatime.agent.ConfigTest::run);
        run("ConfigWarningTest", io.github.yagipass.verbatime.agent.ConfigWarningTest::run);
        run("LogTest", LogTest::run);
        run("StartupGateSetupTest", io.github.yagipass.verbatime.agent.StartupGateSetupTest::run);
        run("TraceFileWriterTest", io.github.yagipass.verbatime.agent.probe.TraceFileWriterTest::run);
        run("ChunkEncoderTest", io.github.yagipass.verbatime.agent.probe.ChunkEncoderTest::run);
        run("GcPausesTest", io.github.yagipass.verbatime.agent.probe.GcPausesTest::run);
        run("ProbeFailureTest", io.github.yagipass.verbatime.agent.probe.ProbeFailureTest::run);
        run("BootstrapInstallerTest", io.github.yagipass.verbatime.agent.BootstrapInstallerTest::run);
        run("TransformerTest", io.github.yagipass.verbatime.agent.TransformerTest::run);
        run("VerbatimeControlTest", io.github.yagipass.verbatime.agent.VerbatimeControlTest::run);
        run("StartupGateTest", io.github.yagipass.verbatime.agent.StartupGateTest::run);
        report();
    }

    public static void report() {
        System.err.println();
        System.err.println("[test] " + Check.passed() + " checks passed, " + Check.failures().size() + " failed");
        for (final String f : Check.failures()) {
            System.err.println("[test]   FAIL " + f);
        }
        System.exit(Check.failures().isEmpty() ? 0 : 1);
    }

    public static void run(final String name, final Check.ThrowingRunnable body) {
        System.err.println("[test] === " + name);
        final int before = Check.failures().size();
        try {
            body.run();
        } catch (final Throwable t) {
            Check.fail(name + " threw " + t);
            t.printStackTrace(System.err);
        }
        System.err.println("[test] === " + name + (Check.failures().size() == before ? " OK" : " FAILED"));
    }
}
