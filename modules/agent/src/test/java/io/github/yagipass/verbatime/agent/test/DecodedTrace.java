package io.github.yagipass.verbatime.agent.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.format.CorruptTraceException;
import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.TraceReader;

public final class DecodedTrace {

    public static final int TAG_ENTER = 0;

    public static final int TAG_EXIT = 1;

    public static final int TAG_EXIT_THROW = 2;

    public record Event(long ticks, int tag, int methodId, int exceptionId) {
    }

    public static final class DecodedSession {
        public final int seq;

        public int rootId = -1;

        public boolean ended;

        public int chunks;

        public final List<Event> events = new ArrayList<>();

        DecodedSession(final int seq) {
            this.seq = seq;
        }
    }

    public final Map<Long, String> threadNames = new LinkedHashMap<>();

    public final Map<Integer, DecodedSession> sessions = new LinkedHashMap<>();

    public final Map<Integer, String> methodNames = new LinkedHashMap<>();

    public final Map<Integer, String> exceptionNames = new LinkedHashMap<>();

    public final List<GcPause> gcPauses = new ArrayList<>();

    public int danglingExceptionRefs;

    public boolean cleanEnd;

    public long startEpochMs;

    public int utcOffsetSeconds;

    public String exceptionName(final int exceptionId) {
        if (exceptionId < 0) {
            return null;
        }
        if (exceptionId == 0) {
            return "<unknown>";
        }
        final String n = exceptionNames.get(exceptionId);
        return n == null ? "<unknown#" + exceptionId + ">" : n;
    }

    public record GcPause(long startTicks, long durTicks, int action, String collector, String cause) {
        public long endTicks() {
            return startTicks + durTicks;
        }
    }

    public record Node(int methodId, int depth, int exceptionId, boolean unclosed) {
        public boolean thrown() {
            return exceptionId >= 0;
        }
    }

    public static DecodedTrace decode(final Path bin) throws IOException {
        return decode(Files.readAllBytes(bin));
    }

    public static DecodedTrace decode(final byte[] data) {
        final DecodedTrace d = new DecodedTrace();
        final Map<Long, DecodedSession> open = new HashMap<>();
        final EventCursor cur = new EventCursor();
        final TraceReader.Outcome outcome;
        try {
            outcome = TraceReader.read(data, new TraceReader.Visitor() {
                @Override
                public void anchor(final long startEpochMs, final int utcOffsetSeconds) {
                    d.startEpochMs = startEpochMs;
                    d.utcOffsetSeconds = utcOffsetSeconds;
                }

                @Override
                public void thread(final long tid, final String name) {
                    d.threadNames.put(tid, name);
                }

                @Override
                public void clazz(final long baseId, final String className, final String[] sigs) {
                    for (int k = 0; k < sigs.length; k++) {
                        d.methodNames.put((int) baseId + k, className + "." + sigs[k]);
                    }
                }

                @Override
                public void exception(final long id, final String className) {
                    d.exceptionNames.put((int) id, className);
                }

                @Override
                public void gc(final long startTicks, final long durTicks, final int action, final String collector,
                        final String cause) {
                    d.gcPauses.add(new GcPause(startTicks, durTicks, action, collector, cause));
                }

                @Override
                public void chunk(final long tid, final long baseTicks, final byte[] bytes, final int off,
                        final int len, final boolean sessionEnd, final boolean truncated) {
                    DecodedSession s = open.get(tid);
                    if (s == null) {
                        s = new DecodedSession(d.sessions.size() + 1);
                        open.put(tid, s);
                        d.sessions.put(s.seq, s);
                    }
                    if (!truncated) {
                        s.chunks++;
                    }
                    cur.reset(bytes, off, len, baseTicks);
                    while (true) {
                        final EventCursor.Event e = cur.next();
                        if (e == EventCursor.Event.ENTER) {
                            if (s.rootId < 0) {
                                s.rootId = cur.methodId();
                            }
                            s.events.add(new Event(cur.ticks(), TAG_ENTER, cur.methodId(), -1));
                        } else if (e == EventCursor.Event.EXIT) {
                            final int exc = cur.exceptionId();
                            if (exc > 0 && !d.exceptionNames.containsKey(exc)) {
                                d.danglingExceptionRefs++;
                            }
                            s.events.add(new Event(cur.ticks(), exc >= 0 ? TAG_EXIT_THROW : TAG_EXIT, -1, exc));
                        } else if (e == EventCursor.Event.END) {
                            break;
                        } else if (!truncated) {
                            throw new IllegalStateException("chunk payload ends mid-event at " + cur.stopIndex());
                        } else {
                            break;
                        }
                    }
                    if (sessionEnd && !truncated) {
                        s.ended = true;
                        open.remove(tid);
                    }
                }
            });
        } catch (final CorruptTraceException e) {
            throw new IllegalStateException(e.getMessage() + " at " + e.offset(), e);
        }
        d.cleanEnd = outcome == TraceReader.Outcome.CLEAN;
        return d;
    }

    public static List<Node> toPreorder(final DecodedSession s) {
        final List<Node> out = new ArrayList<>();
        final List<Integer> stack = new ArrayList<>();
        for (final Event e : s.events) {
            if (e.tag() == TAG_ENTER) {
                out.add(new Node(e.methodId(), stack.size(), -1, true));
                stack.add(out.size() - 1);
            } else {
                if (stack.isEmpty()) {
                    throw new IllegalStateException("exit with no open frame in session #" + s.seq);
                }
                final int i = stack.removeLast();
                final Node n = out.get(i);
                out.set(i, new Node(n.methodId(), n.depth(), e.exceptionId(), false));
            }
        }
        return out;
    }
}
