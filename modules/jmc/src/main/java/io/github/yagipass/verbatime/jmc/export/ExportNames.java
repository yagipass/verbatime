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
    if (s.indexOf('\n') < 0 && s.indexOf('\r') < 0) {
      return s;
    }
    return s.replace("\r", "").replace("\n", " ");
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
