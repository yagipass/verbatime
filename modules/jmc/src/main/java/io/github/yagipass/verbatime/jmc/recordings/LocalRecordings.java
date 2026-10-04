package io.github.yagipass.verbatime.jmc.recordings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.google.errorprone.annotations.Var;

public final class LocalRecordings {

    public record Entry(Path file, String connectionDir, long size, long modifiedMs) {

        public String name() {
            return file.getFileName().toString();
        }
    }

    private LocalRecordings() {
    }

    public static Path localFile(Path base, String connectionDir, String recordingName,
            long recordingId, String startEpochMs) {
        String stem = recordingName.isEmpty() ? "rec" : fileSafe(recordingName);
        return base.resolve(connectionDir).resolve(stem + "-" + recordingId + "-" + startEpochMs + ".vbtm");
    }

    public static String fileSafe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @SuppressWarnings("EmptyCatch")
    static List<Entry> scan(Path base) {
        List<Entry> out = new ArrayList<>();
        if (Files.isDirectory(base)) {
            collect(base, "", out);
            try (Stream<Path> s = Files.list(base)) {
                s.filter(Files::isDirectory).forEach(d -> collect(d, d.getFileName().toString(), out));
            } catch (IOException ignored) {
            }
        }
        out.sort(Comparator.comparingLong(Entry::modifiedMs).reversed().thenComparing(Entry::name));
        return out;
    }

    record DeleteResult(int failed, @Nullable String firstError) {
    }

    public static long prepareResume(Path local) throws IOException {
        Files.createDirectories(local.getParent());
        return Files.exists(local) ? Files.size(local) : 0;
    }

    static List<Entry> deletable(List<Entry> chosen, Predicate<Path> transferring) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : chosen) {
            if (!transferring.test(e.file())) {
                out.add(e);
            }
        }
        return out;
    }

    static DeleteResult deleteAll(Path base, List<Entry> targets) {
        @Var int failed = 0;
        @Var String firstError = null;
        for (Entry e : targets) {
            try {
                delete(base, e);
            } catch (IOException ex) {
                failed++;
                if (firstError == null) {
                    firstError = e.name() + ": " + ex.getMessage();
                }
            }
        }
        return new DeleteResult(failed, firstError);
    }

    static void delete(Path base, Entry e) throws IOException {
        Path file = e.file().toAbsolutePath().normalize();
        Path root = base.toAbsolutePath().normalize();
        Path parent = file.getParent();
        boolean underRecordingsDir = parent != null
                && (parent.equals(root) || (parent.getParent() != null && parent.getParent().equals(root)));
        if (!underRecordingsDir || !file.getFileName().toString().endsWith(".vbtm") || !Files.isRegularFile(file)) {
            throw new IOException("not a recording under " + root + ": " + file);
        }
        Files.delete(file);
    }

    @SuppressWarnings("EmptyCatch")
    private static void collect(Path dir, String connection, List<Entry> out) {
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".vbtm") && Files.isRegularFile(p))
                    .forEach(p -> out.add(entry(p, connection)));
        } catch (IOException ignored) {
        }
    }

    @SuppressWarnings("EmptyCatch")
    private static Entry entry(Path p, String connection) {
        @Var long size = -1;
        @Var long modified = 0;
        try {
            size = Files.size(p);
            modified = Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ignored) {
        }
        return new Entry(p, connection, size, modified);
    }
}
