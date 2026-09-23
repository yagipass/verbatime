package io.github.yagipass.verbatime.jmc.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.jmc.recordings.LocalRecordings.Entry;

final class ControlTextsTest {

    @Test
    void deletePromptNamesEveryFileSoTheUserCanCheckBeforeConfirming() {
        final List<Entry> three = List.of(rec("a.vbtm", 1 << 20), rec("b.vbtm", 2 << 20), rec("c.vbtm", -1));
        assertEquals("Delete 3 recordings, 3.0 MB in total?\n\na.vbtm\nb.vbtm\nc.vbtm\n\nThis cannot be undone.",
                ControlTexts.deletePrompt(three, 0), "unknown sizes do not poison the total");

        final Entry one = rec("only.vbtm", 512);
        assertEquals("Delete only.vbtm, 512 B?\n" + one.file() + "\n\nThis cannot be undone.",
                ControlTexts.deletePrompt(List.of(one), 0), "a single row keeps the full path");
    }

    @Test
    void deletePromptCapsTheListAndSaysHowManyAreSkipped() {
        final List<Entry> twelve = IntStream.range(0, 12).mapToObj(i -> rec("r" + i + ".vbtm", 1)).toList();
        final String text = ControlTexts.deletePrompt(twelve, 1);
        assertTrue(text.startsWith("Delete 12 recordings, 12 B in total?\n"), text);
        assertTrue(text.contains("\nr9.vbtm\n\u2026 and 2 more\n"), text);
        assertFalse(text.contains("r10.vbtm"), "names past the cap collapse into the count");
        assertTrue(text.contains("\n\n1 still being transferred will be skipped.\n\nThis cannot be undone."), text);
    }

    @Test
    void recordingTextShowsIdElapsedPulledAndLag() {
        assertEquals("Recording #7, 1:05 elapsed, 1.0 MB transferred, 1.0 MB behind, about 1 s",
                ControlTexts.recordingText(7, 65_000, 1 << 20, 2 << 20, 1 << 20));
        assertEquals("Recording #?, 0:00 elapsed, 0 B transferred",
                ControlTexts.recordingText(0, 0, 0, -1, 0), "an agent that reports no id or size gets no lag clause");
    }

    @Test
    void transferringTextSaysOfTotalOrPulledWhenTotalIsUnknown() {
        assertEquals("Transferring #5: 512 B of 2.0 MB", ControlTexts.transferringText(5, 512, 2 << 20));
        assertEquals("Transferring #5: 512 B transferred", ControlTexts.transferringText(5, 512, -1));
    }

    @Test
    void idleTextHintsWhenThereAreNoRoots() {
        assertEquals("Idle", ControlTexts.idleText(true));
        assertEquals("Idle. Add a root to start recording", ControlTexts.idleText(false));
    }

    @Test
    void countTextIsBlankUntilBothCountsAreKnown() {
        assertEquals("12 classes, 340 methods instrumented", ControlTexts.countText("12", "340"));
        assertEquals("", ControlTexts.countText("12", null), "an older agent that reports only one count shows nothing");
        assertEquals("", ControlTexts.countText(null, null));
    }

    private static Entry rec(final String name, final long size) {
        return new Entry(Path.of("/tmp/recordings/localhost_9010", name), "localhost_9010", size, 0);
    }
}
