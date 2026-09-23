package io.github.yagipass.verbatime.jmc.index;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

import io.github.yagipass.verbatime.format.RecordEncoder;
import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;

public final class RandomTraces {

    private RandomTraces() {
    }

    public static byte[] random(final long seed) {
        final Random rng = new Random(seed);
        final int numTids = 1 + rng.nextInt(4);
        final int numClasses = 3 + rng.nextInt(5);
        final int numExceptions = 1 + rng.nextInt(4);

        final List<Deque<byte[]>> queues = new ArrayList<>();
        for (int t = 0; t < numTids; t++) {
            final long tid = 10 + t * 7L;
            final Deque<byte[]> q = new ArrayDeque<>();
            q.add(record(w -> w.thread(tid, "worker-" + tid)));
            long ticks = 1000L + rng.nextInt(5000);
            final int sessions = 1 + rng.nextInt(3);
            for (int s = 0; s < sessions; s++) {
                final boolean lastSession = s == sessions - 1;
                final int kind = lastSession ? rng.nextInt(3) : 0;
                ticks += rng.nextInt(200);
                final List<long[]> events = new ArrayList<>();
                final int target = 40 + rng.nextInt(400);
                int depth = 0;
                final int maxDepth = 2 + rng.nextInt(30);
                while (events.size() < target || (depth > 0 && kind == 0)) {
                    final boolean enter;
                    if (depth == 0) {
                        if (events.size() >= target) {
                            break;
                        }
                        enter = true;
                    } else if (depth >= maxDepth) {
                        enter = false;
                    } else if (events.size() >= target) {
                        enter = false;
                    } else {
                        enter = rng.nextInt(100) < 55;
                    }
                    if (rng.nextInt(100) < 60) {
                        ticks += rng.nextInt(40);
                    }
                    if (enter) {
                        events.add(new long[] { ticks, 1, rng.nextInt(numClasses * 10) });
                        depth++;
                    } else {
                        events.add(new long[] { ticks, 0, rng.nextInt(10) == 0 ? rng.nextInt(numExceptions + 1) : -1 });
                        depth--;
                    }
                    if (kind == 1 && depth > 0 && events.size() >= target + rng.nextInt(20)) {
                        break;
                    }
                }
                final boolean ended = kind == 0;
                final long lastEmitted = events.isEmpty() ? ticks : events.get(events.size() - 1)[0];
                chunkEvents(events, () -> 1 + rng.nextInt(24), (base, payload, isLast) -> {
                    final boolean endHere = ended && isLast && rng.nextBoolean();
                    q.add(record(w -> w.chunk(tid, base, payload, endHere)));
                    if (ended && isLast && !endHere) {
                        q.add(record(w -> w.chunk(tid, lastEmitted, new byte[0], true)));
                    }
                });
                if (events.isEmpty() && ended) {
                    final long b = ticks;
                    q.add(record(w -> w.chunk(tid, b, new byte[0], true)));
                }
                ticks = lastEmitted + 1 + rng.nextInt(500);
            }
            queues.add(q);
        }

        final Deque<byte[]> classes = new ArrayDeque<>();
        for (int c = 0; c < numClasses; c++) {
            if (c == 1) {
                continue;
            }
            final String[] sigs = new String[10];
            for (int m = 0; m < 10; m++) {
                sigs[m] = "m" + m + "()V";
            }
            final int base = c * 10;
            classes.add(record(w -> w.clazz(base, "pkg.gen.Cls" + base, sigs)));
        }
        queues.add(classes);

        final Deque<byte[]> exceptions = new ArrayDeque<>();
        for (int x = 1; x <= numExceptions; x++) {
            if (x == 2) {
                continue;
            }
            final int id = x;
            exceptions.add(record(w -> w.exception(id, "pkg.gen.Failure" + id + "Exception")));
        }
        queues.add(exceptions);

        final Deque<byte[]> gcs = new ArrayDeque<>();
        final int numGcs = rng.nextInt(6);
        final String[] collectors = { "G1 Young Generation", "G1 Old Generation", "Copy", "MarkSweepCompact" };
        final String[] causes = { "G1 Evacuation Pause", "Allocation Failure", "System.gc()", "G1 Humongous Allocation" };
        for (int g = 0; g < numGcs; g++) {
            final long start = g == 0 ? 0 : rng.nextInt(20_000);
            final long dur = g == 1 ? 0 : 10_000L * (1 + rng.nextInt(120));
            final int action = rng.nextInt(3);
            final String name = g == 2 ? "X".repeat(Vbtm.MAX_GC_LABEL_BYTES) : collectors[rng.nextInt(collectors.length)];
            final String cause = causes[rng.nextInt(causes.length)];
            gcs.add(record(w -> w.gc(start, dur, action, name, cause)));
        }
        if (!gcs.isEmpty()) {
            queues.add(gcs);
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(RecordEncoder.header(TestTraces.DEFAULT_START_EPOCH_MS, TestTraces.DEFAULT_UTC_OFFSET_SECONDS));
        final List<Deque<byte[]>> live = new ArrayList<>(queues);
        while (!live.isEmpty()) {
            final Deque<byte[]> q = live.get(rng.nextInt(live.size()));
            out.writeBytes(q.poll());
            if (q.isEmpty()) {
                live.remove(q);
            }
        }
        out.write(Vbtm.RECORD_END);
        return out.toByteArray();
    }

    public interface ChunkSink {
        void chunk(long baseTicks, byte[] payload, boolean lastOfSession);
    }

    public static void chunkEvents(final List<long[]> events, final IntSupplier chunkSize, final ChunkSink sink) {
        int i = 0;
        while (i < events.size()) {
            final int k = Math.min(chunkSize.getAsInt(), events.size() - i);
            final long base = events.get(i)[0];
            final TraceBuilder.Payload p = new TraceBuilder.Payload(base);
            for (int j = i; j < i + k; j++) {
                final long[] e = events.get(j);
                if (e[1] == 1) {
                    p.enter(e[0], (int) e[2]);
                } else if (e[2] < 0) {
                    p.exit(e[0]);
                } else {
                    p.exitThrow(e[0], (int) e[2]);
                }
            }
            i += k;
            sink.chunk(base, p.bytes(), i == events.size());
        }
    }

    private interface Rec {
        void write(TraceBuilder w);
    }

    private static byte[] record(final Rec r) {
        final TraceBuilder w = TestTraces.writer();
        r.write(w);
        final byte[] all = w.bytes();
        final byte[] rec = new byte[all.length - Vbtm.HEADER_BYTES];
        System.arraycopy(all, Vbtm.HEADER_BYTES, rec, 0, rec.length);
        return rec;
    }
}
