package io.github.yagipass.verbatime.fixtures;

public final class InstanceGateMain {

    private static final long INIT_NANOS = System.nanoTime();

    static {
        GateHarness.startReleaser();
    }

    public InstanceGateMain() {
    }

    public void main() throws Exception {
        GateHarness.verify(INIT_NANOS);
    }
}
