package io.github.yagipass.verbatime.corpusfixtures;

public final class DepthWalkFixture {

  private DepthWalkFixture() {}

  public static String callerFoundByDepth() {
    return Library.caller().getName();
  }

  static final class Library {

    private Library() {}

    static Class<?> caller() {
      return Internals.callerAtDepth(2);
    }
  }

  static final class Internals {

    private static final StackWalker WALKER =
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private Internals() {}

    static Class<?> callerAtDepth(int depth) {
      return WALKER
          .walk(frames -> frames.skip(depth).findFirst())
          .orElseThrow()
          .getDeclaringClass();
    }
  }
}
