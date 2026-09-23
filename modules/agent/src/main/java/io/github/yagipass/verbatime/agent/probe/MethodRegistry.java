package io.github.yagipass.verbatime.agent.probe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import io.github.yagipass.verbatime.format.Vbtm;

public final class MethodRegistry {

    public static final int LIMIT_REACHED = -1;

    private static final Object LOCK = new Object();

    private static String[] classNames = new String[1024];

    private static String[] sigs = new String[1024];

    private static int size;

    public record CommittedClass(int baseId, String className, List<String> sigs) {
    }

    private static final Object SINK_LOCK = new Object();

    private static final List<CommittedClass> committed = new ArrayList<>();

    private static TraceFileWriter sink;

    private MethodRegistry() {
    }

    public static int reserveIds(final String className, final List<String> methodSigs) {
        synchronized (LOCK) {
            if (methodSigs.size() > Vbtm.METHOD_ID_LIMIT - size) {
                return LIMIT_REACHED;
            }
            final int base = size;
            final int needed = base + methodSigs.size();
            if (needed > classNames.length) {
                int n = classNames.length;
                while (n < needed) {
                    n *= 2;
                }
                classNames = Arrays.copyOf(classNames, n);
                sigs = Arrays.copyOf(sigs, n);
            }
            for (final String sig : methodSigs) {
                classNames[size] = className;
                sigs[size] = sig;
                size++;
            }
            return base;
        }
    }

    public static void commitClass(final int baseId, final String className, final List<String> methodSigs) {
        final List<String> copy = List.copyOf(methodSigs);
        synchronized (SINK_LOCK) {
            committed.add(new CommittedClass(baseId, className, copy));
            if (sink != null) {
                sink.writeClass(baseId, className, copy);
            }
        }
    }

    public static void attach(final TraceFileWriter w) {
        synchronized (SINK_LOCK) {
            sink = w;
            for (final CommittedClass c : committed) {
                w.writeClass(c.baseId(), c.className(), c.sigs());
            }
        }
    }

    public static void detach() {
        synchronized (SINK_LOCK) {
            sink = null;
        }
    }

    public static List<CommittedClass> committed() {
        synchronized (SINK_LOCK) {
            return List.copyOf(committed);
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return size;
        }
    }

    public static String methodName(final int id) {
        synchronized (LOCK) {
            return methodNameOf(sigs[id]);
        }
    }

    public static String methodNameOf(final String sig) {
        final int paren = sig.indexOf('(');
        return paren < 0 ? sig : sig.substring(0, paren);
    }

    public static String displayName(final int id) {
        synchronized (LOCK) {
            if (id < 0 || id >= size) {
                return "<unknown#" + id + ">";
            }
            return classNames[id] + "." + sigs[id];
        }
    }

    public static String[] search(final String query, final int max) {
        final String q = query.toLowerCase(Locale.ROOT);
        final TreeSet<String> hits = new TreeSet<>();
        for (final CommittedClass c : committed()) {
            for (final String sig : c.sigs()) {
                final String method = methodNameOf(sig);
                if (method.isEmpty() || method.charAt(0) == '<') {
                    continue;
                }
                final String candidate = c.className() + "::" + method;
                if (candidate.toLowerCase(Locale.ROOT).contains(q)) {
                    hits.add(candidate);
                }
            }
        }
        final String[] out = new String[Math.min(max, hits.size())];
        int n = 0;
        for (final String s : hits) {
            if (n == out.length) {
                break;
            }
            out[n++] = s;
        }
        return out;
    }
}
