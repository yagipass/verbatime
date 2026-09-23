package io.github.yagipass.verbatime.agent.test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

import io.github.yagipass.verbatime.fixtures.Fixture;

public final class E2EMain {

    private E2EMain() {
    }

    public static void main(final String[] args) throws Exception {
        if (io.github.yagipass.verbatime.agent.probe.Probe.class.getClassLoader() != null) {
            System.err.println("[e2e] FAIL: Probe should be bootstrap-defined but came from " + io.github.yagipass.verbatime.agent.probe.Probe.class.getClassLoader());
            System.exit(1);
        }
        if (E2EMain.class.getClassLoader() == null) {
            System.err.println("[e2e] FAIL: E2EMain unexpectedly bootstrap-defined");
            System.exit(1);
        }

        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        final ObjectName control = new ObjectName("verbatime:type=Control");
        server.invoke(control, "replaceRoots", new Object[] { new String[] { "io.github.yagipass.verbatime.fixtures.Fixture::root", "io.github.yagipass.verbatime.fixtures.Fixture::rootThrows", "io.github.yagipass.verbatime.fixtures.Fixture::rootAllocates" } }, new String[] { "[Ljava.lang.String;" });
        final long id = (Long) server.invoke(control, "startRecording", new Object[] { "e2e" }, new String[] { "java.lang.String" });
        System.err.println("[e2e] recording #" + id + " started over JMX");
        final CountDownLatch majorGc = subscribeAfterAgent();

        final Fixture fx = new Fixture();
        final String r = fx.root();
        if (r == null || r.isEmpty()) {
            System.err.println("[e2e] FAIL: instrumented root() returned '" + r + "'");
            System.exit(1);
        }
        try {
            fx.rootThrows();
            System.err.println("[e2e] FAIL: rootThrows() did not throw");
            System.exit(1);
        } catch (final IllegalStateException expected) {
            System.err.println("[e2e] ok: rootThrows() threw " + expected.getClass().getSimpleName());
        }
        final int allocated = fx.rootAllocates();
        if (allocated <= 0) {
            System.err.println("[e2e] FAIL: instrumented rootAllocates() returned " + allocated);
            System.exit(1);
        }

        if (!majorGc.await(10, TimeUnit.SECONDS)) {
            System.err.println("[e2e] FAIL: no major GC notification arrived within 10 s of rootAllocates()");
            System.exit(1);
        }

        server.invoke(control, "stopRecording", new Object[0], new String[0]);
        System.err.println("[e2e] ok: root() = " + r);
    }

    private static CountDownLatch subscribeAfterAgent() {
        final CountDownLatch latch = new CountDownLatch(1);
        for (final GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc instanceof final NotificationEmitter emitter) {
                emitter.addNotificationListener((n, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType()) && GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData()).getGcAction().contains("major")) {
                        latch.countDown();
                    }
                }, null, null);
            }
        }
        return latch;
    }
}
