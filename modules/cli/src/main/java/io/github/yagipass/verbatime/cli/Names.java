package io.github.yagipass.verbatime.cli;

import java.util.HashMap;
import java.util.Map;

final class Names {

    private final TraceFile file;

    private final String[] display;

    private final boolean[] collides;

    Names(final TraceFile file) {
        this.file = file;
        final int n = file.methodCount;
        display = new String[n];
        collides = new boolean[n];
        final Map<String, Integer> seen = new HashMap<>();
        for (int id = 0; id < n; id++) {
            if (file.methodClass(id) == null) {
                continue;
            }
            final String base = simpleClass(file.methodClass(id)) + "." + methodName(file.methodSig(id));
            final int k = seen.merge(base, 1, Integer::sum);
            display[id] = k == 1 ? base : base + "#" + k;
        }
        for (int id = 0; id < n; id++) {
            if (display[id] != null) {
                collides[id] = display[id].indexOf('#') >= 0 || seen.getOrDefault(display[id], 1) > 1;
            }
        }
    }

    String displayName(final int id) {
        return id >= 0 && id < display.length && display[id] != null ? display[id] : "<method " + id + ">";
    }

    String fullName(final int id) {
        final String cls = file.methodClass(id);
        return cls == null ? displayName(id) : cls + "." + file.methodSig(id);
    }

    boolean collides(final int id) {
        return id >= 0 && id < collides.length && collides[id];
    }

    static String simpleClass(final String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    static String methodName(final String sig) {
        final int p = sig.indexOf('(');
        return p < 0 ? sig : sig.substring(0, p);
    }

    static String sanitize(final String s) {
        if (s.indexOf('\n') < 0 && s.indexOf('\r') < 0 && s.indexOf('\t') < 0) {
            return s;
        }
        return s.replace("\r", "").replace('\n', ' ').replace('\t', ' ');
    }
}
