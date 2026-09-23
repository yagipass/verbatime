package io.github.yagipass.verbatime.jmc.index;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import io.github.yagipass.verbatime.format.RecordEncoder;
import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.export.SessionExporter;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor;

public final class IndexBench {

    private IndexBench() {
    }

    public static void main(final String[] args) throws IOException {
        final long events = args.length > 0 ? Long.parseLong(args[0]) : 10_000_000;
        final Path file = Path.of(args.length > 1 ? args[1] : "bench.vbtm");
        if (!Files.exists(file) || Files.size(file) < 5) {
            System.out.printf("generating %,d events into %s ...%n", events, file);
            final long t = System.nanoTime();
            generate(file, events);
            System.out.printf("  wrote %,d MB in %.1fs%n", Files.size(file) >> 20, (System.nanoTime() - t) / 1e9);
        } else {
            System.out.printf("reusing %s (%,d MB)%n", file, Files.size(file) >> 20);
        }

        long t = System.nanoTime();
        final TraceSnapshot data = TraceIndexer.index(file);
        final double pass1 = (System.nanoTime() - t) / 1e9;
        System.out.printf("pass 1: %.2fs — %,d calls, D=%.1fµs, %d threads, %d sessions%n", pass1, data.totalCalls,
                data.overviewThresholdNs / 1e3, data.threads.size(), data.sessions.size());

        final long span = data.maxNs - data.minNs;
        final Random rng = new Random(1);
        double worst = 0;
        double total = 0;
        final int n = 40;
        for (int i = 0; i < n; i++) {
            final long w = Math.max((long) (span * Math.pow(0.5, rng.nextInt(20))), 10_000);
            final long t0 = data.minNs + (long) (rng.nextDouble() * (span - w));
            t = System.nanoTime();
            final var res = WindowExtractor.extract(data, t0, t0 + w, 1600, WindowExtractor.DEFAULT_CALL_BUDGET);
            final double dt = (System.nanoTime() - t) / 1e9;
            worst = Math.max(worst, dt);
            total += dt;
            if (i < 5 || dt > 0.5) {
                System.out.printf("  window %,dns..+%,dns -> %,d calls in %.0fms%n", t0, w, res.totalCalls,
                        dt * 1000);
            }
        }
        System.out.printf("windows: mean %.0fms, worst %.0fms over %d random zooms%n", total / n * 1000, worst * 1000,
                n);

        Session longest = null;
        for (final Session s : data.sessions) {
            if (longest == null || s.durNs() > longest.durNs()) {
                longest = s;
            }
        }
        for (final long floor : new long[] { 0, 10_000 }) {
            final Path out = Files.createTempFile("bench-export", ".txt");
            t = System.nanoTime();
            final SessionExporter.Result r = SessionExporter.export(data, longest, floor, out,
                    TraceIndexer.ProgressListener.NONE);
            System.out.printf("export floor=%s: %.2fs — %,d MB, %,d lines, %,d calls%n",
                    SessionExporter.floorLabel(floor), (System.nanoTime() - t) / 1e9, r.bytes() >> 20, r.lines(),
                    r.calls());
            Files.deleteIfExists(out);
        }
    }

    private static void generate(final Path file, final long events) throws IOException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file.toFile()), 1 << 20)) {
            out.write(RecordEncoder.header(System.currentTimeMillis(), 0));
            final Random rng = new Random(11);
            final int tids = 6;
            for (int c = 0; c < 200; c++) {
                final List<String> sigs = new ArrayList<>(50);
                for (int m = 0; m < 50; m++) {
                    sigs.add("method" + m + "(Ljava/lang/String;I)V");
                }
                out.write(RecordEncoder.clazz(c * 50L, "com.example.generated.pkg" + (c % 10) + ".Class" + c, sigs));
            }
            for (int t = 0; t < tids; t++) {
                out.write(RecordEncoder.thread(100 + t, "http-nio-8080-exec-" + t));
            }

            final long perTid = events / tids;
            final long[] ticks = new long[tids];
            Arrays.fill(ticks, 1000);
            final int chunkEvents = 512 * 1024;
            final byte[] head = new byte[RecordEncoder.MAX_CHUNK_HEADER_BYTES];
            for (int t = 0; t < tids; t++) {
                long remaining = perTid;
                while (remaining > 0) {
                    final int inChunk = (int) Math.min(chunkEvents, remaining);
                    final long base = ticks[t];
                    final TraceBuilder.Payload payload = new TraceBuilder.Payload(base);
                    int depth = 0;
                    boolean first = true;
                    for (int i = 0; i < inChunk; i++) {
                        final boolean enter = depth == 0 || (depth < 60 && rng.nextInt(100) < 52);
                        final long delta = first ? 0 : rng.nextInt(60);
                        ticks[t] += first ? 0 : delta;
                        first = false;
                        if (enter) {
                            payload.enter(ticks[t], rng.nextInt(10_000));
                            depth++;
                        } else {
                            payload.exit(ticks[t]);
                            depth--;
                        }
                    }
                    remaining -= inChunk;
                    final byte[] bytes = payload.bytes();
                    final int n = RecordEncoder.chunkHeader(head, 0, 100 + t, base, bytes.length, false);
                    out.write(head, 0, n);
                    out.write(bytes);
                }
            }
            out.write(RecordEncoder.end());
        }
    }
}
