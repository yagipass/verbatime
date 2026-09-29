package io.github.yagipass.verbatime.jmc.control;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

final class ManualExecutor extends AbstractExecutorService {

    private final Deque<Runnable> queue = new ArrayDeque<>();

    private boolean shutdown;

    @Override
    public void execute(Runnable r) {
        if (shutdown) {
            throw new RejectedExecutionException("executor is shut down");
        }
        queue.add(r);
    }

    int deferred() {
        return queue.size();
    }

    void runAll() {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> dropped = List.copyOf(queue);
        queue.clear();
        return dropped;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown && queue.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return isTerminated();
    }
}
