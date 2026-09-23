package io.github.yagipass.verbatime.fixtures;

public final class PrivateMain {

    private PrivateMain() {
    }

    public static void run() {
        main(new String[0]);
    }

    private static void main(final String[] args) {
        System.out.println("root() = " + new Fixture().root() + " with " + args.length + " args");
    }
}
