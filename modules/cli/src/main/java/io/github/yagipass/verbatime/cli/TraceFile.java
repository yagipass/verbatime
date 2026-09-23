package io.github.yagipass.verbatime.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.format.Vbtm;

final class TraceFile {

    enum Status {
        COMPLETE, TRUNCATED, CORRUPT
    }

    record Chunk(long offset, int len, long baseTicks) {
    }

    static final class Session {

        final int number;

        final long tid;

        final List<Chunk> chunks = new ArrayList<>();

        boolean ended;

        Session(final int number, final long tid) {
            this.number = number;
            this.tid = tid;
        }
    }

    record GcPause(long startTicks, long durTicks, int action, String collector, String cause) {

        long endTicks() {
            return startTicks + durTicks;
        }

        String kind() {
            return action == Vbtm.GC_ACTION_MINOR ? "minor" : action == Vbtm.GC_ACTION_MAJOR ? "major" : "pause";
        }
    }

    final Path path;

    final MappedTrace data;

    final List<GcPause> gcPauses = new ArrayList<>();

    final List<Session> sessions = new ArrayList<>();

    long startEpochMs;

    int utcOffsetSeconds;

    int methodCount;

    Status status = Status.COMPLETE;

    long corruptOffset = -1;

    String corruptReason;

    private final Map<Long, String> threads = new HashMap<>();

    private String[] classNames = new String[256];

    private String[] sigs = new String[256];

    private String[] exceptions = new String[16];

    private long pos;

    private TraceFile(final Path path, final MappedTrace data) {
        this.path = path;
        this.data = data;
    }

    static TraceFile open(final String arg) {
        try {
            return open(Path.of(arg));
        } catch (final InvalidPathException e) {
            throw new CliException(CliException.UNREADABLE, "not a valid path: " + arg, null);
        }
    }

    static TraceFile open(final Path path) {
        if (!Files.isRegularFile(path)) {
            throw new CliException(CliException.UNREADABLE, "no such file: " + path,
                    "pass the path of a .vbtm recording");
        }
        final MappedTrace data;
        try {
            data = MappedTrace.open(path);
        } catch (final NoSuchFileException e) {
            throw new CliException(CliException.UNREADABLE, "no such file: " + path,
                    "pass the path of a .vbtm recording");
        } catch (final IOException e) {
            throw new CliException(CliException.UNREADABLE, "cannot read " + path + ": " + e.getMessage(), null);
        }
        final TraceFile f = new TraceFile(path, data);
        f.scan();
        return f;
    }

    String fileName() {
        return Names.sanitize(path.getFileName().toString());
    }

    Session session(final String ref) {
        final String s = ref.startsWith("#") ? ref.substring(1) : ref;
        try {
            return session(Integer.parseInt(s));
        } catch (final NumberFormatException e) {
            throw CliException.usage("'" + ref + "' is not a session id",
                    "session ids are the numbers in the id column of vbtm sessions");
        }
    }

    Session session(final int number) {
        if (number < 1 || number > sessions.size()) {
            throw CliException.usage("no session " + number + ", the recording has " + sessions.size(),
                    "vbtm sessions " + Args.shellQuote(path.toString()));
        }
        return sessions.get(number - 1);
    }

    String methodClass(final int id) {
        return id >= 0 && id < classNames.length ? classNames[id] : null;
    }

    String methodSig(final int id) {
        return id >= 0 && id < sigs.length ? sigs[id] : null;
    }

    String exceptionName(final int id) {
        return id > 0 && id < exceptions.length && exceptions[id] != null ? exceptions[id] : "unknown";
    }

    String threadName(final long tid) {
        final String n = threads.get(tid);
        return n != null ? n : "tid " + tid;
    }

    OffsetDateTime wallClock(final long ticks) {
        return Instant.ofEpochMilli(startEpochMs).plusNanos(ticks * Vbtm.NANOS_PER_TICK)
                .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds));
    }

    void markCorrupt(final long offset, final String reason) {
        if (status != Status.CORRUPT || offset < corruptOffset) {
            status = Status.CORRUPT;
            corruptOffset = offset;
            corruptReason = reason;
        }
    }

    String statusText() {
        return switch (status) {
            case COMPLETE -> "complete";
            case TRUNCATED -> "truncated";
            case CORRUPT -> "corrupt at offset " + corruptOffset + ": " + corruptReason;
        };
    }

    int exitCode() {
        return status == Status.CORRUPT ? CliException.CORRUPT : 0;
    }

    private void scan() {
        final long size = data.size();
        if (size < Vbtm.MAGIC_BYTES || !hasMagic()) {
            throw new CliException(CliException.UNREADABLE,
                    path + " is not a .vbtm recording, its first bytes are not the vbtm magic", null);
        }
        if (size <= Vbtm.VERSION_OFFSET) {
            status = Status.TRUNCATED;
            return;
        }
        final int version = data.byteAt(Vbtm.VERSION_OFFSET);
        if (version != Vbtm.VERSION) {
            throw new CliException(CliException.UNREADABLE,
                    path + " is format version " + version + ", this reader reads version " + Vbtm.VERSION, null);
        }
        if (size <= Vbtm.ANCHOR_OFFSET) {
            status = Status.TRUNCATED;
            return;
        }
        if (data.byteAt(Vbtm.ANCHOR_OFFSET) != Vbtm.RECORD_ANCHOR) {
            markCorrupt(Vbtm.ANCHOR_OFFSET, "the anchor record must follow the version byte");
            return;
        }
        if (size < Vbtm.HEADER_BYTES) {
            status = Status.TRUNCATED;
            return;
        }
        pos = Vbtm.ANCHOR_OFFSET + 1;
        startEpochMs = fixed(8);
        utcOffsetSeconds = (int) fixed(4);
        if (utcOffsetSeconds < -Vbtm.MAX_UTC_OFFSET_SECONDS || utcOffsetSeconds > Vbtm.MAX_UTC_OFFSET_SECONDS) {
            markCorrupt(Vbtm.ANCHOR_OFFSET, "UTC offset " + utcOffsetSeconds + " s out of range");
            utcOffsetSeconds = 0;
            return;
        }
        try {
            readRecords(size);
        } catch (final Truncated t) {
            status = Status.TRUNCATED;
        } catch (final BadRecord e) {
            markCorrupt(e.offset, e.getMessage());
        }
    }

    private void readRecords(final long size) {
        final Map<Long, Session> open = new HashMap<>();
        boolean endSeen = false;
        while (pos < size) {
            final long recordStart = pos;
            if (endSeen) {
                markCorrupt(recordStart, (size - recordStart) + " bytes after the END record");
                return;
            }
            final int type = data.byteAt(pos++);
            switch (type) {
                case Vbtm.RECORD_THREAD -> {
                    final long tid = varint();
                    threads.put(tid, string(recordStart, varint()));
                }
                case Vbtm.RECORD_CHUNK, Vbtm.RECORD_CHUNK_END -> {
                    if (!readChunk(recordStart, type == Vbtm.RECORD_CHUNK_END, open, size)) {
                        return;
                    }
                }
                case Vbtm.RECORD_CLASS -> {
                    final long baseId = varint();
                    final long count = varint();
                    if (baseId < 0 || count < 0 || baseId > Vbtm.METHOD_ID_LIMIT
                            || count > Vbtm.METHOD_ID_LIMIT - baseId) {
                        markCorrupt(recordStart, "method ids exceed the 2^22 format limit");
                        return;
                    }
                    final String cls = string(recordStart, varint());
                    if (count > size - pos) {
                        throw new Truncated();
                    }
                    final String[] s = new String[(int) count];
                    for (int k = 0; k < s.length; k++) {
                        s[k] = string(recordStart, varint());
                    }
                    declare((int) baseId, cls, s);
                }
                case Vbtm.RECORD_EXCEPTION -> {
                    final long id = varint();
                    if (id <= 0 || id >= Vbtm.EXCEPTION_ID_LIMIT) {
                        markCorrupt(recordStart, "exception id " + id + " outside 1.." + (Vbtm.EXCEPTION_ID_LIMIT - 1));
                        return;
                    }
                    final String name = string(recordStart, varint());
                    if (id >= exceptions.length) {
                        exceptions = Arrays.copyOf(exceptions, (int) Math.max(id + 1, exceptions.length * 2L));
                    }
                    exceptions[(int) id] = name;
                }
                case Vbtm.RECORD_GC -> {
                    final long start = varint();
                    final long dur = varint();
                    final long action = varint();
                    if (start < 0 || start > Vbtm.MAX_TICKS || dur < 0 || dur > Vbtm.MAX_TICKS - start) {
                        markCorrupt(recordStart, "GC pause ticks out of range");
                        return;
                    }
                    if (action < 0 || action > Vbtm.GC_ACTION_MAJOR) {
                        markCorrupt(recordStart, "unknown GC action " + action);
                        return;
                    }
                    final String collector = gcLabel(recordStart);
                    final String cause = gcLabel(recordStart);
                    gcPauses.add(new GcPause(start, dur, (int) action, collector, cause));
                }
                case Vbtm.RECORD_END -> endSeen = true;
                default -> {
                    markCorrupt(recordStart, "unknown record type " + type);
                    return;
                }
            }
        }
        if (!endSeen) {
            status = Status.TRUNCATED;
        }
    }

    private boolean readChunk(final long recordStart, final boolean endsSession, final Map<Long, Session> open,
            final long size) {
        final long tid = varint();
        final long baseTicks = varint();
        final long len = varint();
        if (baseTicks < 0 || baseTicks > Vbtm.MAX_TICKS) {
            markCorrupt(recordStart, "chunk base ticks out of range");
            return false;
        }
        if (len < 0 || len > Vbtm.MAX_CHUNK_PAYLOAD_BYTES) {
            markCorrupt(recordStart, "implausible chunk payload length " + len);
            return false;
        }
        Session s = open.get(tid);
        if (s == null) {
            s = new Session(sessions.size() + 1, tid);
            sessions.add(s);
            open.put(tid, s);
        }
        final long end = pos + len;
        if (end > size) {
            s.chunks.add(new Chunk(pos, (int) (size - pos), baseTicks));
            status = Status.TRUNCATED;
            return false;
        }
        s.chunks.add(new Chunk(pos, (int) len, baseTicks));
        pos = end;
        if (endsSession) {
            s.ended = true;
            open.remove(tid);
        }
        return true;
    }

    private boolean hasMagic() {
        final byte[] m = Vbtm.magic();
        for (int i = 0; i < m.length; i++) {
            if (data.byteAt(i) != (m[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    private void declare(final int baseId, final String cls, final String[] names) {
        final int end = baseId + names.length;
        if (end > classNames.length) {
            final int n = Math.max(end, classNames.length * 2);
            classNames = Arrays.copyOf(classNames, n);
            sigs = Arrays.copyOf(sigs, n);
        }
        for (int k = 0; k < names.length; k++) {
            classNames[baseId + k] = cls;
            sigs[baseId + k] = names[k];
        }
        methodCount = Math.max(methodCount, end);
    }

    private long fixed(final int bytes) {
        long v = 0;
        for (int i = 0; i < bytes; i++) {
            v = (v << 8) | data.byteAt(pos++);
        }
        return bytes == 4 ? (int) v : v;
    }

    private String gcLabel(final long recordStart) {
        final long len = varint();
        if (len > Vbtm.MAX_GC_LABEL_BYTES) {
            throw new BadRecord(recordStart, "GC label of " + len + " bytes");
        }
        return string(recordStart, len);
    }

    private long varint() {
        long r = 0;
        int shift = 0;
        while (true) {
            if (pos >= data.size()) {
                throw new Truncated();
            }
            final int b = data.byteAt(pos++);
            r |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return r;
            }
            shift += 7;
            if (shift > 63) {
                throw new BadRecord(pos, "varint too long");
            }
        }
    }

    private String string(final long recordStart, final long len) {
        if (len < 0 || len > Integer.MAX_VALUE) {
            throw new BadRecord(recordStart, "implausible string length " + len);
        }
        if (len > data.size() - pos) {
            throw new Truncated();
        }
        final byte[] b = new byte[(int) len];
        data.copy(pos, b, b.length);
        pos += len;
        return Names.sanitize(new String(b, StandardCharsets.UTF_8));
    }

    private static final class Truncated extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Truncated() {
            super(null, null, false, false);
        }
    }

    private static final class BadRecord extends RuntimeException {

        private static final long serialVersionUID = 1L;

        final long offset;

        BadRecord(final long offset, final String message) {
            super(message, null, false, false);
            this.offset = offset;
        }
    }
}
