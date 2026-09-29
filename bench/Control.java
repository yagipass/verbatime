import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.google.errorprone.annotations.Var;

final class Control implements AutoCloseable {

    record Gc(long count, long millis) {
    }

    private static final Object[] NO_ARGS = new Object[0];

    private static final String[] NO_SIGNATURE = new String[0];

    private final JMXConnector connector;

    private final MBeanServerConnection server;

    private final ObjectName control;

    private final ObjectName os;

    private final ObjectName collectors;

    private Control(JMXConnector connector) throws IOException, JMException {
        this.connector = connector;
        this.server = connector.getMBeanServerConnection();
        this.control = new ObjectName("verbatime:type=Control");
        this.os = new ObjectName("java.lang:type=OperatingSystem");
        this.collectors = new ObjectName("java.lang:type=GarbageCollector,*");
    }

    static Control connect(String hostPort) throws IOException, JMException {
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + hostPort + "/jmxrmi");
        return new Control(JMXConnectorFactory.connect(url));
    }

    boolean hasAgent() throws IOException {
        return server.isRegistered(control);
    }

    Map<String, String> status() throws IOException, JMException {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : (String[]) server.invoke(control, "status", NO_ARGS, NO_SIGNATURE)) {
            int eq = line.indexOf('=');
            map.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return map;
    }

    void replaceRoots(String... specs) throws IOException, JMException {
        server.invoke(control, "replaceRoots", new Object[] { specs }, new String[] { "[Ljava.lang.String;" });
    }

    long startRecording(String name) throws IOException, JMException {
        return (Long) server.invoke(control, "startRecording", new Object[] { name }, new String[] { "java.lang.String" });
    }

    void stopRecording() throws IOException, JMException {
        server.invoke(control, "stopRecording", NO_ARGS, NO_SIGNATURE);
    }

    byte[] download(long recordingId) throws IOException, JMException {
        String[] idAndOffset = { "long", "long" };
        String[] id = { "long" };
        long stream = (Long) server.invoke(control, "openStream", new Object[] { recordingId, 0L }, idAndOffset);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        try {
            while (true) {
                byte[] part = (byte[]) server.invoke(control, "readStream", new Object[] { stream }, id);
                if (part == null) {
                    return all.toByteArray();
                }
                if (part.length == 0) {
                    throw new IllegalStateException("recording " + recordingId + " is still open. Stop it before downloading");
                }
                all.write(part);
            }
        } finally {
            server.invoke(control, "closeStream", new Object[] { stream }, id);
        }
    }

    long processCpuNanos() throws IOException, JMException {
        return (Long) server.getAttribute(os, "ProcessCpuTime");
    }

    Gc gc() throws IOException, JMException {
        @Var long count = 0;
        @Var long millis = 0;
        for (ObjectName name : server.queryNames(collectors, null)) {
            count += (Long) server.getAttribute(name, "CollectionCount");
            millis += (Long) server.getAttribute(name, "CollectionTime");
        }
        return new Gc(count, millis);
    }

    @Override
    public void close() throws IOException {
        connector.close();
    }
}
