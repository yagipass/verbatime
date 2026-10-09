package io.github.yagipass.verbatime.jmc.export;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class BodyLines {

  private static final byte[] SELF = " self ".getBytes(StandardCharsets.UTF_8);

  private static final byte[] FLAG_THROWN = " !".getBytes(StandardCharsets.UTF_8);

  private static final byte[] FLAG_UNCLOSED = " ~".getBytes(StandardCharsets.UTF_8);

  private static final int FLAG_EXC_PREFIX = 3;

  private static final byte[] DOT = "·".getBytes(StandardCharsets.UTF_8);

  private static final byte[] CALLS_BELOW = " calls <".getBytes(StandardCharsets.UTF_8);

  private static final byte[] NESTED_OPEN = ", ".getBytes(StandardCharsets.UTF_8);

  private static final byte[] NESTED_CLOSE = " incl. nested".getBytes(StandardCharsets.UTF_8);

  private static final byte[] THROWN_OPEN = " [!".getBytes(StandardCharsets.UTF_8);

  private static final byte[] THROWN_CLOSE = "]".getBytes(StandardCharsets.UTF_8);

  private static final byte[] COLON = ": ".getBytes(StandardCharsets.UTF_8);

  private static final byte[] COMMA = ", ".getBytes(StandardCharsets.UTF_8);

  private static final byte[] TIMES = "×".getBytes(StandardCharsets.UTF_8);

  private final PatchableFileWriter writer;

  private final ExportNames names;

  private final long sessionStartTicks;

  private final int width;

  private final int excWidth;

  private final byte[] floorLabelBytes;

  private final byte[] flagNone;

  private final byte[] flagScratch;

  private final long[] sortScratch = new long[64];

  BodyLines(
      PatchableFileWriter writer,
      ExportNames names,
      long sessionStartTicks,
      int width,
      int excWidth,
      byte[] floorLabelBytes) {
    this.writer = writer;
    this.names = names;
    this.sessionStartTicks = sessionStartTicks;
    this.width = width;
    this.excWidth = excWidth;
    this.floorLabelBytes = floorLabelBytes;
    this.flagNone = new byte[FLAG_EXC_PREFIX + excWidth];
    Arrays.fill(flagNone, (byte) ' ');
    this.flagScratch = new byte[flagNone.length];
  }

  long lines() {
    return writer.lines();
  }

  long nextLine() {
    return writer.lines() + 1;
  }

  void writePlaceholder(OpenCalls st, int k) throws IOException {
    st.lineNo[k] = writer.lines() + 1;
    writer.writeTicksAsMs(st.startTicks[k] - sessionStartTicks);
    writer.put(' ');
    st.durPatchOffset[k] = writer.position();
    writer.placeholder(width);
    writer.put(' ');
    writer.num(k);
    writer.put(' ');
    writer.bytes(names.utf8(st.methodId[k]));
    writer.bytes(SELF);
    st.selfPatchOffset[k] = writer.position();
    writer.placeholder(width);
    writer.bytes(flagNone);
    writer.newline();
  }

  void patchPlaceholder(
      OpenCalls st, int k, long dur, long self, boolean thrown, int excNo, boolean unclosed)
      throws IOException {
    writer.patchTicksAsMs(st.durPatchOffset[k], dur, width);
    writer.patchTicksAsMs(st.selfPatchOffset[k], self, width);
    if (thrown) {
      patchFlag(st.selfPatchOffset[k] + width, excNo);
    } else if (unclosed) {
      writer.patch(st.selfPatchOffset[k] + width, FLAG_UNCLOSED);
    }
  }

  void writeLine(
      OpenCalls st, int k, long dur, long self, boolean thrown, int excNo, boolean unclosed)
      throws IOException {
    writer.writeTicksAsMs(st.startTicks[k] - sessionStartTicks);
    writer.put(' ');
    writer.writeTicksAsMs(dur);
    writer.put(' ');
    writer.num(k);
    writer.put(' ');
    writer.bytes(names.utf8(st.methodId[k]));
    if (st.directChildren[k] > 0) {
      writer.bytes(SELF);
      writer.writeTicksAsMs(self);
    }
    if (thrown) {
      writer.bytes(FLAG_THROWN);
      if (excNo > 0) {
        writer.put('e');
        writer.num(excNo);
      }
    }
    if (unclosed) {
      writer.bytes(FLAG_UNCLOSED);
    }
    writer.newline();
  }

  void writeBelowFloorLine(OpenCalls st, int k) throws IOException {
    writer.writeTicksAsMs(st.startTicks[k] - sessionStartTicks);
    writer.put(' ');
    writer.writeTicksAsMs(st.belowFloorTicks[k]);
    writer.put(' ');
    writer.num(k + 1);
    writer.put(' ');
    writer.bytes(DOT);
    writer.num(st.belowFloorCalls[k]);
    writer.bytes(CALLS_BELOW);
    writer.bytes(floorLabelBytes);
    writer.bytes(NESTED_OPEN);
    writer.num(st.belowFloorDescendants[k]);
    writer.bytes(NESTED_CLOSE);
    if (st.belowFloorThrown[k] > 0) {
      writer.bytes(THROWN_OPEN);
      writer.num(st.belowFloorThrown[k]);
      writer.bytes(THROWN_CLOSE);
    }
    writer.bytes(COLON);
    BelowFloorCounts t = st.belowFloorAt(k);
    int n = t.size();
    long[] keys = sortScratch.length >= n ? sortScratch : new long[n];
    for (int i = 0; i < n; i++) {
      keys[i] = ((long) (Integer.MAX_VALUE - t.count(i)) << 32) | i;
    }
    Arrays.sort(keys, 0, n);
    for (int i = 0; i < n; i++) {
      int e = (int) keys[i];
      if (i > 0) {
        writer.bytes(COMMA);
      }
      writer.bytes(names.utf8(t.methodId(e)));
      if (t.count(e) > 1) {
        writer.bytes(TIMES);
        writer.num(t.count(e));
      }
    }
    writer.newline();
  }

  private static int digits(long v) {
    return Long.toString(Math.max(v, 0)).length();
  }

  private void patchFlag(long off, int excNo) throws IOException {
    Arrays.fill(flagScratch, (byte) ' ');
    flagScratch[1] = '!';
    if (excNo > 0 && digits(excNo) <= excWidth) {
      flagScratch[2] = 'e';
      @Var int v = excNo;
      for (int i = FLAG_EXC_PREFIX + digits(excNo) - 1; v > 0; i--) {
        flagScratch[i] = (byte) ('0' + v % 10);
        v /= 10;
      }
    }
    writer.patch(off, flagScratch);
  }
}
