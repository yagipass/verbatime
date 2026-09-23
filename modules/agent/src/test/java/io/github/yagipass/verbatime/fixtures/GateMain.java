package io.github.yagipass.verbatime.fixtures;

public final class GateMain {

    private static final long INIT_NANOS = System.nanoTime();

    static {
        GateHarness.startReleaser();
    }

    private GateMain() {
    }

    public static void main(final String[] args) throws Exception {
        GateHarness.verify(INIT_NANOS);
    }
}
