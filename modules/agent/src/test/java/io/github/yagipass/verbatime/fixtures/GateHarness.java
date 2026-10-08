package io.github.yagipass.verbatime.fixtures;

import com.google.errorprone.annotations.Var;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

final class GateHarness {

  private static final long RELEASE_DELAY_MS = 500;

  private GateHarness() {}

  static void startReleaser() {
    Thread releaser =
        new Thread(
            () -> {
              try {
                Thread.sleep(RELEASE_DELAY_MS);
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                ObjectName control = new ObjectName("verbatime:type=Control");
                server.invoke(
                    control,
                    "replaceRoots",
                    new Object[] {
                      new String[] {"io.github.yagipass.verbatime.fixtures.Fixture::root"}
                    },
                    new String[] {"[Ljava.lang.String;"});
                server.invoke(
                    control,
                    "startRecording",
                    new Object[] {"gate-e2e"},
                    new String[] {"java.lang.String"});
              } catch (Throwable t) {
                t.printStackTrace();
                Runtime.getRuntime().halt(1);
              }
            },
            "gate-releaser");
    releaser.setDaemon(true);
    releaser.start();
  }

  static void verify(long initNanos) throws Exception {
    long heldMs = (System.nanoTime() - initNanos) / 1_000_000;
    if (heldMs < RELEASE_DELAY_MS - 100) {
      System.err.println(
          "[e2e-gate] FAIL: main() entered after only " + heldMs + " ms, so the gate did not hold");
      System.exit(1);
    }

    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    ObjectName control = new ObjectName("verbatime:type=Control");
    String[] status = (String[]) server.invoke(control, "status", new Object[0], new String[0]);
    @Var boolean recording = false;
    @Var boolean released = false;
    for (String line : status) {
      recording |= line.equals("state=recording");
      released |= line.equals("waitstart.state=released");
    }
    if (!recording || !released) {
      System.err.println(
          "[e2e-gate] FAIL: expected state=recording and waitstart.state=released when main() starts, got "
              + String.join(" | ", status));
      System.exit(1);
    }

    new Fixture().root();
    server.invoke(control, "stopRecording", new Object[0], new String[0]);
    System.err.println(
        "[e2e-gate] ok: main() held for "
            + heldMs
            + " ms, recording was live before startup code ran");
  }
}
