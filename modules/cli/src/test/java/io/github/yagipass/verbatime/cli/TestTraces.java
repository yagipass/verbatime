package io.github.yagipass.verbatime.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;

final class TestTraces {

    private TestTraces() {
    }

    static TraceBuilder builder() {
        final byte[] s1a = new TraceBuilder.Payload(0).enter(0, 0).enter(10, 1).exit(310).enter(320, 1).exit(420)
                .bytes();
        final byte[] s1b = new TraceBuilder.Payload(430).enter(430, 2).enter(440, 3).enter(450, 3).exit(460).exit(470)
                .exit(900).exit(1000).bytes();
        final byte[] s2 = new TraceBuilder.Payload(100).enter(100, 4).enter(110, 1).exitThrow(200, 1).exitThrow(210, 1)
                .bytes();
        final byte[] s3 = new TraceBuilder.Payload(2000).enter(2000, 0).enter(2010, 1).bytes();
        return new TraceBuilder(1_700_000_000_000L, 9 * 3600).thread(1, "http-1").thread(2, "http-2")
                .clazz(0, "com.example.App", "handle()V", "query(I)V", "render()V", "fib(I)I")
                .clazz(4, "com.other.App", "handle()V").exception(1, "java.sql.SQLException")
                .gc(150, 20, Vbtm.GC_ACTION_MINOR, "G1 Young", "G1 Evacuation Pause").chunk(1, 0, s1a, false)
                .chunk(2, 100, s2, true).chunk(1, 430, s1b, true).chunk(1, 2000, s3, false);
    }

    static TraceBuilder throwsBuilder() {
        final byte[] s1 = new TraceBuilder.Payload(0).enter(0, 0).enter(10, 1).enter(20, 2).exitThrow(50, 1)
                .exitThrow(60, 1).enter(70, 4).enter(80, 2).exitThrow(100, 1).enter(110, 2).exitThrow(140, 1).exit(150)
                .enter(160, 3).enter(170, 5).exitThrow(200, 2).exitThrow(230, 3).enter(240, 6).exitThrow(250, 0)
                .enter(260, 2).exit(290).exit(300).bytes();
        final byte[] s2 = new TraceBuilder.Payload(1000).enter(1000, 0).enter(1010, 1).enter(1020, 7)
                .exitThrow(1100, 1).exitThrow(1110, 1).exitThrow(1120, 1).bytes();
        return new TraceBuilder(1_700_000_000_000L, 9 * 3600).thread(1, "w-1").thread(2, "w-2")
                .clazz(0, "com.example.Svc", "handle()V", "load()V", "query()V", "parse()V", "retry()V", "read()V",
                        "log()V")
                .clazz(7, "com.other.Svc", "query()V").exception(1, "java.sql.SQLException")
                .exception(2, "java.io.IOException").exception(3, "java.lang.IllegalStateException")
                .chunk(1, 0, s1, true).chunk(2, 1000, s2, true);
    }

    static Path write(final Path dir, final String name, final byte[] bytes) throws IOException {
        final Path p = dir.resolve(name);
        Files.write(p, bytes);
        return p;
    }

    static Path trace(final Path dir) throws IOException {
        return write(dir, "t.vbtm", builder().end().bytes());
    }

    static Path throwsTrace(final Path dir) throws IOException {
        return write(dir, "e.vbtm", throwsBuilder().end().bytes());
    }
}
