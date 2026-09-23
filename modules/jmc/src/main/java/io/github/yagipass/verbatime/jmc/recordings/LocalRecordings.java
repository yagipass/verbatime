package io.github.yagipass.verbatime.jmc.recordings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class LocalRecordings {

    public record Entry(Path file, String connectionDir, long size, long modifiedMs) {

        public String name() {
            return file.getFileName().toString();
        }
    }

    private LocalRecordings() {
    }

    public static Path localFile(final Path base, final String connectionDir, final String recordingName,
            final long recordingId, final String startEpochMs) {
        final String stem = recordingName.isEmpty() ? "rec" : fileSafe(recordingName);
        return base.resolve(connectionDir).resolve(stem + "-" + recordingId + "-" + startEpochMs + ".vbtm");
    }

    public static String fileSafe(final String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @SuppressWarnings("EmptyCatch")
    static List<Entry> scan(final Path base) {
        final List<Entry> out = new ArrayList<>();
        if (Files.isDirectory(base)) {
            collect(base, "", out);
            try (Stream<Path> s = Files.list(base)) {
                s.filter(Files::isDirectory).forEach(d -> collect(d, d.getFileName().toString(), out));
            } catch (final IOException ignored) {
            }
        }
        out.sort(Comparator.comparingLong(Entry::modifiedMs).reversed().thenComparing(Entry::name));
        return out;
    }

    record DeleteResult(int failed, String firstError) {
    }

    public static long prepareResume(final Path local) throws IOException {
        Files.createDirectories(local.getParent());
        return Files.exists(local) ? Files.size(local) : 0;
    }

    static List<Entry> deletable(final List<Entry> chosen, final Predicate<Path> transferring) {
        final List<Entry> out = new ArrayList<>();
        for (final Entry e : chosen) {
            if (!transferring.test(e.file())) {
                out.add(e);
            }
        }
        return out;
    }

    static DeleteResult deleteAll(final Path base, final List<Entry> targets) {
        int failed = 0;
        String firstError = null;
        for (final Entry e : targets) {
            try {
                delete(base, e);
            } catch (final IOException ex) {
                failed++;
                if (firstError == null) {
                    firstError = e.name() + ": " + ex.getMessage();
                }
            }
        }
        return new DeleteResult(failed, firstError);
    }

    static void delete(final Path base, final Entry e) throws IOException {
        final Path file = e.file().toAbsolutePath().normalize();
        final Path root = base.toAbsolutePath().normalize();
        final Path parent = file.getParent();
        final boolean underRecordingsDir = parent != null
                && (parent.equals(root) || (parent.getParent() != null && parent.getParent().equals(root)));
        if (!underRecordingsDir || !file.getFileName().toString().endsWith(".vbtm") || !Files.isRegularFile(file)) {
            throw new IOException("not a recording under " + root + ": " + file);
        }
        Files.delete(file);
    }

    @SuppressWarnings("EmptyCatch")
    private static void collect(final Path dir, final String connection, final List<Entry> out) {
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".vbtm") && Files.isRegularFile(p))
                    .forEach(p -> out.add(entry(p, connection)));
        } catch (final IOException ignored) {
        }
    }

    @SuppressWarnings("EmptyCatch")
    private static Entry entry(final Path p, final String connection) {
        long size = -1;
        long modified = 0;
        try {
            size = Files.size(p);
            modified = Files.getLastModifiedTime(p).toMillis();
        } catch (final IOException ignored) {
        }
        return new Entry(p, connection, size, modified);
    }
}
