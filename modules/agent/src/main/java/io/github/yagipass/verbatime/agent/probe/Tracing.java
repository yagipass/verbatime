package io.github.yagipass.verbatime.agent.probe;

public final class Tracing {

    private Tracing() {
    }

    public static void start(final TraceFileWriter w) {
        MethodRegistry.attach(w);
        ExceptionRegistry.attach(w);
        Probe.attach(w);
        GcPauses.attach(w);
        Probe.enable();
    }

    public static int stop() {
        final int flushed = Probe.disableAndFlush();
        GcPauses.detach();
        Probe.detach();
        MethodRegistry.detach();
        ExceptionRegistry.detach();
        return flushed;
    }
}
