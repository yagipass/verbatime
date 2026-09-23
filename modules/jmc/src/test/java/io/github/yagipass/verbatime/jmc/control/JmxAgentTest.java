package io.github.yagipass.verbatime.jmc.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

final class JmxAgentTest {

    @Test
    void splitsAtFirstEqualsOnly() {
        final Map<String, String> m = JmxAgent
                .parseStatus(new String[] { "root.0=ok com.example.web.RequestHandler::handle",
                        "recording.file=rec-3-20260828-120000.vbtm", "future.key=a=b=c", });
        assertEquals("ok com.example.web.RequestHandler::handle", m.get("root.0"));
        assertEquals("rec-3-20260828-120000.vbtm", m.get("recording.file"));
        assertEquals("a=b=c", m.get("future.key"));
    }

    @Test
    void preservesAgentLineOrder() {
        final Map<String, String> m = JmxAgent.parseStatus(new String[] { "v=1", "pid=7", "state=idle" });
        assertEquals(List.of("v", "pid", "state"), List.copyOf(m.keySet()));
    }

    @Test
    void skipsMalformedLines() {
        final Map<String, String> m = JmxAgent.parseStatus(new String[] { "v=1", "garbage", "=nokey", "" });
        assertEquals(1, m.size());
        assertTrue(m.containsKey("v"));
    }
}
