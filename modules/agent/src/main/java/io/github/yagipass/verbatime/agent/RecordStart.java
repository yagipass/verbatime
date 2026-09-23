package io.github.yagipass.verbatime.agent;

import java.util.Locale;

public enum RecordStart {

    ONDEMAND, STARTUP;

    static RecordStart parse(final String value) {
        return switch (value) {
            case "ondemand" -> ONDEMAND;
            case "startup" -> STARTUP;
            default -> throw new IllegalArgumentException("record= needs startup or ondemand, got '" + value + "'");
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
