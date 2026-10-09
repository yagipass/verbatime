package io.github.yagipass.verbatime.corpusfixtures;

public final class StackTraceFixture {

  static final class Inner {

    private Inner() {}

    static String frames() {
      StackTraceElement[] st = new Throwable().getStackTrace();
      return st[0].getClassName()
          + "."
          + st[0].getMethodName()
          + " <- "
          + st[1].getClassName()
          + "."
          + st[1].getMethodName();
    }
  }

  private StackTraceFixture() {}

  public static String topTwoFrames() {
    return Inner.frames();
  }
}
