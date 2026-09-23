package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

final class ViewerHtmlTest {

    @Test
    void renderFillsBothMarkersAndLeavesNoneBehind() throws IOException {
        final String html = ViewerHtml.render("a __VBTMINIT__ b __VBTMHOST__ c", "{\"x\":1}", "window.y = 2;");
        assertEquals("a {\"x\":1} b window.y = 2; c", html);
        assertThrows(IOException.class, () -> ViewerHtml.render("no markers here", "{}", ""),
                "a template without the markers would silently ship a page with no data");
    }

    @Test
    void theTemplateHasEachMarkerOnceWithTheHostSlotBeforeTheMainScript() throws IOException {
        final String t = ViewerHtml.template();
        assertEquals(t.indexOf(ViewerHtml.INIT_MARKER), t.lastIndexOf(ViewerHtml.INIT_MARKER));
        assertEquals(t.indexOf(ViewerHtml.HOST_MARKER), t.lastIndexOf(ViewerHtml.HOST_MARKER));
        final int host = t.indexOf(ViewerHtml.HOST_MARKER);
        final int main = t.indexOf("\"use strict\"");
        assertTrue(host > 0 && host < main,
                "host functions must be defined before the page script checks for them with typeof");
        final String rendered = ViewerHtml.render(t, "{}", "");
        assertFalse(rendered.contains("__VBTM"), "the page ships without any marker");
    }

    @Test
    void thePageDefinesEveryPageFunctionAndCallsEveryHostFunctionByTheBridgeNames() throws IOException {
        final String t = ViewerHtml.template();
        for (final String fn : ViewerBridge.PAGE_FUNCTIONS) {
            assertTrue(t.contains("window." + fn + " = function"), fn + " must be defined by the page");
        }
        for (final String fn : ViewerBridge.HOST_FUNCTIONS) {
            assertTrue(t.contains("window." + fn + "("), fn + " must be called by the page");
        }
    }
}
