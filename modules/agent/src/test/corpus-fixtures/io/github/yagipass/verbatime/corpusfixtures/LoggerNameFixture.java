package io.github.yagipass.verbatime.corpusfixtures;

import org.apache.logging.log4j.LogManager;

public final class LoggerNameFixture {

    private LoggerNameFixture() {
    }

    public static String loggerName() {
        return LogManager.getLogger().getName();
    }
}
