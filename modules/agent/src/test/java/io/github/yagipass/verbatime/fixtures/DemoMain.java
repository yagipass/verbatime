package io.github.yagipass.verbatime.fixtures;

public final class DemoMain {

    private DemoMain() {
    }

    public static void main(final String[] args) {
        final Fixture fx = new Fixture();
        System.out.println("root() = " + fx.root());
        try {
            fx.rootThrows();
        } catch (final IllegalStateException expected) {
            System.out.println("rootThrows() threw as expected");
        }
    }
}
