package io.github.yagipass.verbatime.jmc.control;

import java.util.ArrayList;
import java.util.List;

final class RootSpecs {

    private RootSpecs() {
    }

    static boolean looksLikeSpec(final String s) {
        final String t = s.trim();
        final int sep = t.indexOf("::");
        return sep > 0 && sep + 2 < t.length();
    }

    static String[] with(final List<RootEntry> applied, final String add) {
        final List<String> out = specs(applied);
        if (!out.contains(add)) {
            out.add(add);
        }
        return out.toArray(new String[0]);
    }

    static String[] without(final List<RootEntry> applied, final String remove) {
        final List<String> out = specs(applied);
        out.remove(remove);
        return out.toArray(new String[0]);
    }

    private static List<String> specs(final List<RootEntry> applied) {
        final List<String> out = new ArrayList<>();
        for (final RootEntry r : applied) {
            if (!out.contains(r.spec())) {
                out.add(r.spec());
            }
        }
        return out;
    }
}
