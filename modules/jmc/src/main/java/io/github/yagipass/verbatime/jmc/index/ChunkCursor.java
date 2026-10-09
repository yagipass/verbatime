package io.github.yagipass.verbatime.jmc.index;

import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ChunkCursor {

  private final MappedTrace buf;

  private final ThreadIndex m;

  private final int last;

  private int c;

  private int len;

  private byte @Nullable [] scratch = DecodeScratch.take();

  private boolean released;

  public ChunkCursor(MappedTrace buf, ThreadIndex m, int c0, int c1) {
    buf.retain();
    this.buf = buf;
    this.m = m;
    this.c = c0 - 1;
    this.last = c1;
  }

  public boolean next() {
    if (c >= last) {
      release();
      return false;
    }
    c++;
    len = (int) m.chunks.payloadLen(c);
    if (len > 0) {
      if (Objects.requireNonNull(scratch).length < len) {
        scratch = DecodeScratch.allocate(len);
      }
      buf.copy(m.chunks.payloadOffset[c], scratch, len);
    }
    return true;
  }

  public int payloadLen() {
    return len;
  }

  public void open(EventCursor cursor) {
    cursor.reset(Objects.requireNonNull(scratch), 0, len, m.chunks.baseTicks[c]);
  }

  public void release() {
    if (released) {
      return;
    }
    released = true;
    DecodeScratch.give(scratch);
    scratch = null;
    buf.release();
  }

  int openDepthAtStart() {
    return m.chunks.openDepthAtStart[c];
  }

  boolean endsSession() {
    return m.chunks.endsSession[c];
  }
}
