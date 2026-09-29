package io.github.yagipass.verbatime.jmc.index;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

import com.google.errorprone.annotations.Var;

import io.github.yagipass.verbatime.format.RecordEncoder;
import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;

public final class RandomTraces {

    private RandomTraces() {
    }

    public static byte[] random(long seed) {
        Random rng = new Random(seed);
        int numTids = 1 + rng.nextInt(4);
        int numClasses = 3 + rng.nextInt(5);
        int numExceptions = 1 + rng.nextInt(4);

        List<Deque<byte[]>> queues = new ArrayList<>();
        for (int t = 0; t < numTids; t++) {
            long tid = 10 + t * 7L;
            Deque<byte[]> q = new ArrayDeque<>();
            q.add(record(w -> w.thread(tid, "worker-" + tid)));
            @Var long ticks = 1000L + rng.nextInt(5000);
            int sessions = 1 + rng.nextInt(3);
            for (int s = 0; s < sessions; s++) {
                boolean lastSession = s == sessions - 1;
                int kind = lastSession ? rng.nextInt(3) : 0;
                ticks += rng.nextInt(200);
                List<long[]> events = new ArrayList<>();
                int target = 40 + rng.nextInt(400);
                @Var int depth = 0;
                int maxDepth = 2 + rng.nextInt(30);
                while (events.size() < target || (depth > 0 && kind == 0)) {
                    boolean enter;
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
                boolean ended = kind == 0;
                long lastEmitted = events.isEmpty() ? ticks : events.get(events.size() - 1)[0];
                chunkEvents(events, () -> 1 + rng.nextInt(24), (base, payload, isLast) -> {
                    boolean endHere = ended && isLast && rng.nextBoolean();
                    q.add(record(w -> w.chunk(tid, base, payload, endHere)));
                    if (ended && isLast && !endHere) {
                        q.add(record(w -> w.chunk(tid, lastEmitted, new byte[0], true)));
                    }
                });
                if (events.isEmpty() && ended) {
                    long b = ticks;
                    q.add(record(w -> w.chunk(tid, b, new byte[0], true)));
                }
                ticks = lastEmitted + 1 + rng.nextInt(500);
            }
            queues.add(q);
        }

        Deque<byte[]> classes = new ArrayDeque<>();
        for (int c = 0; c < numClasses; c++) {
            if (c == 1) {
                continue;
            }
            String[] sigs = new String[10];
            for (int m = 0; m < 10; m++) {
                sigs[m] = "m" + m + "()V";
            }
            int base = c * 10;
            classes.add(record(w -> w.clazz(base, "pkg.gen.Cls" + base, sigs)));
        }
        queues.add(classes);

        Deque<byte[]> exceptions = new ArrayDeque<>();
        for (int x = 1; x <= numExceptions; x++) {
            if (x == 2) {
                continue;
            }
            int id = x;
            exceptions.add(record(w -> w.exception(id, "pkg.gen.Failure" + id + "Exception")));
        }
        queues.add(exceptions);

        Deque<byte[]> gcs = new ArrayDeque<>();
        int numGcs = rng.nextInt(6);
        String[] collectors = { "G1 Young Generation", "G1 Old Generation", "Copy", "MarkSweepCompact" };
        String[] causes = { "G1 Evacuation Pause", "Allocation Failure", "System.gc()", "G1 Humongous Allocation" };
        for (int g = 0; g < numGcs; g++) {
            long start = g == 0 ? 0 : rng.nextInt(20_000);
            long dur = g == 1 ? 0 : 10_000L * (1 + rng.nextInt(120));
            int action = rng.nextInt(3);
            String name = g == 2 ? "X".repeat(Vbtm.MAX_GC_LABEL_BYTES) : collectors[rng.nextInt(collectors.length)];
            String cause = causes[rng.nextInt(causes.length)];
            gcs.add(record(w -> w.gc(start, dur, action, name, cause)));
        }
        if (!gcs.isEmpty()) {
            queues.add(gcs);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(RecordEncoder.header(TestTraces.DEFAULT_START_EPOCH_MS, TestTraces.DEFAULT_UTC_OFFSET_SECONDS));
        List<Deque<byte[]>> live = new ArrayList<>(queues);
        while (!live.isEmpty()) {
            Deque<byte[]> q = live.get(rng.nextInt(live.size()));
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

    public static void chunkEvents(List<long[]> events, IntSupplier chunkSize, ChunkSink sink) {
        @Var int i = 0;
        while (i < events.size()) {
            int k = Math.min(chunkSize.getAsInt(), events.size() - i);
            long base = events.get(i)[0];
            TraceBuilder.Payload p = new TraceBuilder.Payload(base);
            for (int j = i; j < i + k; j++) {
                long[] e = events.get(j);
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

    private static byte[] record(Rec r) {
        TraceBuilder w = TestTraces.writer();
        r.write(w);
        byte[] all = w.bytes();
        byte[] rec = new byte[all.length - Vbtm.HEADER_BYTES];
        System.arraycopy(all, Vbtm.HEADER_BYTES, rec, 0, rec.length);
        return rec;
    }
}
