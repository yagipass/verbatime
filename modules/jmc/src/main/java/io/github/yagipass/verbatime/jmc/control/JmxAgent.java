package io.github.yagipass.verbatime.jmc.control;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

final class JmxAgent implements Agent {

    private static final String OBJECT_NAME = "verbatime:type=Control";

    private final MBeanServerConnection connection;

    private final AutoCloseable closer;

    private final ObjectName objectName;

    @SuppressWarnings("BanJNDI")
    static JmxAgent dial(final String target) throws IOException {
        final String url = target.startsWith("service:jmx:") ? target
                : "service:jmx:rmi:///jndi/rmi://" + target + "/jmxrmi";
        final JMXConnector c = JMXConnectorFactory.connect(new JMXServiceURL(url));
        try {
            return new JmxAgent(c.getMBeanServerConnection(), c);
        } catch (final RuntimeException | IOException e) {
            try {
                c.close();
            } catch (final IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    private JmxAgent(final MBeanServerConnection connection, final AutoCloseable closer) {
        this.connection = connection;
        this.closer = closer;
        try {
            this.objectName = new ObjectName(OBJECT_NAME);
        } catch (final MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Map<String, String> status() throws IOException {
        return parseStatus((String[]) invoke("status", new Object[0], new String[0]));
    }

    @Override
    public String[] searchMethods(final String query, final int max) throws IOException {
        return (String[]) invoke("searchMethods", new Object[] { query, max },
                new String[] { "java.lang.String", "int" });
    }

    @Override
    public void replaceRoots(final String[] specs) throws IOException {
        invoke("replaceRoots", new Object[] { specs }, new String[] { "[Ljava.lang.String;" });
    }

    @Override
    public long startRecording() throws IOException {
        return ((Long) invoke("startRecording", new Object[] { "" }, new String[] { "java.lang.String" })).longValue();
    }

    @Override
    public void stopRecording() throws IOException {
        invoke("stopRecording", new Object[0], new String[0]);
    }

    @Override
    public long openStream(final long recordingId, final long fromOffset) throws IOException {
        return ((Long) invoke("openStream", new Object[] { recordingId, fromOffset }, new String[] { "long", "long" }))
                .longValue();
    }

    @Override
    public byte[] readStream(final long streamId) throws IOException {
        return (byte[]) invoke("readStream", new Object[] { streamId }, new String[] { "long" });
    }

    @Override
    public void closeStream(final long streamId) throws IOException {
        invoke("closeStream", new Object[] { streamId }, new String[] { "long" });
    }

    private Object invoke(final String op, final Object[] params, final String[] sig) throws IOException {
        try {
            return connection.invoke(objectName, op, params, sig);
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException(op + ": " + cause.getMessage(), cause);
        }
    }

    static Map<String, String> parseStatus(final String[] lines) {
        final Map<String, String> m = new LinkedHashMap<>();
        for (final String l : lines) {
            final int eq = l.indexOf('=');
            if (eq > 0) {
                m.put(l.substring(0, eq), l.substring(eq + 1));
            }
        }
        return m;
    }

    @Override
    public void close() throws IOException {
        try {
            closer.close();
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }
}
