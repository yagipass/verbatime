package io.github.yagipass.verbatime.agent.probe;

public final class Log {

  private Log() {}

  public static void info(String msg) {
    System.err.println("[verbatime] " + msg);
  }

  public static void warn(String msg) {
    System.err.println("[verbatime] WARN " + msg);
  }

  public static String plural(long n, String noun) {
    return n + " " + (n == 1 ? noun : noun + "s");
  }
}
