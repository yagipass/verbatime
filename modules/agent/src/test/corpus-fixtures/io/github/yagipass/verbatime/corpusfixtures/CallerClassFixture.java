package io.github.yagipass.verbatime.corpusfixtures;

public final class CallerClassFixture {

  private CallerClassFixture() {}

  public static String callerOfCallee() {
    return Callee.callerClass().getName();
  }

  static final class Callee {

    private static final StackWalker WALKER =
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private Callee() {}

    static Class<?> callerClass() {
      return WALKER.getCallerClass();
    }
  }
}
