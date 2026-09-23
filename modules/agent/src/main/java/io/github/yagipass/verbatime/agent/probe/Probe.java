package io.github.yagipass.verbatime.agent.probe;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Probe {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private static final Set<Session> LIVE_SESSIONS = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger LIVE_COUNT = new AtomicInteger();

    private static final AtomicInteger STARTED = new AtomicInteger();

    private static final Object ROOTS_LOCK = new Object();

    private static volatile TraceFileWriter sink;

    private static volatile boolean enabled;

    private static volatile boolean warnedBroken;

    private static volatile long[] rootBits = new long[0];

    private Probe() {
    }

    static void attach(final TraceFileWriter w) {
        sink = w;
    }

    static void detach() {
        sink = null;
    }

    static void enable() {
        warnedBroken = false;
        enabled = true;
    }

    public static void addRootId(final int id) {
        synchronized (ROOTS_LOCK) {
            long[] bits = rootBits;
            final int word = id >>> 6;
            bits = word < bits.length ? bits.clone() : Arrays.copyOf(bits, word + 1);
            bits[word] |= 1L << id;
            rootBits = bits;
        }
    }

    public static void replaceRootBits(final long[] bits) {
        synchronized (ROOTS_LOCK) {
            rootBits = bits;
        }
    }

    public static void enter(final int id) {
        if (!enabled) {
            return;
        }
        Session session = null;
        try {
            session = CURRENT.get();
            if (session != null) {
                if (!session.closed) {
                    session.enter(id);
                    return;
                }
                dropClosed(session);
            }
            final long[] bits = rootBits;
            final int word = id >>> 6;
            if (word >= bits.length || (bits[word] & (1L << id)) == 0) {
                return;
            }
            final TraceFileWriter w = sink;
            if (w == null || w.isStopped()) {
                return;
            }
            try {
                session = new Session(w, id, STARTED.incrementAndGet());
            } catch (final OutOfMemoryError e) {
                Log.warn("could not allocate the event chunk for root " + MethodRegistry.displayName(id) + " on thread " + Thread.currentThread().getName() + ", so this execution is not traced");
                return;
            }
            CURRENT.set(session);
            LIVE_SESSIONS.add(session);
            LIVE_COUNT.incrementAndGet();
            if (!enabled) {
                unbind(session);
                return;
            }
            session.enter(id);
        } catch (final Throwable e) {
            if (session != null && !session.closed) {
                session.failure = e;
                session.closed = true;
            }
        }
    }

    public static void exit(final int id) {
        if (!enabled) {
            return;
        }
        Session session = null;
        try {
            session = CURRENT.get();
            if (session == null) {
                return;
            }
            if (session.closed) {
                if (session.failure == null) {
                    unbind(session);
                }
                return;
            }
            session.exit(id, Session.EXIT);
            if (session.depth < 0) {
                finish(session);
            }
        } catch (final Throwable e) {
            if (session != null && !session.closed) {
                session.failure = e;
                session.closed = true;
            }
        }
    }

    public static void exitThrow(final Throwable t, final int id) {
        if (!enabled) {
            return;
        }
        Session session = null;
        try {
            session = CURRENT.get();
            if (session == null) {
                return;
            }
            if (session.closed) {
                if (session.failure == null) {
                    unbind(session);
                }
                return;
            }
            int exc;
            try {
                exc = ExceptionRegistry.id(t.getClass());
            } catch (final Throwable e) {
                exc = ExceptionRegistry.UNKNOWN;
            }
            session.exit(id, Session.EXIT | Session.THROW, exc);
            if (session.depth < 0) {
                finish(session);
            }
        } catch (final Throwable e) {
            if (session != null && !session.closed) {
                session.failure = e;
                session.closed = true;
            }
        }
    }

    private static void finish(final Session session) {
        session.finish();
        session.closed = true;
        unbind(session);
    }

    private static void dropClosed(final Session session) {
        final Throwable failure = session.failure;
        if (failure == null) {
            unbind(session);
            return;
        }
        session.finish();
        session.failure = null;
        unbind(session);
        if (!warnedBroken) {
            warnedBroken = true;
            Log.warn("session #" + session.seq + " root=" + MethodRegistry.displayName(session.rootId) + " thread=\"" + session.owner.getName() + "\" ended early after " + failure + " inside the agent. Its call tree stops there and open frames show as unclosed");
        }
    }

    private static void unbind(final Session session) {
        CURRENT.remove();
        if (LIVE_SESSIONS.remove(session)) {
            LIVE_COUNT.decrementAndGet();
        }
    }

    @SuppressWarnings("ModifyCollectionInEnhancedForLoop")
    static int disableAndFlush() {
        enabled = false;
        int flushed = 0;
        for (final Session session : LIVE_SESSIONS) {
            if (LIVE_SESSIONS.remove(session)) {
                LIVE_COUNT.decrementAndGet();
                session.flushTruncated();
                flushed++;
            }
        }
        return flushed;
    }

    public static int liveSessions() {
        return LIVE_COUNT.get();
    }

    public static int endedSessions() {
        return STARTED.get() - LIVE_COUNT.get();
    }
}
