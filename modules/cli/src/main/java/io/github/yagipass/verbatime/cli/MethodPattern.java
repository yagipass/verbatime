package io.github.yagipass.verbatime.cli;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MethodPattern {

    private static final int CANDIDATES_SHOWN = 15;

    final String text;

    final List<Integer> methodIds;

    private final BitSet ids;

    private MethodPattern(final String text, final BitSet ids) {
        this.text = text;
        this.ids = ids;
        final List<Integer> list = new ArrayList<>();
        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1)) {
            list.add(id);
        }
        this.methodIds = List.copyOf(list);
    }

    boolean matches(final int methodId) {
        return methodId >= 0 && ids.get(methodId);
    }

    String fullNames(final Names names) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < methodIds.size() && i < 5; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names.fullName(methodIds.get(i)));
        }
        if (methodIds.size() > 5) {
            sb.append(", +").append(methodIds.size() - 5).append(" more");
        }
        return sb.toString();
    }

    String shortNames(final Names names) {
        final List<String> seen = new ArrayList<>();
        for (final int id : methodIds) {
            final String d = names.displayName(id);
            final int hash = d.indexOf('#');
            final String base = hash < 0 ? d : d.substring(0, hash);
            if (!seen.contains(base)) {
                seen.add(base);
            }
        }
        return seen.size() <= 3 ? String.join(", ", seen)
                : String.join(", ", seen.subList(0, 3)) + ", +" + (seen.size() - 3) + " more";
    }

    static MethodPattern resolve(final TraceFile file, final Names names, final String pattern,
            final boolean acceptSeveral) {
        final BitSet ids = match(file, names, pattern);
        if (ids.isEmpty()) {
            throw CliException.usage("no method matches '" + pattern + "'",
                    "use Class::method, pkg.Class::method, the name as printed such as Class.method, "
                            + "or part of the class and method name");
        }
        if (!acceptSeveral) {
            rejectSeveral(file, pattern, ids);
        }
        return new MethodPattern(pattern, ids);
    }

    private static void rejectSeveral(final TraceFile file, final String pattern, final BitSet ids) {
        final Map<String, Integer> groups = new LinkedHashMap<>();
        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1)) {
            groups.merge(file.methodClass(id) + "." + Names.methodName(file.methodSig(id)), 1, Integer::sum);
        }
        if (groups.size() <= 1) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("'").append(pattern).append("' matches ").append(groups.size()).append(" methods:");
        int shown = 0;
        for (final String g : groups.keySet()) {
            if (shown++ == CANDIDATES_SHOWN) {
                sb.append("\n  ... ").append(groups.size() - CANDIDATES_SHOWN).append(" more");
                break;
            }
            sb.append("\n  ").append(g);
        }
        throw CliException.usage(sb.toString(), "narrow it to one, e.g. pkg.Class::method, or add --all");
    }

    private static BitSet match(final TraceFile file, final Names names, final String pattern) {
        final BitSet ids = new BitSet();
        final int n = file.methodCount;
        if (pattern.indexOf('#') >= 0) {
            for (int id = 0; id < n; id++) {
                if (pattern.equals(names.displayName(id))) {
                    ids.set(id);
                }
            }
            return ids;
        }
        final int sep = pattern.indexOf("::");
        if (sep >= 0) {
            matchClassMethod(file, pattern.substring(0, sep), pattern.substring(sep + 2), ids);
            return ids;
        }
        final int dot = pattern.lastIndexOf('.');
        if (dot > 0 && dot < pattern.length() - 1) {
            matchClassMethod(file, pattern.substring(0, dot), pattern.substring(dot + 1), ids);
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        for (int id = 0; id < n; id++) {
            final String cls = file.methodClass(id);
            if (cls != null && (cls + "." + Names.methodName(file.methodSig(id))).contains(pattern)) {
                ids.set(id);
            }
        }
        return ids;
    }

    private static void matchClassMethod(final TraceFile file, final String cls, final String method,
            final BitSet ids) {
        final boolean qualified = cls.indexOf('.') >= 0;
        for (int id = 0; id < file.methodCount; id++) {
            final String c = file.methodClass(id);
            if (c == null || !method.equals(Names.methodName(file.methodSig(id)))) {
                continue;
            }
            if (qualified ? c.equals(cls) : Names.simpleClass(c).equals(cls)) {
                ids.set(id);
            }
        }
    }
}
