package io.github.yagipass.verbatime.jmc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

public final class ViewerHtml {

    private static final String RESOURCE = "/viewer/viewer.html";

    static final String INIT_MARKER = "__VBTMINIT__";

    static final String HOST_MARKER = "__VBTMHOST__";

    private ViewerHtml() {
    }

    public static String template() throws IOException {
        try (InputStream in = ViewerHtml.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("bundle resource missing: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String render(final String template, final String metaJson, final String hostJs)
            throws IOException {
        return replace(replace(template, INIT_MARKER, metaJson), HOST_MARKER, hostJs);
    }

    static String page(final TraceSnapshot data, final ViewerJson.SentNames sentNames, final ViewerJson.SentSessions cursor)
            throws IOException {
        return render(template(), ViewerJson.metaJson(data, sentNames, cursor), "");
    }

    private static String replace(final String template, final String marker, final String value)
            throws IOException {
        final int i = template.indexOf(marker);
        if (i < 0) {
            throw new IOException(RESOURCE + " has no " + marker + " marker");
        }
        return template.substring(0, i) + value + template.substring(i + marker.length());
    }
}
