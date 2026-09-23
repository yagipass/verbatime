package io.github.yagipass.verbatime.agent.probe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExceptionRegistry {

    static final int UNKNOWN = 0;

    private static final Object LOCK = new Object();

    private static final Map<String, Integer> byName = new HashMap<>();

    private static final List<String> names = new ArrayList<>();

    private static TraceFileWriter sink;

    private static final ClassValue<Integer> IDS = new ClassValue<>() {
        @Override
        protected Integer computeValue(final Class<?> type) {
            final String name = type.getName();
            synchronized (LOCK) {
                final Integer existing = byName.get(name);
                if (existing != null) {
                    return existing;
                }
                final int id = names.size() + 1;
                byName.put(name, id);
                names.add(name);
                if (sink != null) {
                    sink.writeException(id, name);
                }
                return id;
            }
        }
    };

    private ExceptionRegistry() {
    }

    public static int id(final Class<?> type) {
        return IDS.get(type);
    }

    static void attach(final TraceFileWriter w) {
        synchronized (LOCK) {
            sink = w;
            for (int i = 0; i < names.size(); i++) {
                w.writeException(i + 1, names.get(i));
            }
        }
    }

    static void detach() {
        synchronized (LOCK) {
            sink = null;
        }
    }

    public static String name(final int id) {
        synchronized (LOCK) {
            if (id < 1 || id > names.size()) {
                return "<unknown#" + id + ">";
            }
            return names.get(id - 1);
        }
    }
}
