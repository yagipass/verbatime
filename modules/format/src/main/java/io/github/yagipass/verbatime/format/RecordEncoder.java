package io.github.yagipass.verbatime.format;

import com.google.errorprone.annotations.Var;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class RecordEncoder {

  public static final int MAX_CHUNK_HEADER_BYTES = 1 + Varint.MAX_BYTES + Varint.MAX_BYTES + 5;

  private RecordEncoder() {}

  public static byte[] header(long startEpochMs, int utcOffsetSeconds) {
    ByteBuffer b = ByteBuffer.allocate(Vbtm.HEADER_BYTES);
    b.put(Vbtm.magic());
    b.put((byte) Vbtm.VERSION);
    b.put((byte) Vbtm.RECORD_ANCHOR);
    b.putLong(startEpochMs);
    b.putInt(utcOffsetSeconds);
    return b.array();
  }

  public static byte[] thread(long tid, String name) {
    return idAndName(Vbtm.RECORD_THREAD, tid, name);
  }

  public static byte[] clazz(long baseId, String className, List<String> sigs) {
    byte[] cls = utf8(className);
    byte[][] sigBytes = new byte[sigs.size()][];
    @Var
    int size =
        1
            + Varint.size(baseId)
            + Varint.size(sigBytes.length)
            + Varint.size(cls.length)
            + cls.length;
    for (int i = 0; i < sigBytes.length; i++) {
      sigBytes[i] = utf8(sigs.get(i));
      size += Varint.size(sigBytes[i].length) + sigBytes[i].length;
    }
    byte[] rec = new byte[size];
    @Var int p = 0;
    rec[p++] = Vbtm.RECORD_CLASS;
    p = Varint.put(rec, p, baseId);
    p = Varint.put(rec, p, sigBytes.length);
    p = putString(rec, p, cls);
    for (byte[] s : sigBytes) {
      p = putString(rec, p, s);
    }
    return rec;
  }

  public static byte[] exception(long id, String className) {
    return idAndName(Vbtm.RECORD_EXCEPTION, id, className);
  }

  public static byte[] gc(
      long startTicks, long durTicks, int action, String collector, String cause) {
    byte[] n = utf8(collector);
    byte[] c = utf8(cause);
    byte[] rec =
        new byte
            [1
                + Varint.size(startTicks)
                + Varint.size(durTicks)
                + Varint.size(action)
                + Varint.size(n.length)
                + n.length
                + Varint.size(c.length)
                + c.length];
    @Var int p = 0;
    rec[p++] = Vbtm.RECORD_GC;
    p = Varint.put(rec, p, startTicks);
    p = Varint.put(rec, p, durTicks);
    p = Varint.put(rec, p, action);
    p = putString(rec, p, n);
    putString(rec, p, c);
    return rec;
  }

  public static byte[] end() {
    return new byte[] {Vbtm.RECORD_END};
  }

  public static int chunkHeader(
      byte[] dst, @Var int off, long tid, long baseTicks, int payloadLen, boolean sessionEnd) {
    dst[off++] = (byte) (sessionEnd ? Vbtm.RECORD_CHUNK_END : Vbtm.RECORD_CHUNK);
    off = Varint.put(dst, off, tid);
    off = Varint.put(dst, off, baseTicks);
    return Varint.put(dst, off, payloadLen);
  }

  private static byte[] idAndName(int type, long id, String name) {
    byte[] utf = utf8(name);
    byte[] rec = new byte[1 + Varint.size(id) + Varint.size(utf.length) + utf.length];
    @Var int p = 0;
    rec[p++] = (byte) type;
    p = Varint.put(rec, p, id);
    putString(rec, p, utf);
    return rec;
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static int putString(byte[] rec, @Var int p, byte[] utf8) {
    p = Varint.put(rec, p, utf8.length);
    System.arraycopy(utf8, 0, rec, p, utf8.length);
    return p + utf8.length;
  }
}
