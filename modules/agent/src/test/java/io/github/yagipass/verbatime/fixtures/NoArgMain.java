package io.github.yagipass.verbatime.fixtures;

public final class NoArgMain {

    private NoArgMain() {
    }

    public static void main() {
        System.out.println("root() = " + new Fixture().root());
    }
}
