package io.github.yagipass.verbatime.jmc.export;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;

public final class SessionExporter {

    static final int OUTLINE_LIMIT = 300;

    static final int HOT_BY_SELF_LIMIT = 40;

    static final int HOT_BY_CALLS_LIMIT = 25;

    static final int WRITE_BUFFER_BYTES = 8 << 20;

    public record Result(Path file, long bytes, long lines, long bodyLines, long calls, long listedCalls,
            long belowFloorCalls, int maxDepth, long outlineThresholdNs) {
    }

    private final TraceSnapshot data;

    private final Session session;

    private final long floorNs;

    private final Path dest;

    private final Path part;

    private final TraceIndexer.ProgressListener progress;

    private final int outlineNodes;

    private final int bufferBytes;

    private SessionExporter(final TraceSnapshot data, final Session session, final long floorNs, final Path dest,
            final TraceIndexer.ProgressListener progress, final int outlineNodes, final int bufferBytes) {
        this.data = data;
        this.session = session;
        this.floorNs = Math.max(floorNs, 0);
        this.dest = dest;
        this.part = dest.resolveSibling(dest.getFileName() + ".part");
        this.progress = progress;
        this.outlineNodes = Math.max(outlineNodes, 1);
        this.bufferBytes = bufferBytes;
    }

    public static Result export(final TraceSnapshot data, final Session session, final long floorNs, final Path dest,
            final TraceIndexer.ProgressListener progress) throws IOException {
        return export(data, session, floorNs, dest, progress, OUTLINE_LIMIT, WRITE_BUFFER_BYTES);
    }

    static Result export(final TraceSnapshot data, final Session session, final long floorNs, final Path dest,
            final TraceIndexer.ProgressListener progress, final int outlineNodes, final int bufferBytes) throws IOException {
        return new SessionExporter(data, session, floorNs, dest, progress, outlineNodes, bufferBytes).run();
    }

    public static String floorLabel(final long floorNs) {
        if (floorNs <= 0) {
            return "none";
        }
        return floorNs % 1000 == 0 ? floorNs / 1000 + " µs" : floorNs + " ns";
    }

    static String floorLabelCompact(final long floorNs) {
        return floorNs % 1000 == 0 ? floorNs / 1000 + "µs" : floorNs + "ns";
    }

    static String msText(final long ticks) {
        final long t = Math.max(ticks, 0);
        return t / Vbtm.TICKS_PER_MS + "." + String.format(Locale.ROOT, "%04d", t % Vbtm.TICKS_PER_MS);
    }

    private Result run() throws IOException {
        final ExportNames names = new ExportNames(data);
        if (session.rootMethodId >= 0) {
            names.displayName(session.rootMethodId);
        }
        final long sessionStartTicks = session.startNs / Vbtm.NANOS_PER_TICK;
        final long sessionDurTicks = session.durNs() / Vbtm.NANOS_PER_TICK;
        final int width = digits(sessionDurTicks / Vbtm.TICKS_PER_MS) + 5;
        final int excWidth = digits(Math.max(data.totalExceptions, 1));
        final long floorTicks = (floorNs + Vbtm.NANOS_PER_TICK - 1) / Vbtm.NANOS_PER_TICK;
        final byte[] floorLabelBytes = floorLabelCompact(floorNs).getBytes(StandardCharsets.UTF_8);
        final OutlineHeap top = new OutlineHeap(outlineNodes);

        PatchableFileWriter writer = null;
        boolean destOpened = false;
        boolean done = false;
        try {
            writer = new PatchableFileWriter(part, bufferBytes);
            final BodyLines body = new BodyLines(writer, names, sessionStartTicks, width, excWidth, floorLabelBytes);
            final BodyPass pass = new BodyPass(data, session, floorTicks, progress, body, top);
            pass.run();
            final long bodyLines = writer.lines();
            writer.close();
            writer = null;
            destOpened = true;
            final ExportHead report = new ExportHead(data, session, floorNs, names, pass, top, width,
                    sessionStartTicks, sessionDurTicks);
            final byte[] head = report.build(bodyLines);
            final long bytes = concatenate(head);
            done = true;
            return new Result(dest, bytes, report.bodyEnd(), bodyLines, pass.totalCalls(), pass.listedCalls(),
                    pass.totalCalls() - pass.listedCalls(), pass.maxDepth(), top.thresholdTicks() * Vbtm.NANOS_PER_TICK);
        } finally {
            if (writer != null) {
                closeQuietly(writer);
            }
            deleteQuietly(part);
            if (!done && destOpened) {
                deleteQuietly(dest);
            }
        }
    }

    private long concatenate(final byte[] head) throws IOException {
        try (FileChannel out = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE); FileChannel in = FileChannel.open(part, StandardOpenOption.READ)) {
            final ByteBuffer hb = ByteBuffer.wrap(head);
            while (hb.hasRemaining()) {
                out.write(hb);
            }
            final long size = in.size();
            long pos = 0;
            while (pos < size) {
                final long n = in.transferTo(pos, size - pos, out);
                if (n <= 0) {
                    copyRest(in, out, pos, size);
                    break;
                }
                pos += n;
            }
            return out.size();
        }
    }

    private static void copyRest(final FileChannel in, final FileChannel out, long pos, final long size)
            throws IOException {
        final ByteBuffer bb = ByteBuffer.allocate(1 << 20);
        while (pos < size) {
            bb.clear();
            final int n = in.read(bb, pos);
            if (n <= 0) {
                throw new IOException("short read at " + pos + " of " + size);
            }
            bb.flip();
            while (bb.hasRemaining()) {
                out.write(bb);
            }
            pos += n;
        }
    }

    private static int digits(final long v) {
        return Long.toString(Math.max(v, 0)).length();
    }

    @SuppressWarnings("EmptyCatch")
    private static void deleteQuietly(final Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (final IOException e) {
        }
    }

    @SuppressWarnings("EmptyCatch")
    private static void closeQuietly(final PatchableFileWriter w) {
        try {
            w.close();
        } catch (final IOException e) {
        }
    }
}
