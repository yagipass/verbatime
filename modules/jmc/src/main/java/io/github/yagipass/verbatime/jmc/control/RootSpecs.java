package io.github.yagipass.verbatime.jmc.control;

import java.util.ArrayList;
import java.util.List;

final class RootSpecs {

    private RootSpecs() {
    }

    static boolean looksLikeSpec(String s) {
        String t = s.trim();
        int sep = t.indexOf("::");
        return sep > 0 && sep + 2 < t.length();
    }

    static String[] with(List<RootEntry> applied, String add) {
        List<String> out = specs(applied);
        if (!out.contains(add)) {
            out.add(add);
        }
        return out.toArray(new String[0]);
    }

    static String[] without(List<RootEntry> applied, String remove) {
        List<String> out = specs(applied);
        out.remove(remove);
        return out.toArray(new String[0]);
    }

    private static List<String> specs(List<RootEntry> applied) {
        List<String> out = new ArrayList<>();
        for (RootEntry r : applied) {
            if (!out.contains(r.spec())) {
                out.add(r.spec());
            }
        }
        return out;
    }
}
