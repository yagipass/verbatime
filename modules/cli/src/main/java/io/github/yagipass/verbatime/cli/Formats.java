package io.github.yagipass.verbatime.cli;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.Vbtm;
import java.util.Locale;

final class Formats {

  private Formats() {}

  static String ms(long ticks) {
    long t = Math.max(ticks, 0);
    return t / Vbtm.TICKS_PER_MS + "." + String.format(Locale.ROOT, "%04d", t % Vbtm.TICKS_PER_MS);
  }

  static String grouped(long v) {
    return String.format(Locale.US, "%,d", v);
  }

  static String percent(long part, long whole) {
    if (whole <= 0) {
      return "-";
    }
    long tenths = Math.round(part * 1000.0 / whole);
    return tenths / 10 + "." + tenths % 10 + "%";
  }

  static String duration(long ticks) {
    if (ticks <= 0) {
      return "0";
    }
    if (ticks % (Vbtm.TICKS_PER_MS * 1000) == 0) {
      return ticks / (Vbtm.TICKS_PER_MS * 1000) + "s";
    }
    if (ticks >= Vbtm.TICKS_PER_MS) {
      return trimZeros(ms(ticks)) + "ms";
    }
    return ticks % 10 == 0 ? ticks / 10 + "us" : ticks / 10 + "." + ticks % 10 + "us";
  }

  static String plural(long n, String noun) {
    return plural(n, noun, noun + "s");
  }

  static String plural(long n, String one, String many) {
    return grouped(n) + " " + (n == 1 ? one : many);
  }

  static long roundUpTicks(long ticks) {
    if (ticks <= 1) {
      return Math.max(ticks, 0);
    }
    @Var long p = 1;
    while (p * 10 <= ticks) {
      p *= 10;
    }
    for (long m : new long[] {1, 2, 5, 10}) {
      if (p * m >= ticks) {
        return p * m;
      }
    }
    return p * 10;
  }

  private static String trimZeros(String s) {
    @Var int end = s.length();
    while (s.charAt(end - 1) == '0') {
      end--;
    }
    if (s.charAt(end - 1) == '.') {
      end--;
    }
    return s.substring(0, end);
  }
}
