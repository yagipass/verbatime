package io.github.yagipass.verbatime.fixtures;

public interface FixtureInterface {

    default String greet(final String who) {
        return "hi " + who + helper();
    }

    static String helper() {
        return "!";
    }

    static int istatic(final int x) {
        return x * 10;
    }

    String abstractMethod();
}
