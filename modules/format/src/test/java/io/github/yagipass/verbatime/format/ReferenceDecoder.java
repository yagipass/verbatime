package io.github.yagipass.verbatime.format;

import com.google.errorprone.annotations.Var;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReferenceDecoder {

  public enum SessionStart {
    MOVED_UP_TO_THE_THREADS_LAST_TICK,
    AS_RECORDED
  }

  public record Call(
      long tid,
      int seq,
      long startNs,
      long durNs,
      int methodId,
      int depth,
      long selfNs,
      int exceptionId,
      boolean unclosed) {

    public boolean thrown() {
      return exceptionId >= 0;
    }
  }

  public static final class Session {
    public int seq;

    public long tid;

    public int rootMethodId = -1;

    public long startNs;

    public long endNs;

    public boolean ended;

    public long callCount;
  }

  public record GcPause(long startNs, long durNs, int action, String name, String cause) {}

  public static final class Result {
    public final List<Call> calls = new ArrayList<>();

    public final List<GcPause> gc = new ArrayList<>();

    public final Map<Long, String> threadNames = new LinkedHashMap<>();

    public final Map<Integer, String> methodNames = new LinkedHashMap<>();

    public final Map<Integer, String> exceptionNames = new LinkedHashMap<>();

    public final List<Session> sessions = new ArrayList<>();

    public boolean truncated;

    public long corruptOffset = -1;

    public boolean corruptInChunk;

    public long startEpochMs;

    public int utcOffsetSeconds;
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

    final boolean inChunk;

    CorruptException(long offset) {
      this(offset, false);
    }

    CorruptException(long offset, boolean inChunk) {
      super(null, null, false, false);
      this.offset = offset;
      this.inChunk = inChunk;
    }
  }

  private static final class State {
    Session session;

    long lastTicks;

    final List<long[]> stack = new ArrayList<>();
  }

  private ReferenceDecoder() {}

  public static Result decode(byte[] data) {
    return decode(data, SessionStart.MOVED_UP_TO_THE_THREADS_LAST_TICK);
  }

  public static Result decode(byte[] data, SessionStart sessionStart) {
    Result res = new Result();
    int n = data.length;
    long tick = Vbtm.NANOS_PER_TICK;
    if (n < 4 || data[0] != 'v' || data[1] != 'b' || data[2] != 't' || data[3] != 'm') {
      throw new IllegalArgumentException("bad magic");
    }
    if (n > 4 && data[4] != 1) {
      throw new IllegalArgumentException("unsupported version " + data[4]);
    }
    Map<Long, State> states = new HashMap<>();
    int[] posBox = {5};
    @Var boolean endSeen = false;
    try {
      if (n > 5 && (data[5] & 0xFF) != Vbtm.RECORD_ANCHOR) {
        throw new CorruptException(5);
      }
      if (n < Vbtm.HEADER_BYTES) {
        throw TruncatedException.I;
      }
      ByteBuffer anchor = ByteBuffer.wrap(data, 6, Vbtm.ANCHOR_BYTES - 1);
      long epochMs = anchor.getLong();
      int offsetSeconds = anchor.getInt();
      if (offsetSeconds < -Vbtm.MAX_UTC_OFFSET_SECONDS
          || offsetSeconds > Vbtm.MAX_UTC_OFFSET_SECONDS) {
        throw new CorruptException(5);
      }
      res.startEpochMs = epochMs;
      res.utcOffsetSeconds = offsetSeconds;
      posBox[0] = Vbtm.HEADER_BYTES;
      while (posBox[0] < n) {
        int recStart = posBox[0];
        if (endSeen) {
          throw new CorruptException(recStart);
        }
        int type = data[posBox[0]++] & 0xFF;
        if (type == Vbtm.RECORD_THREAD) {
          long tid = varint(data, posBox);
          res.threadNames.put(tid, string(data, posBox, recStart));
        } else if (type == Vbtm.RECORD_CHUNK || type == Vbtm.RECORD_CHUNK_END) {
          chunk(data, posBox, recStart, type == Vbtm.RECORD_CHUNK_END, res, states, sessionStart);
        } else if (type == Vbtm.RECORD_CLASS) {
          long baseId = varint(data, posBox);
          long count = varint(data, posBox);
          if (baseId < 0
              || count < 0
              || baseId > Vbtm.METHOD_ID_LIMIT
              || count > Vbtm.METHOD_ID_LIMIT - baseId) {
            throw new CorruptException(recStart);
          }
          String cls = string(data, posBox, recStart);
          for (int k = 0; k < count; k++) {
            res.methodNames.put((int) (baseId + k), cls + "." + string(data, posBox, recStart));
          }
        } else if (type == Vbtm.RECORD_EXCEPTION) {
          long id = varint(data, posBox);
          if (id <= 0 || id >= Vbtm.EXCEPTION_ID_LIMIT) {
            throw new CorruptException(recStart);
          }
          res.exceptionNames.put((int) id, string(data, posBox, recStart));
        } else if (type == Vbtm.RECORD_GC) {
          long start = varint(data, posBox);
          long dur = varint(data, posBox);
          long action = varint(data, posBox);
          if (start < 0
              || start > Vbtm.MAX_TICKS
              || dur < 0
              || dur > Vbtm.MAX_TICKS - start
              || action < 0
              || action > Vbtm.GC_ACTION_MAJOR) {
            throw new CorruptException(recStart);
          }
          String[] labels = new String[2];
          for (int k = 0; k < 2; k++) {
            long len = varint(data, posBox);
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
          endSeen = true;
        } else {
          throw new CorruptException(recStart);
        }
      }
    } catch (TruncatedException t) {
      res.truncated = true;
    } catch (CorruptException c) {
      res.corruptOffset = c.offset;
      res.corruptInChunk = c.inChunk;
    }
    if (!endSeen && !res.truncated && res.corruptOffset < 0) {
      res.truncated = true;
    }
    List<Map.Entry<Long, State>> open = new ArrayList<>();
    for (Map.Entry<Long, State> e : states.entrySet()) {
      if (e.getValue().session != null) {
        open.add(e);
      }
    }
    open.sort(Comparator.comparingInt(e -> e.getValue().session.seq));
    for (Map.Entry<Long, State> e : open) {
      drain(res, states, e.getKey(), tick);
    }
    return res;
  }

  public static Verdict verdict(byte[] data) {
    return verdict(data, SessionStart.MOVED_UP_TO_THE_THREADS_LAST_TICK);
  }

  public static Verdict verdict(byte[] data, SessionStart sessionStart) {
    Result res;
    try {
      res = decode(data, sessionStart);
    } catch (IllegalArgumentException notARecording) {
      return Verdict.NOT_A_RECORDING;
    }
    if (res.corruptOffset >= 0) {
      return Verdict.corrupt(res.corruptOffset);
    }
    List<Verdict.Session> sessions = new ArrayList<>();
    for (Session s : res.sessions) {
      sessions.add(
          new Verdict.Session(
              s.tid,
              s.rootMethodId,
              s.callCount,
              s.ended,
              s.startNs / Vbtm.NANOS_PER_TICK,
              s.endNs / Vbtm.NANOS_PER_TICK));
    }
    return Verdict.readable(res.truncated, sessions);
  }

  private static void chunk(
      byte[] data,
      int[] posBox,
      int recStart,
      boolean endsSession,
      Result res,
      Map<Long, State> states,
      SessionStart sessionStart) {
    int n = data.length;
    long tick = Vbtm.NANOS_PER_TICK;
    long tid = varint(data, posBox);
    long baseTicks = varint(data, posBox);
    long payloadLen = varint(data, posBox);
    if (baseTicks < 0 || baseTicks > Vbtm.MAX_TICKS) {
      throw new CorruptException(recStart);
    }
    if (payloadLen < 0 || payloadLen > Vbtm.MAX_CHUNK_PAYLOAD_BYTES) {
      throw new CorruptException(recStart);
    }
    long end = posBox[0] + payloadLen;
    boolean partial = end > n;
    int limit = (int) Math.min(end, n);
    State st = states.computeIfAbsent(tid, k -> new State());
    if (st.session == null && sessionStart == SessionStart.AS_RECORDED) {
      st.lastTicks = 0;
    }
    long base = Math.max(baseTicks, st.lastTicks);
    if (st.session == null) {
      Session ns = new Session();
      ns.seq = res.sessions.size() + 1;
      ns.tid = tid;
      ns.startNs = base * tick;
      st.session = ns;
      st.lastTicks = base;
      res.sessions.add(ns);
    }
    Session s = st.session;
    @Var long ticks = base;
    @Var boolean first = true;
    @Var int decoded = 0;
    while (posBox[0] < limit) {
      int mark = posBox[0];
      try {
        long v = eventVarint(data, posBox, limit, mark);
        if ((v & 1) == 0) {
          if (!first) {
            if ((v >>> 1) > Vbtm.MAX_TICKS - ticks) {
              throw new CorruptException(mark, true);
            }
            ticks += v >>> 1;
          }
          long methodId = eventVarint(data, posBox, limit, mark);
          if (methodId < 0 || methodId >= Vbtm.METHOD_ID_LIMIT) {
            throw new CorruptException(mark, true);
          }
          if (s.rootMethodId < 0) {
            s.rootMethodId = (int) methodId;
          }
          st.stack.add(new long[] {ticks, 0, methodId});
        } else {
          @Var int exc = -1;
          if ((v & 2) != 0) {
            long e = eventVarint(data, posBox, limit, mark);
            if (e < 0 || e >= Vbtm.EXCEPTION_ID_LIMIT) {
              throw new CorruptException(mark, true);
            }
            exc = (int) e;
          }
          if (!first) {
            if ((v >>> 2) > Vbtm.MAX_TICKS - ticks) {
              throw new CorruptException(mark, true);
            }
            ticks += v >>> 2;
          }
          if (st.stack.isEmpty()) {
            throw new CorruptException(mark, true);
          }
          long[] top = st.stack.remove(st.stack.size() - 1);
          long startNs = top[0] * tick;
          long durNs = ticks * tick - startNs;
          long selfNs = Math.max(durNs - top[1], 0);
          if (!st.stack.isEmpty()) {
            st.stack.get(st.stack.size() - 1)[1] += durNs;
          }
          s.callCount++;
          res.calls.add(
              new Call(
                  tid, s.seq, startNs, durNs, (int) top[2], st.stack.size(), selfNs, exc, false));
        }
        first = false;
        decoded++;
      } catch (TruncatedException t) {
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
      throw new CorruptException(posBox[0], true);
    }
    if (endsSession) {
      s.ended = true;
      drain(res, states, tid, tick);
    }
  }

  private static void drain(Result res, Map<Long, State> states, long tid, long tick) {
    State st = states.get(tid);
    Session s = st.session;
    long endNs = st.lastTicks * tick;
    while (!st.stack.isEmpty()) {
      long[] top = st.stack.remove(st.stack.size() - 1);
      long startNs = top[0] * tick;
      long durNs = endNs - startNs;
      long selfNs = Math.max(durNs - top[1], 0);
      if (!st.stack.isEmpty()) {
        st.stack.get(st.stack.size() - 1)[1] += durNs;
      }
      s.callCount++;
      res.calls.add(
          new Call(tid, s.seq, startNs, durNs, (int) top[2], st.stack.size(), selfNs, -1, true));
    }
    s.endNs = endNs;
    st.session = null;
  }

  private static String string(byte[] data, int[] pos, int recStart) {
    long len = varint(data, pos);
    if (len < 0 || len > Vbtm.MAX_STRING_BYTES) {
      throw new CorruptException(recStart);
    }
    if (len > data.length - pos[0]) {
      throw TruncatedException.I;
    }
    String s = new String(data, pos[0], (int) len, StandardCharsets.UTF_8);
    pos[0] += (int) len;
    return s;
  }

  private static long varint(byte[] data, int[] pos) {
    @Var long v = 0;
    @Var int shift = 0;
    while (true) {
      if (pos[0] >= data.length) {
        throw TruncatedException.I;
      }
      int b = data[pos[0]++] & 0xFF;
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

  private static long eventVarint(byte[] data, int[] pos, int end, int eventStart) {
    @Var long v = 0;
    @Var int shift = 0;
    while (true) {
      if (pos[0] >= end) {
        throw TruncatedException.I;
      }
      int b = data[pos[0]++] & 0xFF;
      v |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return v;
      }
      shift += 7;
      if (shift > 63) {
        throw new CorruptException(eventStart, true);
      }
    }
  }
}
