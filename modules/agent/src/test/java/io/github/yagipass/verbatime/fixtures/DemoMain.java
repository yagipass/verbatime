package io.github.yagipass.verbatime.fixtures;

public final class DemoMain {

  private DemoMain() {}

  public static void main(String[] args) {
    Fixture fx = new Fixture();
    System.out.println("root() = " + fx.root());
    try {
      fx.rootThrows();
    } catch (IllegalStateException expected) {
      System.out.println("rootThrows() threw as expected");
    }
  }
}
