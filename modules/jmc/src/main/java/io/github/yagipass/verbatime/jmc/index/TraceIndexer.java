package io.github.yagipass.verbatime.jmc.index;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.FrameStack;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.SessionState;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

public final class TraceIndexer {

  public interface ProgressListener {

    ProgressListener NONE = (done, total) -> false;

    boolean report(long bytesDone, long bytesTotal);
  }

  public static final class CancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CancelledException() {
      super(null, null, false, false);
    }
  }

  public static final class NotTraceFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    private NotTraceFormatException(String message) {
      super(message);
    }
  }

  private static final class ThreadState {

    private final long tid;

    private @Nullable SessionState session;

    private long lastTicks;

    private final FrameStack stack;

    private int maxDepth;

    private long totalCalls;

    private final ChunkTable chunks;

    private final Calls overview;

    private ThreadState(long tid) {
      this.tid = tid;
      stack = new FrameStack();
      chunks = new ChunkTable();
      overview = new Calls(tid);
    }

    private ThreadState(ThreadState src, List<SessionState> sessions) {
      tid = src.tid;
      session = src.session == null ? null : sessions.get(src.session.seq - 1);
      lastTicks = src.lastTicks;
      stack = src.stack.copy();
      maxDepth = src.maxDepth;
      totalCalls = src.totalCalls;
      chunks = src.chunks.copy();
      overview = src.overview.copy();
    }

    private ThreadIndex freeze() {
      return new ThreadIndex(tid, maxDepth, totalCalls, chunks, overview);
    }
  }

  private static final class TruncatedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("StaticAssignmentOfThrowable")
    private static final TruncatedException INSTANCE = new TruncatedException();

    private TruncatedException() {
      super(null, null, false, false);
    }
  }

  private static final class CorruptException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final long offset;

    private final String reason;

    private CorruptException(long offset, String reason) {
      super(null, null, false, false);
      this.offset = offset;
      this.reason = reason;
    }
  }

  public static final int DEFAULT_OVERVIEW_BUDGET = 120_000;

  private static final AtomicLong GENERATIONS = new AtomicLong();

  private final Path path;

  private final int overviewBudget;

  private final Object lock = new Object();

  private boolean closed;

  private @Nullable MappedTrace buf;

  private long size;

  private long pos;

  private TraceSnapshot.Builder data;

  private long[] durationHistogram;

  private Map<Long, ThreadState> states;

  private byte[] scratch = new byte[1 << 16];

  private final EventCursor eventCursor = new EventCursor();

  private long overviewThresholdNs;

  private long overviewTotal;

  private long compactTrigger;

  private long minNs;

  private long maxNs;

  private int recordCounter;

  private boolean endSeen;

  private long endOffset;

  private boolean magicChecked;

  private boolean versionChecked;

  private boolean anchorRead;

  private byte @Nullable [] header;

  private final Map<String, String> gcLabels = new HashMap<>();

  private TraceIndexer(Path path, int overviewBudget) {
    this.path = path;
    this.overviewBudget = Math.max(overviewBudget, 1);
    reset();
  }

  private TraceIndexer(TraceIndexer src) {
    path = src.path;
    overviewBudget = src.overviewBudget;
    size = src.size;
    pos = src.pos;
    data = src.data.copy();
    durationHistogram = src.durationHistogram.clone();
    states = new HashMap<>();
    for (Map.Entry<Long, ThreadState> e : src.states.entrySet()) {
      states.put(e.getKey(), new ThreadState(e.getValue(), data.sessions));
    }
    overviewThresholdNs = src.overviewThresholdNs;
    overviewTotal = src.overviewTotal;
    compactTrigger = src.compactTrigger;
    minNs = src.minNs;
    maxNs = src.maxNs;
    endSeen = src.endSeen;
    endOffset = src.endOffset;
    magicChecked = src.magicChecked;
    versionChecked = src.versionChecked;
    anchorRead = src.anchorRead;
  }

  public static TraceIndexer open(Path path, int overviewBudget) {
    return new TraceIndexer(path, overviewBudget);
  }

  public void close() {
    MappedTrace b;
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      b = buf;
    }
    if (b != null) {
      b.release();
    }
  }

  public void advance(ProgressListener progress) throws IOException {
    @Var MappedTrace guard = acquire();
    try {
      long fileSize = Files.size(path);
      if (guard == null || fileSize != size) {
        guard = remap(guard);
        if (size < pos || headerChanged(guard)) {
          reset();
        }
      }
      if (data.corruption != null) {
        return;
      }
      if (!magicChecked) {
        checkMagic(guard);
      }
      if (!versionChecked && !checkVersion(guard)) {
        return;
      }
      if (!anchorRead && !readAnchor(guard)) {
        return;
      }
      try {
        scan(guard, false, progress, pos);
      } catch (CorruptException c) {
        corrupt(c.offset, c.reason);
      }
    } finally {
      if (guard != null) {
        guard.release();
      }
    }
  }

  public TraceSnapshot snapshot() {
    MappedTrace guard = acquire();
    if (guard == null) {
      throw new IllegalStateException("advance() before snapshot()");
    }
    try {
      TraceIndexer c = new TraceIndexer(this);
      c.complete(guard);
      return c.data.build(guard);
    } finally {
      guard.release();
    }
  }

  static TraceSnapshot index(Path path) throws IOException {
    return index(path, DEFAULT_OVERVIEW_BUDGET, ProgressListener.NONE);
  }

  static TraceSnapshot index(Path path, int overviewBudget, ProgressListener progress)
      throws IOException {
    TraceIndexer ix = open(path, overviewBudget);
    try {
      ix.advance(progress);
      return ix.snapshot();
    } finally {
      ix.close();
    }
  }

  private static String faultReason(EventCursor.Fault fault, long value, long at) {
    return switch (fault) {
      case VARINT_TOO_LONG -> "varint too long at offset " + at;
      case METHOD_ID_LIMIT -> "method id " + value + " is outside the 2^22 format range";
      case EXCEPTION_ID_LIMIT -> "exception id " + value + " is outside the 2^22 format range";
      case TICKS_LIMIT ->
          "tick delta " + value + " pushes the clock past the format limit at offset " + at;
    };
  }

  private void reset() {
    data = new TraceSnapshot.Builder(path);
    data.generation = GENERATIONS.incrementAndGet();
    durationHistogram = new long[DurationHistogram.SIZE];
    states = new HashMap<>();
    pos = 0;
    overviewThresholdNs = 0;
    overviewTotal = 0;
    compactTrigger = 2L * overviewBudget;
    minNs = Long.MAX_VALUE;
    maxNs = 0;
    endSeen = false;
    endOffset = 0;
    magicChecked = false;
    versionChecked = false;
    anchorRead = false;
  }

  private @Nullable MappedTrace acquire() {
    synchronized (lock) {
      if (closed) {
        throw new CancelledException();
      }
      if (buf != null) {
        buf.retain();
      }
      return buf;
    }
  }

  private MappedTrace remap(@Nullable MappedTrace guard) throws IOException {
    MappedTrace fresh = MappedTrace.open(path);
    MappedTrace old;
    synchronized (lock) {
      if (closed) {
        fresh.release();
        throw new CancelledException();
      }
      old = buf;
      buf = fresh;
      fresh.retain();
    }
    size = fresh.size();
    if (guard != null) {
      guard.release();
    }
    if (old != null) {
      old.release();
    }
    return fresh;
  }

  private void checkMagic(MappedTrace in) throws NotTraceFormatException {
    if (size < Vbtm.MAGIC_BYTES) {
      throw new NotTraceFormatException(
          path.getFileName()
              + ": file shorter than the "
              + Vbtm.MAGIC_BYTES
              + "-byte magic \""
              + Vbtm.MAGIC
              + "\"");
    }
    byte[] magic = new byte[Vbtm.MAGIC_BYTES];
    in.copy(0, magic, magic.length);
    if (!Vbtm.hasMagic(magic, 0, magic.length)) {
      throw new NotTraceFormatException(
          path.getFileName()
              + ": not a supported recording, expected the magic \""
              + Vbtm.MAGIC
              + "\"");
    }
    pos = Vbtm.MAGIC_BYTES;
    magicChecked = true;
  }

  private boolean checkVersion(MappedTrace in) throws NotTraceFormatException {
    if (size <= Vbtm.VERSION_OFFSET) {
      return false;
    }
    int version = in.byteAt(Vbtm.VERSION_OFFSET);
    if (version != Vbtm.VERSION) {
      throw new NotTraceFormatException(
          path.getFileName()
              + ": format version "
              + version
              + " is not supported, this plugin reads version "
              + Vbtm.VERSION);
    }
    pos = Vbtm.ANCHOR_OFFSET;
    versionChecked = true;
    return true;
  }

  private boolean readAnchor(MappedTrace in) {
    long at = Vbtm.ANCHOR_OFFSET;
    if (size <= at) {
      return false;
    }
    if (in.byteAt(at) != Vbtm.RECORD_ANCHOR) {
      corrupt(at, "expected the anchor record at offset " + at);
      return false;
    }
    if (size < Vbtm.HEADER_BYTES) {
      return false;
    }
    byte[] body = new byte[Vbtm.ANCHOR_BYTES - 1];
    in.copy(at + 1, body, body.length);
    ByteBuffer b = ByteBuffer.wrap(body);
    long epochMs = b.getLong();
    int offsetSeconds = b.getInt();
    if (offsetSeconds < -Vbtm.MAX_UTC_OFFSET_SECONDS
        || offsetSeconds > Vbtm.MAX_UTC_OFFSET_SECONDS) {
      corrupt(
          at,
          "UTC offset " + offsetSeconds + " s out of range in the anchor record at offset " + at);
      return false;
    }
    data.startEpochMs = epochMs;
    data.utcOffsetSeconds = offsetSeconds;
    header = new byte[Vbtm.HEADER_BYTES];
    in.copy(0, header, header.length);
    pos = Vbtm.HEADER_BYTES;
    anchorRead = true;
    return true;
  }

  private boolean headerChanged(MappedTrace in) {
    if (!anchorRead) {
      return false;
    }
    byte[] now = new byte[Vbtm.HEADER_BYTES];
    in.copy(0, now, now.length);
    return !Arrays.equals(header, now);
  }

  private void complete(MappedTrace in) {
    if (data.corruption == null && !anchorRead) {
      data.truncated = true;
    } else if (data.corruption == null) {
      try {
        scan(in, true, ProgressListener.NONE, pos);
        if (!endSeen && data.corruption == null) {
          data.truncated = true;
        }
      } catch (TruncatedException t) {
        data.truncated = true;
      } catch (CorruptException c) {
        corrupt(c.offset, c.reason);
      }
    }
    finish();
  }

  private void scan(MappedTrace in, boolean partialOk, ProgressListener progress, long start) {
    while (pos < size) {
      if (endSeen) {
        corrupt(endOffset, "data after the end-of-recording footer at offset " + endOffset);
        return;
      }
      if ((++recordCounter & 255) == 0 && progress.report(pos - start, size - start)) {
        throw new CancelledException();
      }
      long recStart = pos;
      int type = in.byteAt(pos++);
      try {
        switch (type) {
          case Vbtm.RECORD_THREAD -> {
            long tid = varint(in);
            String name = string(in, recStart, varint(in));
            data.threadNames.put(tid, name);
          }
          case Vbtm.RECORD_CHUNK, Vbtm.RECORD_CHUNK_END -> {
            if (!readChunk(in, recStart, type == Vbtm.RECORD_CHUNK_END, partialOk)) {
              return;
            }
            if (progress.report(pos - start, size - start)) {
              throw new CancelledException();
            }
          }
          case Vbtm.RECORD_CLASS -> {
            long baseId = varint(in);
            long count = varint(in);
            if (baseId < 0
                || count < 0
                || baseId > Vbtm.METHOD_ID_LIMIT
                || count > Vbtm.METHOD_ID_LIMIT - baseId) {
              corrupt(recStart, "method ids exceed the 2^22 format limit");
              return;
            }
            String cls = string(in, recStart, varint(in));
            String[] sigs = new String[(int) Math.min(count, size - pos)];
            @Var int parsed = 0;
            try {
              for (; parsed < count; parsed++) {
                sigs[parsed] = string(in, recStart, varint(in));
              }
            } catch (TruncatedException t) {
              if (partialOk) {
                registerMethods(baseId, count, cls, sigs, parsed);
              }
              throw t;
            }
            registerMethods(baseId, count, cls, sigs, parsed);
          }
          case Vbtm.RECORD_EXCEPTION -> {
            long id = varint(in);
            if (id <= 0 || id >= Vbtm.EXCEPTION_ID_LIMIT) {
              corrupt(
                  recStart,
                  "exception id "
                      + id
                      + " outside 1.."
                      + (Vbtm.EXCEPTION_ID_LIMIT - 1)
                      + " at offset "
                      + recStart);
              return;
            }
            String name = string(in, recStart, varint(in));
            ensureExceptionNames((int) id + 1);
            if (data.exceptionNames[(int) id] == null) {
              data.totalExceptions++;
            }
            data.exceptionNames[(int) id] = name;
          }
          case Vbtm.RECORD_GC -> {
            long gcStart = varint(in);
            long durTicks = varint(in);
            long action = varint(in);
            if (gcStart < 0
                || gcStart > Vbtm.MAX_TICKS
                || durTicks < 0
                || durTicks > Vbtm.MAX_TICKS - gcStart) {
              corrupt(recStart, "GC pause ticks out of range at offset " + recStart);
              return;
            }
            if (action < 0 || action > Vbtm.GC_ACTION_MAJOR) {
              corrupt(recStart, "unknown GC action " + action + " at offset " + recStart);
              return;
            }
            long nameLen = varint(in);
            if (nameLen > Vbtm.MAX_GC_LABEL_BYTES) {
              corrupt(recStart, "GC collector name of " + nameLen + " bytes at offset " + recStart);
              return;
            }
            String name = gcLabel(string(in, recStart, nameLen));
            long causeLen = varint(in);
            if (causeLen > Vbtm.MAX_GC_LABEL_BYTES) {
              corrupt(recStart, "GC cause of " + causeLen + " bytes at offset " + recStart);
              return;
            }
            String cause = gcLabel(string(in, recStart, causeLen));
            data.gc.append(
                gcStart * Vbtm.NANOS_PER_TICK,
                durTicks * Vbtm.NANOS_PER_TICK,
                (int) action,
                name,
                cause);
          }
          case Vbtm.RECORD_END -> {
            endSeen = true;
            endOffset = recStart;
          }
          default -> {
            corrupt(recStart, "unknown record type " + type + " at offset " + recStart);
            return;
          }
        }
      } catch (TruncatedException t) {
        if (partialOk) {
          throw t;
        }
        pos = recStart;
        return;
      }
    }
  }

  private boolean readChunk(MappedTrace in, long recStart, boolean endsSession, boolean partialOk) {
    long tid = varint(in);
    long baseTicks = varint(in);
    long payloadLen = varint(in);
    long payloadStart = pos;
    if (baseTicks < 0 || baseTicks > Vbtm.MAX_TICKS) {
      corrupt(recStart, "chunk base ticks out of range at offset " + recStart);
      return false;
    }
    if (payloadLen < 0 || payloadLen > Vbtm.MAX_CHUNK_PAYLOAD_BYTES) {
      corrupt(payloadStart, "implausible chunk payload length " + payloadLen);
      return false;
    }
    long end = payloadStart + payloadLen;
    boolean partial = end > size;
    if (partial && !partialOk) {
      throw TruncatedException.INSTANCE;
    }
    long limit = Math.min(end, size);

    ThreadState st = states.computeIfAbsent(tid, ThreadState::new);
    @Var long base = baseTicks;
    if (base < st.lastTicks) {
      base = st.lastTicks;
    }
    @Var SessionState s = st.session;
    if (s == null) {
      s = st.session = new SessionState(data.sessions.size() + 1, tid, base * Vbtm.NANOS_PER_TICK);
      data.sessions.add(s);
      s.firstChunk = st.chunks.count;
      st.lastTicks = base;
    }
    long stop = decodePayload(in, st, s, payloadStart, (int) (limit - payloadStart), base);
    int ci = st.chunks.count - 1;
    s.lastChunk = ci;
    pos = stop;
    if (data.corruption != null) {
      return false;
    }
    if (partial) {
      throw TruncatedException.INSTANCE;
    }
    if (stop != end) {
      corrupt(stop, "chunk payload ends mid-event at offset " + stop);
      return false;
    }
    if (endsSession) {
      st.chunks.markSessionEnd(ci);
      s.ended = true;
      closeOpenCalls(st, s);
      st.session = null;
    }
    return true;
  }

  private long decodePayload(
      MappedTrace in, ThreadState st, SessionState s, long fileOffset, int len, long baseTicks) {
    if (scratch.length < len) {
      scratch = DecodeScratch.allocate(len);
    }
    in.copy(fileOffset, scratch, len);
    int openDepthAtStart = st.stack.depth();
    EventCursor cur = eventCursor;
    cur.reset(scratch, 0, len, baseTicks);
    FrameStack stack = st.stack;
    while (true) {
      EventCursor.Event e = cur.next();
      if (e == EventCursor.Event.ENTER) {
        if (s.rootMethodId < 0) {
          s.rootMethodId = cur.methodId();
        }
        stack.push(cur.ticks(), cur.methodId());
        if (stack.depth() - 1 > st.maxDepth) {
          st.maxDepth = stack.depth() - 1;
        }
      } else if (e == EventCursor.Event.EXIT) {
        if (stack.depth() == 0) {
          long at = fileOffset + cur.eventIndex();
          corrupt(at, "exit with no open frame in session #" + s.seq + " at offset " + at);
          if (cur.decodedEvents() > 1) {
            st.lastTicks = cur.ticks();
          }
          st.chunks.append(fileOffset, at, baseTicks, cur.ticks(), openDepthAtStart);
          return at;
        }
        stack.pop(cur.ticks());
        onCall(
            st,
            s,
            stack.startNs(),
            stack.durNs(),
            stack.methodId(),
            stack.depth(),
            stack.selfNs(),
            false,
            cur.exceptionId());
      } else {
        if (e == EventCursor.Event.CORRUPT) {
          corrupt(
              fileOffset + cur.eventIndex(),
              faultReason(
                  Objects.requireNonNull(cur.fault()),
                  cur.faultValue(),
                  fileOffset + cur.eventIndex()));
        }
        if (cur.decodedEvents() > 0) {
          st.lastTicks = cur.ticks();
        }
        long stop = fileOffset + cur.stopIndex();
        st.chunks.append(fileOffset, stop, baseTicks, cur.ticks(), openDepthAtStart);
        return stop;
      }
    }
  }

  private void closeOpenCalls(ThreadState st, SessionState s) {
    long endTicks = st.lastTicks;
    FrameStack stack = st.stack;
    while (stack.depth() > 0) {
      stack.pop(endTicks);
      onCall(
          st,
          s,
          stack.startNs(),
          stack.durNs(),
          stack.methodId(),
          stack.depth(),
          stack.selfNs(),
          true,
          -1);
    }
    s.endNs = endTicks * Vbtm.NANOS_PER_TICK;
  }

  private void onCall(
      ThreadState st,
      SessionState s,
      long startNs,
      long durNs,
      int methodId,
      int depth,
      long selfNs,
      boolean unclosed,
      int exc) {
    data.totalCalls++;
    st.totalCalls++;
    s.callCount++;
    long endNs = startNs + durNs;
    if (startNs < minNs) {
      minNs = startNs;
    }
    if (endNs > maxNs) {
      maxNs = endNs;
    }
    durationHistogram[DurationHistogram.bucketIndex(durNs)]++;
    if (!unclosed) {
      ensureAgg(methodId);
      data.callsByMethod[methodId]++;
    }
    if (durNs >= overviewThresholdNs || unclosed) {
      appendOverview(st, startNs, durNs, selfNs, methodId, depth, unclosed, exc);
    }
  }

  private void appendOverview(
      ThreadState st,
      long startNs,
      long durNs,
      long selfNs,
      int methodId,
      int depth,
      boolean unclosed,
      int exc) {
    st.overview.add(startNs, durNs, selfNs, methodId, depth, unclosed, exc);
    if (++overviewTotal > compactTrigger) {
      compact();
    }
  }

  private void compact() {
    long newD =
        DurationHistogram.chooseThreshold(durationHistogram, data.totalCalls, overviewBudget);
    if (newD <= overviewThresholdNs) {
      compactTrigger *= 2;
      return;
    }
    overviewThresholdNs = newD;
    overviewTotal = dropBelow(newD);
  }

  private long dropBelow(long d) {
    @Var long kept = 0;
    for (ThreadState st : states.values()) {
      kept += st.overview.dropShorterThan(d);
    }
    return kept;
  }

  private void finish() {
    List<ThreadState> open = new ArrayList<>();
    for (ThreadState st : states.values()) {
      if (st.session != null) {
        open.add(st);
      }
    }
    open.sort(Comparator.comparingInt(st -> Objects.requireNonNull(st.session).seq));
    for (ThreadState st : open) {
      closeOpenCalls(st, Objects.requireNonNull(st.session));
      st.session = null;
    }

    long finalD =
        DurationHistogram.chooseThreshold(durationHistogram, data.totalCalls, overviewBudget);
    if (finalD > overviewThresholdNs) {
      overviewThresholdNs = finalD;
      dropBelow(finalD);
    }
    data.overviewThresholdNs = overviewThresholdNs;

    List<ThreadIndex> threads = new ArrayList<>();
    for (ThreadState st : states.values()) {
      if (st.totalCalls > 0) {
        st.overview.sortByStart();
        threads.add(st.freeze());
      }
    }
    threads.sort(Comparator.comparingLong(m -> m.tid));
    data.threads = threads;

    if (data.totalCalls == 0) {
      data.minNs = 0;
      data.maxNs = 1;
    } else {
      data.minNs = minNs;
      data.maxNs = maxNs;
    }
  }

  private long varint(MappedTrace in) {
    @Var long v = 0;
    @Var int shift = 0;
    while (true) {
      if (pos >= size) {
        throw TruncatedException.INSTANCE;
      }
      int b = in.byteAt(pos++);
      v |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return v;
      }
      shift += 7;
      if (shift > 63) {
        throw new CorruptException(pos, "varint too long at offset " + pos);
      }
    }
  }

  private String string(MappedTrace in, long recStart, long len) {
    if (len < 0) {
      throw new CorruptException(
          recStart, "negative string length " + len + " at offset " + recStart);
    }
    if (len > size - pos) {
      throw TruncatedException.INSTANCE;
    }
    byte[] b = new byte[(int) len];
    in.copy(pos, b, (int) len);
    pos += len;
    return new String(b, StandardCharsets.UTF_8);
  }

  private void registerMethods(long baseId, long count, String cls, String[] sigs, int n) {
    ensureMethodNames((int) (baseId + count));
    for (int k = 0; k < n; k++) {
      int id = (int) baseId + k;
      if (data.methodNames[id] == null) {
        data.totalMethods++;
      }
      data.methodNames[id] = cls + "." + sigs[k];
    }
  }

  private void ensureMethodNames(int n) {
    if (data.methodNames.length < n) {
      data.methodNames = Arrays.copyOf(data.methodNames, Math.max(n, data.methodNames.length * 2));
    }
  }

  private String gcLabel(String s) {
    String seen = gcLabels.putIfAbsent(s, s);
    return seen != null ? seen : s;
  }

  private void ensureExceptionNames(int n) {
    if (data.exceptionNames.length < n) {
      data.exceptionNames =
          Arrays.copyOf(data.exceptionNames, Math.max(n, data.exceptionNames.length * 2));
    }
  }

  private void ensureAgg(int methodId) {
    if (data.callsByMethod.length <= methodId) {
      int cap = Math.max(methodId + 1, Math.max(1024, data.callsByMethod.length * 2));
      data.callsByMethod = Arrays.copyOf(data.callsByMethod, cap);
    }
  }

  private void corrupt(long offset, String reason) {
    if (data.corruption == null) {
      data.corruption = new TraceSnapshot.Corruption(offset, reason);
    }
  }
}
