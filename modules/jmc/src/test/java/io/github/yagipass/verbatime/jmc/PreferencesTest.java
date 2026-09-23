package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class PreferencesTest {

    @Test
    void blankPreferenceMeansTheDefaultNotTheWorkingDirectory() {

        final Path dflt = Path.of("/ws/verbatime/recordings");
        assertEquals(dflt, Preferences.resolveRecordingsDir("", dflt));
        assertEquals(dflt, Preferences.resolveRecordingsDir("   ", dflt));
        assertEquals(dflt, Preferences.resolveRecordingsDir(null, dflt));
        assertEquals(Path.of("/elsewhere"), Preferences.resolveRecordingsDir(" /elsewhere ", dflt));
    }

    @Test
    void defaultDirectorySitsInTheWorkspaceLikeJmcsPersistedJmxData() {
        assertEquals(Path.of("/Users/x/.jmc/9.1.2/verbatime/recordings"),
                Preferences.recordingsDirIn(Path.of("/Users/x/.jmc/9.1.2")));
    }
}
