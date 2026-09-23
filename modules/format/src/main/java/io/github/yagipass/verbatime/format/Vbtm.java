package io.github.yagipass.verbatime.format;

import java.nio.charset.StandardCharsets;

public final class Vbtm {

    private Vbtm() {
    }

    public static final String MAGIC = "vbtm";

    public static final int MAGIC_BYTES = 4;

    public static final int VERSION = 1;

    private static final int VERSION_BYTES = 1;

    public static final int VERSION_OFFSET = MAGIC_BYTES;

    public static final int ANCHOR_OFFSET = MAGIC_BYTES + VERSION_BYTES;

    public static final long NANOS_PER_TICK = 100;

    public static final long TICKS_PER_MS = 1_000_000L / NANOS_PER_TICK;

    public static final long MAX_TICKS = Long.MAX_VALUE / NANOS_PER_TICK;

    public static final int RECORD_THREAD = 0x01;

    public static final int RECORD_CHUNK = 0x02;

    public static final int RECORD_CHUNK_END = 0x03;

    public static final int RECORD_CLASS = 0x04;

    public static final int RECORD_END = 0x05;

    public static final int RECORD_ANCHOR = 0x06;

    public static final int RECORD_EXCEPTION = 0x07;

    public static final int RECORD_GC = 0x08;

    public static final int GC_ACTION_UNKNOWN = 0;

    public static final int GC_ACTION_MINOR = 1;

    public static final int GC_ACTION_MAJOR = 2;

    public static final int MAX_GC_LABEL_BYTES = 256;

    public static final int ANCHOR_BYTES = 1 + 8 + 4;

    public static final int HEADER_BYTES = ANCHOR_OFFSET + ANCHOR_BYTES;

    public static final int MAX_UTC_OFFSET_SECONDS = 18 * 3600;

    public static final int METHOD_ID_LIMIT = 1 << 22;

    public static final int EXCEPTION_ID_LIMIT = 1 << 22;

    public static final long MAX_CHUNK_PAYLOAD_BYTES = 1L << 28;

    public static byte[] magic() {
        return MAGIC.getBytes(StandardCharsets.US_ASCII);
    }

    public static boolean hasMagic(final byte[] b, final int off, final int len) {
        if (len < MAGIC_BYTES) {
            return false;
        }
        for (int i = 0; i < MAGIC_BYTES; i++) {
            if (b[off + i] != (byte) MAGIC.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
