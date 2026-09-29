package io.github.yagipass.verbatime.fixtures;

public final class MainBodyNameCollision {

    private MainBodyNameCollision() {
    }

    public static void main(String[] args) {
        System.out.println("root() = " + new Fixture().root() + " with " + args.length + " args");
    }

    public static void main$trace(String[] args) {
        main(args);
    }
}
