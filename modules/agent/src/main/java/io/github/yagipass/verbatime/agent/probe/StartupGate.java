package io.github.yagipass.verbatime.agent.probe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

import com.google.errorprone.annotations.Var;

public final class StartupGate {

    private enum State {
        UNARMED, ARMED, WAITING, RELEASED, EXPIRED
    }

    private static volatile @Nullable CountDownLatch latch;

    private static volatile long timeoutMs;

    private static volatile State state = State.UNARMED;

    private StartupGate() {
    }

    public static void arm(long timeoutMs) {
        StartupGate.timeoutMs = timeoutMs;
        state = State.ARMED;
        latch = new CountDownLatch(1);
    }

    public static void await() {
        CountDownLatch l = latch;
        if (l == null) {
            return;
        }
        state = State.WAITING;
        Log.info("waitstart: pausing before main() for up to " + (timeoutMs / 1000) + " s until startRecording arrives over JMX");
        @Var boolean released = false;
        try {
            released = l.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("waitstart: interrupted while waiting, starting main()");
        }
        if (released) {
            state = State.RELEASED;
            Log.info("waitstart: released by startRecording, starting main()");
        } else {
            state = State.EXPIRED;
            Log.warn("waitstart: no startRecording within " + (timeoutMs / 1000) + " s, starting main() without recording");
        }
    }

    public static void release() {
        CountDownLatch l = latch;
        if (l != null) {
            l.countDown();
        }
    }

    public static @Nullable String stateName() {
        return switch (state) {
            case UNARMED -> null;
            case ARMED -> "armed";
            case WAITING -> "waiting";
            case RELEASED -> "released";
            case EXPIRED -> "expired";
        };
    }
}
