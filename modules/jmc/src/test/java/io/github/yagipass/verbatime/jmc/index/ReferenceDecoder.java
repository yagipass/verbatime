package io.github.yagipass.verbatime.jmc.index;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.format.Vbtm;

public final class ReferenceDecoder {

    public record Call(long tid, int seq, long startNs, long durNs, int methodId, int depth, long selfNs, int exceptionId,
            boolean unclosed) {

        public boolean thrown() {
            return exceptionId >= 0;
        }
    }

    static final class Session {
        int seq;

        long tid;

        int rootMethodId = -1;

        long startNs;

        long endNs;

        boolean ended;

        long callCount;
    }

    record GcPause(long startNs, long durNs, int action, String name, String cause) {
    }

    public static final class Result {
        public final List<Call> calls = new ArrayList<>();

        final List<GcPause> gc = new ArrayList<>();

        final Map<Long, String> threadNames = new LinkedHashMap<>();

        final Map<Integer, String> methodNames = new LinkedHashMap<>();

        final Map<Integer, String> exceptionNames = new LinkedHashMap<>();

        final List<Session> sessions = new ArrayList<>();

        boolean truncated;

        long corruptOffset = -1;

        long startEpochMs;

        int utcOffsetSeconds;
    }

    private static final class TruncatedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("StaticAssignmentOfThrowable")
        static final TruncatedException I = new TruncatedException();

        private TruncatedException() {
            super(null, null, false, false);
        }
    }

    private static final class CorruptException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        final long offset;

        CorruptException(final long offset) {
            super(null, null, false, false);
            this.offset = offset;
        }
    }

    private static final class State {
        Session session;

        long lastTicks;

        final List<long[]> stack = new ArrayList<>();
    }

    public static Result decode(final byte[] data) {
        final Result res = new Result();
        final int n = data.length;
        final long tick = Vbtm.NANOS_PER_TICK;
        if (n < 4 || data[0] != 'v' || data[1] != 'b' || data[2] != 't' || data[3] != 'm') {
            throw new IllegalArgumentException("bad magic");
        }
        if (n > 4 && data[4] != 1) {
            throw new IllegalArgumentException("unsupported version " + data[4]);
        }
        final Map<Long, State> states = new HashMap<>();
        final int[] posBox = { 5 };
        boolean endSeen = false;
        try {
            if (n > 5 && (data[5] & 0xFF) != Vbtm.RECORD_ANCHOR) {
                throw new CorruptException(5);
            }
            if (n < Vbtm.HEADER_BYTES) {
                throw TruncatedException.I;
            }
            final ByteBuffer anchor = ByteBuffer.wrap(data, 6, Vbtm.ANCHOR_BYTES - 1);
            final long epochMs = anchor.getLong();
            final int offsetSeconds = anchor.getInt();
            if (offsetSeconds < -Vbtm.MAX_UTC_OFFSET_SECONDS || offsetSeconds > Vbtm.MAX_UTC_OFFSET_SECONDS) {
                throw new CorruptException(5);
            }
            res.startEpochMs = epochMs;
            res.utcOffsetSeconds = offsetSeconds;
            posBox[0] = Vbtm.HEADER_BYTES;
            while (posBox[0] < n) {
                final int recStart = posBox[0];
                final int type = data[posBox[0]++] & 0xFF;
                if (type == Vbtm.RECORD_THREAD) {
                    final long tid = varint(data, posBox);
                    final int len = (int) varint(data, posBox);
                    if (len < 0) {
                        throw new CorruptException(recStart);
                    }
                    if (posBox[0] + len > n) {
                        throw TruncatedException.I;
                    }
                    res.threadNames.put(tid, new String(data, posBox[0], len, StandardCharsets.UTF_8));
                    posBox[0] += len;
                } else if (type == Vbtm.RECORD_CHUNK || type == Vbtm.RECORD_CHUNK_END) {
                    final long tid = varint(data, posBox);
                    final long baseTicks = varint(data, posBox);
                    final long payloadLen = varint(data, posBox);
                    if (baseTicks < 0 || baseTicks > Vbtm.MAX_TICKS) {
                        throw new CorruptException(recStart);
                    }
                    final long end = posBox[0] + payloadLen;
                    final boolean partial = end > n;
                    final int limit = (int) Math.min(end, n);
                    final State st = states.computeIfAbsent(tid, k -> new State());
                    if (st.session == null) {
                        final Session ns = new Session();
                        ns.seq = res.sessions.size() + 1;
                        ns.tid = tid;
                        ns.startNs = baseTicks * tick;
                        st.session = ns;
                        st.lastTicks = baseTicks;
                        res.sessions.add(ns);
                    }
                    final Session s = st.session;
                    long ticks = baseTicks;
                    boolean first = true;
                    int decoded = 0;
                    while (posBox[0] < limit) {
                        final int mark = posBox[0];
                        try {
                            final long v = varint(data, posBox, limit);
                            if ((v & 1) == 0) {
                                if (!first) {
                                    if ((v >>> 1) > Vbtm.MAX_TICKS - ticks) {
                                        throw new CorruptException(mark);
                                    }
                                    ticks += v >>> 1;
                                }
                                final long methodId = varint(data, posBox, limit);
                                if (s.rootMethodId < 0) {
                                    s.rootMethodId = (int) methodId;
                                }
                                st.stack.add(new long[] { ticks, 0, methodId });
                            } else {
                                int exc = -1;
                                if ((v & 2) != 0) {
                                    final long e = varint(data, posBox, limit);
                                    if (e >= Vbtm.EXCEPTION_ID_LIMIT) {
                                        throw new CorruptException(mark);
                                    }
                                    exc = (int) e;
                                }
                                if (!first) {
                                    if ((v >>> 2) > Vbtm.MAX_TICKS - ticks) {
                                        throw new CorruptException(mark);
                                    }
                                    ticks += v >>> 2;
                                }
                                if (st.stack.isEmpty()) {
                                    throw new CorruptException(mark);
                                }
                                final long[] top = st.stack.remove(st.stack.size() - 1);
                                final long startNs = top[0] * tick;
                                final long durNs = ticks * tick - startNs;
                                final long selfNs = Math.max(durNs - top[1], 0);
                                if (!st.stack.isEmpty()) {
                                    st.stack.get(st.stack.size() - 1)[1] += durNs;
                                }
                                s.callCount++;
                                res.calls.add(new Call(tid, s.seq, startNs, durNs, (int) top[2], st.stack.size(),
                                        selfNs, exc, false));
                            }
                            first = false;
                            decoded++;
                        } catch (final TruncatedException t) {
                            posBox[0] = mark;
                            break;
                        }
                    }
                    if (decoded > 0) {
                        st.lastTicks = ticks;
                    }
                    if (partial) {
                        throw TruncatedException.I;
                    }
                    if (posBox[0] != end) {
                        throw new CorruptException(posBox[0]);
                    }
                    if (type == Vbtm.RECORD_CHUNK_END) {
                        s.ended = true;
                        drain(res, states, tid, tick);
                    }
                } else if (type == Vbtm.RECORD_CLASS) {
                    final long baseId = varint(data, posBox);
                    final long count = varint(data, posBox);
                    if (baseId < 0 || count < 0 || baseId > Vbtm.METHOD_ID_LIMIT
                            || count > Vbtm.METHOD_ID_LIMIT - baseId) {
                        throw new CorruptException(recStart);
                    }
                    final int clen = (int) varint(data, posBox);
                    if (clen < 0) {
                        throw new CorruptException(recStart);
                    }
                    if (posBox[0] + clen > n) {
                        throw TruncatedException.I;
                    }
                    final String cls = new String(data, posBox[0], clen, StandardCharsets.UTF_8);
                    posBox[0] += clen;
                    for (int k = 0; k < count; k++) {
                        final int slen = (int) varint(data, posBox);
                        if (slen < 0) {
                            throw new CorruptException(recStart);
                        }
                        if (posBox[0] + slen > n) {
                            throw TruncatedException.I;
                        }
                        res.methodNames.put((int) (baseId + k),
                                cls + "." + new String(data, posBox[0], slen, StandardCharsets.UTF_8));
                        posBox[0] += slen;
                    }
                } else if (type == Vbtm.RECORD_EXCEPTION) {
                    final long id = varint(data, posBox);
                    if (id <= 0 || id >= Vbtm.EXCEPTION_ID_LIMIT) {
                        throw new CorruptException(recStart);
                    }
                    final int len = (int) varint(data, posBox);
                    if (len < 0) {
                        throw new CorruptException(recStart);
                    }
                    if (posBox[0] + len > n) {
                        throw TruncatedException.I;
                    }
                    res.exceptionNames.put((int) id, new String(data, posBox[0], len, StandardCharsets.UTF_8));
                    posBox[0] += len;
                } else if (type == Vbtm.RECORD_GC) {
                    final long start = varint(data, posBox);
                    final long dur = varint(data, posBox);
                    final long action = varint(data, posBox);
                    if (start < 0 || start > Vbtm.MAX_TICKS || dur < 0 || dur > Vbtm.MAX_TICKS - start || action < 0
                            || action > Vbtm.GC_ACTION_MAJOR) {
                        throw new CorruptException(recStart);
                    }
                    final String[] labels = new String[2];
                    for (int k = 0; k < 2; k++) {
                        final long len = varint(data, posBox);
                        if (len < 0 || len > Vbtm.MAX_GC_LABEL_BYTES) {
                            throw new CorruptException(recStart);
                        }
                        if (posBox[0] + len > n) {
                            throw TruncatedException.I;
                        }
                        labels[k] = new String(data, posBox[0], (int) len, StandardCharsets.UTF_8);
                        posBox[0] += (int) len;
                    }
                    res.gc.add(new GcPause(start * tick, dur * tick, (int) action, labels[0], labels[1]));
                } else if (type == Vbtm.RECORD_END) {
                    if (posBox[0] != n) {
                        throw new CorruptException(recStart);
                    }
                    endSeen = true;
                } else {
                    throw new CorruptException(recStart);
                }
            }
        } catch (final TruncatedException t) {
            res.truncated = true;
        } catch (final CorruptException c) {
            res.corruptOffset = c.offset;
        }
        if (!endSeen && !res.truncated && res.corruptOffset < 0) {
            res.truncated = true;
        }
        final List<Map.Entry<Long, State>> open = new ArrayList<>();
        for (final Map.Entry<Long, State> e : states.entrySet()) {
            if (e.getValue().session != null) {
                open.add(e);
            }
        }
        open.sort(Comparator.comparingInt(e -> e.getValue().session.seq));
        for (final Map.Entry<Long, State> e : open) {
            drain(res, states, e.getKey(), tick);
        }
        return res;
    }

    private static void drain(final Result res, final Map<Long, State> states, final long tid, final long tick) {
        final State st = states.get(tid);
        final Session s = st.session;
        final long endNs = st.lastTicks * tick;
        while (!st.stack.isEmpty()) {
            final long[] top = st.stack.remove(st.stack.size() - 1);
            final long startNs = top[0] * tick;
            final long durNs = endNs - startNs;
            final long selfNs = Math.max(durNs - top[1], 0);
            if (!st.stack.isEmpty()) {
                st.stack.get(st.stack.size() - 1)[1] += durNs;
            }
            s.callCount++;
            res.calls.add(new Call(tid, s.seq, startNs, durNs, (int) top[2], st.stack.size(), selfNs, -1, true));
        }
        s.endNs = endNs;
        st.session = null;
    }

    private static long varint(final byte[] data, final int[] pos) {
        return varint(data, pos, data.length);
    }

    private static long varint(final byte[] data, final int[] pos, final int end) {
        long v = 0;
        int shift = 0;
        while (true) {
            if (pos[0] >= end) {
                throw TruncatedException.I;
            }
            final int b = data[pos[0]++] & 0xFF;
            v |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return v;
            }
            shift += 7;
            if (shift > 63) {
                throw new CorruptException(pos[0]);
            }
        }
    }
}
