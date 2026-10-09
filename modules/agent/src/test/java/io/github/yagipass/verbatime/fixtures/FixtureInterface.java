package io.github.yagipass.verbatime.fixtures;

public interface FixtureInterface {

  static String helper() {
    return "!";
  }

  static int istatic(int x) {
    return x * 10;
  }

  default String greet(String who) {
    return "hi " + who + helper();
  }

  String abstractMethod();
}
