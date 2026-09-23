package io.github.yagipass.verbatime.jmc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class FakeUiThread implements UiThread {

    private record Timer(long dueMs, Runnable r) {
    }

    private final Deque<Runnable> posted = new ArrayDeque<>();

    private final List<Timer> timers = new ArrayList<>();

    public long nowMs;

    @Override
    public void post(final Runnable r) {
        posted.add(r);
    }

    @Override
    public void postAfter(final int delayMs, final Runnable r) {
        timers.add(new Timer(nowMs + delayMs, r));
    }

    public int pendingTimers() {
        return timers.size();
    }

    public void runOne() {
        posted.poll().run();
    }

    public void runPosted() {
        while (!posted.isEmpty()) {
            posted.poll().run();
        }
    }

    public void advance(final long ms) {
        nowMs += ms;
        boolean fired = true;
        while (fired) {
            fired = false;
            for (final Timer t : List.copyOf(timers)) {
                if (t.dueMs() <= nowMs) {
                    timers.remove(t);
                    t.r().run();
                    fired = true;
                }
            }
        }
        runPosted();
    }
}
