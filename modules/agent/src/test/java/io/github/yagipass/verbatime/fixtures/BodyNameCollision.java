package io.github.yagipass.verbatime.fixtures;

public final class BodyNameCollision {

    public int foo() {
        return 1;
    }

    public int foo$trace() {
        return 2;
    }

    public int bar() {
        return foo() + foo$trace();
    }
}
