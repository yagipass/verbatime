package io.github.yagipass.verbatime.fixtures;

public final class MainBodyNameCollision {

    private MainBodyNameCollision() {
    }

    public static void main(final String[] args) {
        System.out.println("root() = " + new Fixture().root() + " with " + args.length + " args");
    }

    public static void main$trace(final String[] args) {
        main(args);
    }
}
