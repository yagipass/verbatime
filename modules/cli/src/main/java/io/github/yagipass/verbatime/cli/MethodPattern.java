package io.github.yagipass.verbatime.cli;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.errorprone.annotations.Var;

final class MethodPattern {

    private static final int CANDIDATES_SHOWN = 15;

    final String text;

    final List<Integer> methodIds;

    private final BitSet ids;

    private MethodPattern(String text, BitSet ids) {
        this.text = text;
        this.ids = ids;
        List<Integer> list = new ArrayList<>();
        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1)) {
            list.add(id);
        }
        this.methodIds = List.copyOf(list);
    }

    boolean matches(int methodId) {
        return methodId >= 0 && ids.get(methodId);
    }

    String fullNames(Names names) {
        StringBuilder sb = new StringBuilder();
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

    String shortNames(Names names) {
        List<String> seen = new ArrayList<>();
        for (int id : methodIds) {
            String d = names.displayName(id);
            int hash = d.indexOf('#');
            String base = hash < 0 ? d : d.substring(0, hash);
            if (!seen.contains(base)) {
                seen.add(base);
            }
        }
        return seen.size() <= 3 ? String.join(", ", seen)
                : String.join(", ", seen.subList(0, 3)) + ", +" + (seen.size() - 3) + " more";
    }

    static MethodPattern resolve(TraceFile file, Names names, String pattern,
            boolean acceptSeveral) {
        BitSet ids = match(file, names, pattern);
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

    private static void rejectSeveral(TraceFile file, String pattern, BitSet ids) {
        Map<String, Integer> groups = new LinkedHashMap<>();
        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1)) {
            TraceFile.MethodRef m = Objects.requireNonNull(file.method(id));
            groups.merge(m.className() + "." + Names.methodName(m.sig()), 1, Integer::sum);
        }
        if (groups.size() <= 1) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("'").append(pattern).append("' matches ").append(groups.size()).append(" methods:");
        @Var int shown = 0;
        for (String g : groups.keySet()) {
            if (shown++ == CANDIDATES_SHOWN) {
                sb.append("\n  ... ").append(groups.size() - CANDIDATES_SHOWN).append(" more");
                break;
            }
            sb.append("\n  ").append(g);
        }
        throw CliException.usage(sb.toString(), "narrow it to one, e.g. pkg.Class::method, or add --all");
    }

    private static BitSet match(TraceFile file, Names names, String pattern) {
        BitSet ids = new BitSet();
        int n = file.methodCount;
        if (pattern.indexOf('#') >= 0) {
            for (int id = 0; id < n; id++) {
                if (pattern.equals(names.displayName(id))) {
                    ids.set(id);
                }
            }
            return ids;
        }
        int sep = pattern.indexOf("::");
        if (sep >= 0) {
            matchClassMethod(file, pattern.substring(0, sep), pattern.substring(sep + 2), ids);
            return ids;
        }
        int dot = pattern.lastIndexOf('.');
        if (dot > 0 && dot < pattern.length() - 1) {
            matchClassMethod(file, pattern.substring(0, dot), pattern.substring(dot + 1), ids);
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        for (int id = 0; id < n; id++) {
            TraceFile.MethodRef m = file.method(id);
            if (m != null && (m.className() + "." + Names.methodName(m.sig())).contains(pattern)) {
                ids.set(id);
            }
        }
        return ids;
    }

    private static void matchClassMethod(TraceFile file, String cls, String method,
            BitSet ids) {
        boolean qualified = cls.indexOf('.') >= 0;
        for (int id = 0; id < file.methodCount; id++) {
            TraceFile.MethodRef m = file.method(id);
            if (m == null || !method.equals(Names.methodName(m.sig()))) {
                continue;
            }
            if (qualified ? m.className().equals(cls) : Names.simpleClass(m.className()).equals(cls)) {
                ids.set(id);
            }
        }
    }
}
