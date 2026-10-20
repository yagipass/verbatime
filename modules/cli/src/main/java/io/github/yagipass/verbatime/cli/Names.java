package io.github.yagipass.verbatime.cli;

import com.google.errorprone.annotations.Var;
import java.util.HashMap;
import java.util.Map;

final class Names {

  private final TraceFile file;

  private final String[] display;

  private final boolean[] collides;

  Names(TraceFile file) {
    this.file = file;
    int n = file.methodCount;
    display = new String[n];
    collides = new boolean[n];
    Map<String, Integer> seen = new HashMap<>();
    for (int id = 0; id < n; id++) {
      TraceFile.MethodRef m = file.method(id);
      if (m == null) {
        continue;
      }
      String base = simpleClass(m.className()) + "." + methodName(m.sig());
      int k = seen.merge(base, 1, Integer::sum);
      display[id] = k == 1 ? base : base + "#" + k;
    }
    for (int id = 0; id < n; id++) {
      if (display[id] != null) {
        collides[id] = display[id].indexOf('#') >= 0 || seen.getOrDefault(display[id], 1) > 1;
      }
    }
  }

  static String simpleClass(String className) {
    return className.substring(className.lastIndexOf('.') + 1);
  }

  static String methodName(String sig) {
    int p = sig.indexOf('(');
    return p < 0 ? sig : sig.substring(0, p);
  }

  static String sanitize(String s) {
    @Var int i = 0;
    while (i < s.length() && !needsEscape(s.charAt(i))) {
      i++;
    }
    if (i == s.length()) {
      return s;
    }
    StringBuilder b = new StringBuilder(s.length() + 16).append(s, 0, i);
    for (; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!needsEscape(c)) {
        b.append(c);
      } else if (c <= 0xFF) {
        b.append(String.format("\\x%02x", (int) c));
      } else {
        b.append(String.format("\\u%04x", (int) c));
      }
    }
    return b.toString();
  }

  String displayName(int id) {
    return id >= 0 && id < display.length && display[id] != null
        ? display[id]
        : "<method " + id + ">";
  }

  String fullName(int id) {
    TraceFile.MethodRef m = file.method(id);
    return m == null ? displayName(id) : m.className() + "." + m.sig();
  }

  boolean collides(int id) {
    return id >= 0 && id < collides.length && collides[id];
  }

  private static boolean needsEscape(char c) {
    return c < 0x20
        || (c >= 0x7F && c <= 0x9F)
        || c == 0x2028
        || c == 0x2029
        || (c >= 0x202A && c <= 0x202E)
        || (c >= 0x2066 && c <= 0x2069);
  }
}
