package io.github.yagipass.verbatime.jmc.export;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ExportNames {

  private final TraceSnapshot data;

  private String[] display;

  private byte[][] utf8;

  private int[] order = new int[256];

  private int count;

  private final Map<String, Integer> seen = new HashMap<>();

  ExportNames(TraceSnapshot data) {
    this.data = data;
    int n = Math.max(data.methodNames.length, 16);
    display = new String[n];
    utf8 = new byte[n][];
  }

  public static String shortName(String full) {
    int p = full.indexOf('(');
    String head = p < 0 ? full : full.substring(0, p);
    int last = head.lastIndexOf('.');
    if (last < 0) {
      return head;
    }
    int prev = head.lastIndexOf('.', last - 1);
    return prev < 0 ? head : head.substring(prev + 1);
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

  String fullName(int methodId) {
    return sanitize(data.methodName(methodId));
  }

  String displayName(int methodId) {
    ensureCapacity(methodId);
    @Var String s = display[methodId];
    if (s == null) {
      String base = shortName(fullName(methodId));
      int k = seen.merge(base, 1, Integer::sum);
      s = k == 1 ? base : base + "#" + k;
      display[methodId] = s;
      utf8[methodId] = s.getBytes(StandardCharsets.UTF_8);
      if (count == order.length) {
        order = Arrays.copyOf(order, count * 2);
      }
      order[count++] = methodId;
    }
    return s;
  }

  byte[] utf8(int methodId) {
    displayName(methodId);
    return utf8[methodId];
  }

  int registeredCount() {
    return count;
  }

  int registeredIdAt(int i) {
    return order[i];
  }

  private static boolean needsEscape(char c) {
    return c < 0x20
        || (c >= 0x7F && c <= 0x9F)
        || c == 0x2028
        || c == 0x2029
        || (c >= 0x202A && c <= 0x202E)
        || (c >= 0x2066 && c <= 0x2069);
  }

  private void ensureCapacity(int methodId) {
    if (methodId < 0) {
      throw new IllegalArgumentException("method id " + methodId);
    }
    if (methodId >= display.length) {
      int n = Math.max(methodId + 1, display.length * 2);
      display = Arrays.copyOf(display, n);
      utf8 = Arrays.copyOf(utf8, n);
    }
  }
}
