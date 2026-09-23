package io.github.yagipass.verbatime.examples.micronaut;

import io.micronaut.runtime.Micronaut;

public final class Application {

    private Application() {
    }

    public static void main(final String[] args) {
        Micronaut.run(Application.class, args);
    }
}
