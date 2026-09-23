package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ViewerPreviewTest {

    @TempDir
    Path dir;

    @Test
    void thePreviewPageStubsTheHostInsideTheHostSlot() throws Exception {
        final Path out = dir.resolve("preview.html");
        ViewerPreview.main(new String[] { out.toString() });
        final String html = Files.readString(out, StandardCharsets.UTF_8);
        assertFalse(html.contains("__VBTM"), "both markers are filled");
        final int slot = html.indexOf("<script id=\"vbtmhost\">");
        final int end = html.indexOf("</script>", slot);
        final String hostScript = html.substring(slot, end);
        assertTrue(hostScript.contains("window.__WIN = {"), "the canned window reply is parked in the host slot");
        assertTrue(hostScript.contains("vbtmPageWindow(__WIN)"), "the stubbed host answers with the page function");
        assertTrue(hostScript.contains("window.vbtmHostRequestWindow = function"));
    }
}
