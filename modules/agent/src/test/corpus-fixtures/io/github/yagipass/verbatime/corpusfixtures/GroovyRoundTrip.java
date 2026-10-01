package io.github.yagipass.verbatime.corpusfixtures;

import groovy.lang.GroovyShell;

public final class GroovyRoundTrip {

    private GroovyRoundTrip() {
    }

    public static String run() {
        GroovyShell shell = new GroovyShell(GroovyRoundTrip.class.getClassLoader());
        return String.valueOf(shell.evaluate("[3, 1, 2].sort().collect { it * 2 }.join(',') + ' ' + 'verbatime'.reverse()"));
    }
}
