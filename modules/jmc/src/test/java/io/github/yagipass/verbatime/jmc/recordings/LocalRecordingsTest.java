package io.github.yagipass.verbatime.jmc.recordings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yagipass.verbatime.jmc.recordings.LocalRecordings.Entry;

final class LocalRecordingsTest {

    @TempDir
    Path base;

    @Test
    void historyRowsSayWhichConnectionARecordingWasPulledFrom() throws IOException {

        final Path loose = touch(base.resolve("loose.vbtm"), 1_000, 3);
        final Path pulled = touch(base.resolve("localhost_9010").resolve("rec-1-2.vbtm"), 2_000, 5);
        touch(base.resolve("localhost_9010").resolve("deeper").resolve("nested.vbtm"), 3_000, 1);
        touch(base.resolve("notes.txt"), 4_000, 1);

        final List<Entry> l = LocalRecordings.scan(base);
        assertEquals(List.of(pulled, loose), l.stream().map(Entry::file).toList(),
                "newest first, and only .vbtm at depth 0 and 1 are history");
        assertEquals("localhost_9010", l.get(0).connectionDir());
        assertEquals("", l.get(1).connectionDir());
        assertEquals("rec-1-2.vbtm", l.get(0).name());
        assertEquals(5, l.get(0).size());
        assertEquals(2_000, l.get(0).modifiedMs());
    }

    @Test
    void sameTimestampFallsBackToNameSoTheOrderIsStable() throws IOException {
        touch(base.resolve("b.vbtm"), 1_000, 1);
        touch(base.resolve("a.vbtm"), 1_000, 1);
        assertEquals(List.of("a.vbtm", "b.vbtm"), LocalRecordings.scan(base).stream().map(Entry::name).toList());
    }

    @Test
    void missingDirectoryIsAnEmptyHistoryNotAnError() {
        assertTrue(LocalRecordings.scan(base.resolve("not-created-yet")).isEmpty());
    }

    @Test
    void deletingARowRemovesThatRecordingFromTheHistoryAndNothingElse() throws IOException {
        final Path keep = touch(base.resolve("localhost_9010").resolve("rec-1-1.vbtm"), 1_000, 1);
        final Path gone = touch(base.resolve("localhost_9010").resolve("rec-1-2.vbtm"), 2_000, 1);

        final List<Entry> before = LocalRecordings.scan(base);
        LocalRecordings.delete(base, before.get(0));

        assertFalse(Files.exists(gone));
        assertTrue(Files.exists(keep));
        assertEquals(List.of(keep), LocalRecordings.scan(base).stream().map(Entry::file).toList());
    }

    @Test
    void deleteRefusesAnythingTheHistoryWouldNotList() throws IOException {
        final Path outside = touch(base.getParent().resolve("elsewhere.vbtm"), 1_000, 1);
        final Path notes = touch(base.resolve("notes.txt"), 1_000, 1);
        final Path nested = touch(base.resolve("h").resolve("deeper").resolve("nested.vbtm"), 1_000, 1);

        for (final Path p : List.of(outside, notes, nested)) {
            assertThrows(IOException.class, () -> LocalRecordings.delete(base, new Entry(p, "", 1, 1_000)), p.toString());
            assertTrue(Files.exists(p), p + " must survive");
        }
    }

    @Test
    void pullTargetEncodesConnectionNameIdAndStartAndSanitisesTheName() {
        assertEquals(base.resolve("localhost_9010").resolve("my_run-3-170000.vbtm"),
                LocalRecordings.localFile(base, "localhost_9010", "my run", 3, "170000"),
                "connection directory, then name-id-start so files from the same agent sort by recording");
        assertEquals(base.resolve("localhost_9010").resolve("rec-3-170000.vbtm"),
                LocalRecordings.localFile(base, "localhost_9010", "", 3, "170000"), "an unnamed recording is rec");
        assertEquals("a_b_c", LocalRecordings.fileSafe("a b:c"));
    }

    @Test
    void resumeOffsetCreatesTheDirectoryAndReportsTheExistingSize() throws IOException {
        final Path fresh = base.resolve("localhost_9010").resolve("rec-1-1.vbtm");
        assertEquals(0, LocalRecordings.prepareResume(fresh), "nothing on disk yet: the transfer starts at byte 0");
        assertTrue(Files.isDirectory(fresh.getParent()), "the connection directory is ready for the first chunk");

        touch(fresh, 1_000, 7);
        assertEquals(7, LocalRecordings.prepareResume(fresh), "a resumed transfer continues exactly after the bytes already saved");
    }

    @Test
    void aRecordingStillBeingPulledIsNeverDeleted() throws IOException {
        final Path pulling = touch(base.resolve("localhost_9010").resolve("rec-1-1.vbtm"), 1_000, 1);
        final Path done = touch(base.resolve("localhost_9010").resolve("rec-1-2.vbtm"), 2_000, 1);
        final List<Entry> both = LocalRecordings.scan(base);

        final List<Entry> targets = LocalRecordings.deletable(both, p -> p.equals(pulling));
        assertEquals(List.of(done), targets.stream().map(Entry::file).toList());

        final LocalRecordings.DeleteResult r = LocalRecordings.deleteAll(base, targets);
        assertEquals(0, r.failed());
        assertNull(r.firstError());
        assertTrue(Files.exists(pulling), "the file the agent is still writing to survives");
        assertFalse(Files.exists(done));
    }

    @Test
    void deleteAllKeepsGoingAfterAFailureAndReportsTheFirstOne() throws IOException {
        final Path outside = touch(base.getParent().resolve("elsewhere.vbtm"), 1_000, 1);
        final Path inside = touch(base.resolve("h").resolve("ok.vbtm"), 1_000, 1);
        final LocalRecordings.DeleteResult r = LocalRecordings.deleteAll(base,
                List.of(new Entry(outside, "", 1, 1_000), new Entry(inside, "h", 1, 1_000)));
        assertEquals(1, r.failed());
        assertTrue(r.firstError().startsWith("elsewhere.vbtm: "), r.firstError());
        assertFalse(Files.exists(inside), "one refusal does not stop the others");
    }

    private static Path touch(final Path p, final long mtimeMs, final int bytes) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[bytes]);
        Files.setLastModifiedTime(p, FileTime.fromMillis(mtimeMs));
        return p;
    }
}
