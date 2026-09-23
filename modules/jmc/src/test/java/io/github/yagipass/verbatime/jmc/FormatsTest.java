package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

final class FormatsTest {

    @Test
    void durationsUseTheSameUnitsAndDecimalsAsThePage() {
        assertEquals("999 ns", Formats.fmtDur(999));
        assertEquals("1.00 µs", Formats.fmtDur(1_000));
        assertEquals("1000.00 µs", Formats.fmtDur(999_999));
        assertEquals("1.00 ms", Formats.fmtDur(1_000_000));
        assertEquals("964.47 ms", Formats.fmtDur(964_470_000));
        assertEquals("1.000 s", Formats.fmtDur(1_000_000_000L));
        assertEquals("1.235 s", Formats.fmtDur(1_234_567_890L));
        assertEquals("0 ns", Formats.fmtDur(-5), "negative durations from clock skew clamp to zero");
        assertEquals("4.9297 s", Formats.fmtTs(4_929_700_000L));
        assertEquals("1,234,567", Formats.fmtInt(1_234_567));
    }

    @Test
    void wallClockReadsLikeTheTooltipAndKeepsTheServerOffset() throws IOException {
        final long epochMs = OffsetDateTime.parse("2026-09-03T14:02:11.318+09:00").toInstant().toEpochMilli();
        final TraceSnapshot jp = TestTraces.index(new TraceBuilder(epochMs, 9 * 3600).thread(1, "t").end());
        assertEquals("2026-09-03 14:02:11.3180 +09:00", Formats.fmtWall(jp.wallClock(0)));
        assertEquals("2026-09-03 14:02:12.2383 +09:00", Formats.fmtWall(jp.wallClock(920_300_000L)));
        assertEquals("2026-09-03 14:02:12.2383 +09:00", Formats.fmtWall(jp.wallClock(920_399_900L)),
                "the 4th decimal is a whole tick: sub-tick digits are truncated, never rounded up");
        assertEquals("2026-09-04 00:00:00.0000 +09:00", Formats.fmtWall(jp.wallClock(35_868_682_000_000L)),
                "the date rolls over with the server's clock, so a long recording keeps its day right");

        final TraceSnapshot ny = TestTraces.index(new TraceBuilder(epochMs, -4 * 3600).thread(1, "t").end());
        assertEquals("2026-09-03 01:02:11.3180 -04:00", Formats.fmtWall(ny.wallClock(0)),
                "the file's offset is shown as recorded, whatever zone the viewer runs in");
        final TraceSnapshot utc = TestTraces.index(new TraceBuilder(epochMs, 0).thread(1, "t").end());
        assertEquals("2026-09-03 05:02:11.3180 +00:00", Formats.fmtWall(utc.wallClock(0)),
                "UTC prints as +00:00, not Z, so every line has the same shape");
    }

    @Test
    void signaturesCollapseToClassDotMethodWithSimpleTypeNames() {
        assertEquals("Cls.m", Formats.shortName("pkg.Cls.m(I)V"));
        assertEquals("Cls.m", Formats.shortName("Cls.m"));
        assertEquals("<no enter>", Formats.shortName("<no enter>"));
        assertEquals("C.m(String, int[], List[][], long, boolean)",
                Formats.signature("pkg.C.m(Ljava/lang/String;[I[[Ljava/util/List;JZ)V"));
        assertEquals("C.m()", Formats.signature("pkg.C.m()V"));
        assertEquals("LoggerContext.getTurboFilterChainDecision_0_3OrMore(Marker, Logger, Level, String, Object[], Throwable)",
                Formats.signature("ch.qos.logback.classic.LoggerContext.getTurboFilterChainDecision_0_3OrMore("
                        + "Lorg/slf4j/Marker;Lch/qos/logback/classic/Logger;Lch/qos/logback/classic/Level;"
                        + "Ljava/lang/String;[Ljava/lang/Object;Ljava/lang/Throwable;)Lch/qos/logback/core/spi/FilterReply;"));
        assertEquals("pkg.C.m(Q)V", Formats.signature("pkg.C.m(Q)V"),
                "an unparseable descriptor falls back to the raw name");
        assertEquals("pkg.C.m(I", Formats.signature("pkg.C.m(I"), "a missing ')' falls back to the raw name");
        assertEquals("C.m", Formats.signature("pkg.C.m"), "no descriptor: just the short name");
    }

    @Test
    void percentagesUseThePagesDecimalsAndRounding() {
        assertEquals("100%", Formats.fmtPct(100));
        assertEquals("61%", Formats.fmtPct(61.4));
        assertEquals("13%", Formats.fmtPct(12.5));
        assertEquals("10.0%", Formats.fmtPct(9.96), "the decimals follow the raw value, not the rounded one");
        assertEquals("5.3%", Formats.fmtPct(5.25));
        assertEquals("1.0%", Formats.fmtPct(1));
        assertEquals("0.92%", Formats.fmtPct(0.92));
        assertEquals("0.99%", Formats.fmtPct(0.995),
                "rounds the exact binary value like toFixed, so the page and the view never disagree");
        assertEquals("0.00%", Formats.fmtPct(0));
        assertEquals("0.00%", Formats.fmtPct(-3), "negative shares from clock skew clamp to zero");
        assertEquals("0.00%", Formats.fmtPct(Double.NaN), "a share without a denominator clamps to zero");
    }

    @Test
    void elapsedReadsLikeAStopwatchAtEveryScale() {
        assertEquals("0:00", Formats.fmtElapsed(0));
        assertEquals("0:59", Formats.fmtElapsed(59_999));
        assertEquals("1:01", Formats.fmtElapsed(61_000));
        assertEquals("1:02:03", Formats.fmtElapsed((3600 + 2 * 60 + 3) * 1000L));
        assertEquals("0:00", Formats.fmtElapsed(-5_000));
    }

    @Test
    void bytesKeepTheHistoricalFileTableThreshold() {
        assertEquals("1048575 B", Formats.fmtBytes((1 << 20) - 1));
        assertEquals("1.0 MB", Formats.fmtBytes(1 << 20));
        assertEquals("608.6 MB", Formats.fmtBytes(638_160_000L));
    }

    @Test
    void lagTellsAViewerWhetherThePullIsKeepingUp() {
        assertEquals("up to date", Formats.fmtLag(100, 100, 5.0));
        assertEquals("up to date", Formats.fmtLag(100, 120, 5.0),
                "a local file ahead of the agent after a resumed transfer is not a lag");
        assertEquals("3.5 MB behind", Formats.fmtLag(3_670_016L + 100, 100, 0),
                "without a write rate there is no time estimate");
        assertEquals("3.5 MB behind, about 2 s", Formats.fmtLag(3_670_016L + 100, 100, 1_835_008.0));
        assertEquals("512 B behind, about 1 s", Formats.fmtLag(512, 0, 100_000.0),
                "a tiny gap still reads as a second, never as zero");
    }

    @Test
    void countSpellsOutTheNounInsteadOfAnOptionalPluralSuffix() {
        assertEquals("1 root", Formats.plural(1, "root"));
        assertEquals("3 roots", Formats.plural(3, "root"));
        assertEquals("0 saved roots", Formats.plural(0, "saved root"));
    }
}
