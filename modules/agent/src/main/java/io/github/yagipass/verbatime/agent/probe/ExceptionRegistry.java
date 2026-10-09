package io.github.yagipass.verbatime.agent.probe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ExceptionRegistry {

  static final int UNKNOWN = 0;

  private static final Object LOCK = new Object();

  private static final Map<String, Integer> byName = new HashMap<>();

  private static final List<String> names = new ArrayList<>();

  private static @Nullable TraceFileWriter sink;

  private static final ClassValue<Integer> IDS =
      new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
          String name = type.getName();
          synchronized (LOCK) {
            Integer existing = byName.get(name);
            if (existing != null) {
              return existing;
            }
            int id = names.size() + 1;
            byName.put(name, id);
            names.add(name);
            if (sink != null) {
              sink.writeException(id, name);
            }
            return id;
          }
        }
      };

  private ExceptionRegistry() {}

  public static int id(Class<?> type) {
    return IDS.get(type);
  }

  public static String name(int id) {
    synchronized (LOCK) {
      if (id < 1 || id > names.size()) {
        return "<unknown#" + id + ">";
      }
      return names.get(id - 1);
    }
  }

  static void attach(TraceFileWriter w) {
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
}
