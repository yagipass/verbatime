package io.github.yagipass.verbatime.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.yagipass.verbatime.agent.jmx.VerbatimeControl;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.StartupGate;
import io.github.yagipass.verbatime.agent.test.Check;

public final class StartupGateTest {

    private StartupGateTest() {
    }

    public static void run() throws Exception {
        StartupGate.release();
        Check.that(StartupGate.stateName() == null, "no gate state before arming");

        StartupGate.arm(150);
        Check.eq("armed", StartupGate.stateName(), "armed right after premain-style arming");
        final long t0 = System.nanoTime();
        StartupGate.await();
        Check.that(System.nanoTime() - t0 >= 140_000_000L, "await held for about the timeout");
        Check.eq("expired", StartupGate.stateName(), "expired after the timeout");

        StartupGate.arm(10_000);
        final Thread gated = new Thread(StartupGate::await, "gated-main");
        gated.start();
        final long deadline = System.currentTimeMillis() + 5_000;
        while (!"waiting".equals(StartupGate.stateName()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        Check.eq("waiting", StartupGate.stateName(), "thread is parked at the gate");

        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
        Files.createDirectories(tmp);
        final int base = MethodRegistry.reserveIds("test.gate.C", List.of("m()V"));
        MethodRegistry.commitClass(base, "test.gate.C", List.of("m()V"));
        final Config cfg = Config.parse("out=" + tmp.resolve("gate.vbtm"));
        final Roots roots = new Roots();
        final VerbatimeControl ctl = new VerbatimeControl(cfg, new Transformer(cfg, roots, null), roots, new Recorder());
        ctl.replaceRoots(new String[] { "test.gate.C::m" });
        ctl.startRecording("gate");
        gated.join(5_000);
        Check.that(!gated.isAlive(), "startRecording released the gate");
        Check.eq("released", StartupGate.stateName(), "released after startRecording");
        ctl.stopRecording();

        StartupGate.release();
        Check.eq("released", StartupGate.stateName(), "releasing an open gate is a no-op");
    }
}
