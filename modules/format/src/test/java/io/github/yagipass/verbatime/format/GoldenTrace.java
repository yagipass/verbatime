package io.github.yagipass.verbatime.format;

final class GoldenTrace {

    static final long EPOCH_MS = 1_700_000_000_000L;

    static final int UTC_OFFSET = 9 * 3600;

    static final int[] PAYLOAD = { 0x00, 0x00, 0x0A, 0x01, 0x0D, 0x0B, 0x01 };

    static final int[] GOLDEN = {
            'v', 'b', 't', 'm', 0x01,
            0x06, 0x00, 0x00, 0x01, 0x8B, 0xCF, 0xE5, 0x68, 0x00, 0x00, 0x00, 0x7E, 0x90,
            0x01, 0x07, 0x04, 'm', 'a', 'i', 'n',
            0x04, 0x00, 0x02, 0x03, 'a', '.', 'B', 0x04, 'm', '(', ')', 'V', 0x05, 'n', '(', 'I', ')', 'V',
            0x07, 0x01, 0x03, 'x', '.', 'E',
            0x08, 0xAC, 0x02, 0x14, 0x01, 0x02, 'G', '1', 0x05, 'A', 'l', 'l', 'o', 'c',
            0x02, 0x07, 0x64, 0x07, 0x00, 0x00, 0x0A, 0x01, 0x0D, 0x0B, 0x01,
            0x03, 0x07, 0xC8, 0x01, 0x00,
            0x05 };

    private static final int EMPTY_END_CHUNK_BYTES = 5;

    private static final int END_RECORD_BYTES = 1;

    static final int CHUNK_PAYLOAD_OFFSET = GOLDEN.length - END_RECORD_BYTES - EMPTY_END_CHUNK_BYTES - PAYLOAD.length;

    private GoldenTrace() {
    }

    static byte[] bytes(final int... v) {
        final byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) {
            b[i] = (byte) v[i];
        }
        return b;
    }

    static byte[] golden() {
        return bytes(GOLDEN);
    }
}
