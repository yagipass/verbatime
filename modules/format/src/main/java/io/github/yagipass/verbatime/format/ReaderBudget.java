package io.github.yagipass.verbatime.format;

public final class ReaderBudget {

  public static final int SESSION_BYTES = 1024;

  public static final int CHUNK_BYTES = 256;

  public static final int THREAD_BYTES = 8192;

  public static final int THREAD_NAME_BYTES = 256;

  public static final int METHOD_NAME_BYTES = 256;

  public static final int FRAME_BYTES = 256;

  public static final int CALL_BYTES = 256;

  public static final int GC_BYTES = 256;

  public static final int STRING_BYTES = 64;

  public static final int ASCII_STRING_BYTES_PER_BYTE = 2;

  public static final int DECODED_STRING_BYTES_PER_BYTE = 6;

  public static final int ESCAPED_STRING_BYTES_PER_BYTE = 20;

  private final long limitBytes;

  private long usedBytes;

  public ReaderBudget() {
    this(Runtime.getRuntime().maxMemory() / 2);
  }

  public ReaderBudget(long limitBytes) {
    if (limitBytes <= 0) {
      throw new IllegalArgumentException("reader memory budget must be positive");
    }
    this.limitBytes = limitBytes;
  }

  public ReaderBudget copy() {
    ReaderBudget copy = new ReaderBudget(limitBytes);
    copy.usedBytes = usedBytes;
    return copy;
  }

  public void reset() {
    usedBytes = 0;
  }

  public long used() {
    return usedBytes;
  }

  public void restore(long used) {
    if (used < 0 || used > usedBytes) {
      throw new IllegalArgumentException("only an earlier usage can be restored");
    }
    usedBytes = used;
  }

  public void reserve(long bytes, String resource) {
    if (bytes < 0) {
      throw new IllegalArgumentException("reservation must not be negative");
    }
    if (bytes > limitBytes - usedBytes) {
      throw new ReadLimitException(resource, limitBytes);
    }
    usedBytes += bytes;
  }
}
