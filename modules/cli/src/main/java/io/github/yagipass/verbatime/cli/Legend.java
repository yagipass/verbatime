package io.github.yagipass.verbatime.cli;

import java.util.LinkedHashSet;
import java.util.Set;

final class Legend {

    private final Names names;

    private final Set<Integer> ids = new LinkedHashSet<>();

    Legend(final Names names) {
        this.names = names;
    }

    void add(final int methodId) {
        if (names.collides(methodId)) {
            ids.add(methodId);
        }
    }

    void print(final Out out) {
        if (ids.isEmpty()) {
            return;
        }
        out.text("");
        out.text("names printed alike:");
        for (final int id : ids) {
            out.text("  " + names.displayName(id) + " = " + names.fullName(id));
            out.json(new Json("name").put("method", names.displayName(id)).put("full", names.fullName(id)));
        }
    }
}
