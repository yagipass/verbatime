package io.github.yagipass.verbatime.fixtures;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

final class GateHarness {

    private static final long RELEASE_DELAY_MS = 500;

    private GateHarness() {
    }

    static void startReleaser() {
        final Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(RELEASE_DELAY_MS);
                final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                final ObjectName control = new ObjectName("verbatime:type=Control");
                server.invoke(control, "replaceRoots", new Object[] { new String[] { "io.github.yagipass.verbatime.fixtures.Fixture::root" } }, new String[] { "[Ljava.lang.String;" });
                server.invoke(control, "startRecording", new Object[] { "gate-e2e" }, new String[] { "java.lang.String" });
            } catch (final Throwable t) {
                t.printStackTrace();
                Runtime.getRuntime().halt(1);
            }
        }, "gate-releaser");
        releaser.setDaemon(true);
        releaser.start();
    }

    static void verify(final long initNanos) throws Exception {
        final long heldMs = (System.nanoTime() - initNanos) / 1_000_000;
        if (heldMs < RELEASE_DELAY_MS - 100) {
            System.err.println("[e2e-gate] FAIL: main() entered after only " + heldMs + " ms, so the gate did not hold");
            System.exit(1);
        }

        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        final ObjectName control = new ObjectName("verbatime:type=Control");
        final String[] status = (String[]) server.invoke(control, "status", new Object[0], new String[0]);
        boolean recording = false;
        boolean released = false;
        for (final String line : status) {
            recording |= line.equals("state=recording");
            released |= line.equals("waitstart.state=released");
        }
        if (!recording || !released) {
            System.err.println("[e2e-gate] FAIL: expected state=recording and waitstart.state=released when main() starts, got " + String.join(" | ", status));
            System.exit(1);
        }

        new Fixture().root();
        server.invoke(control, "stopRecording", new Object[0], new String[0]);
        System.err.println("[e2e-gate] ok: main() held for " + heldMs + " ms, recording was live before startup code ran");
    }
}
