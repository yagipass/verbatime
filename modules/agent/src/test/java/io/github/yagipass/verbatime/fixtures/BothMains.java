package io.github.yagipass.verbatime.fixtures;

public final class BothMains {

    public static void main(final String[] args) {
        System.out.println("root() = " + new Fixture().root() + " with " + args.length + " args");
    }

    public void main() {
        main(new String[0]);
    }
}
