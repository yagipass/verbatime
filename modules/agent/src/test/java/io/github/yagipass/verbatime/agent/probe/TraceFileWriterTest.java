package io.github.yagipass.verbatime.agent.probe;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.google.errorprone.annotations.Var;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.DecodedTrace;
import io.github.yagipass.verbatime.format.TraceReader;
import io.github.yagipass.verbatime.format.Vbtm;

public final class TraceFileWriterTest {

    private TraceFileWriterTest() {
    }

    public static void run() throws Exception {
        Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
        Files.createDirectories(tmp);
        int idA = MethodRegistry.reserveIds("test.bin.A", List.of("a()V"));
        int idB = MethodRegistry.reserveIds("test.bin.B", List.of("b()V"));

        roundtrip(tmp, idA, idB);
        defaultChunkStartsSmallAndStaysBelowHumongous(tmp, idA);
        growingBufferFlushesOnlyAtTheChunkSize(tmp, idA, idB);
        quantization(tmp, idA);
        rollover(tmp, idA, idB);
        threadRename(tmp, idA);
        concurrentThreads(tmp, idA, idB);
        unclosedFlush(tmp, idA, idB);
        stopDuringPushDoesNotRewriteFlushedEvents(tmp, idA, idB);
        stopReadingTheBufferFromBeforeItGrewKeepsTheRecording(tmp, idA, idB);
        stopReleasesTheBufferOfTheSessionLeftOnItsThread(tmp, idA, idB);
        emptyEndChunkCarriesTheLastTick(tmp, idA, idB);
        backwardFirstEventIsClampedToTheLastTick(tmp, idA);
        ioFailure(tmp, idA);
        errorDuringFullFlush(tmp, idA);
        validation(tmp, idA);
        classRecord(tmp);
        registrySink(tmp);
        exceptionRecord(tmp);
        exceptionRegistrySink(tmp);
        gcRecord(tmp);
        gcTicks();
    }

    private static void roundtrip(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-roundtrip.vbtm"));
        Session r = new Session(w, idA, 1, 8);
        long o = w.originNanos;
        int excBoom = 7;
        w.writeException(excBoom, "test.bin.Boom");
        fill(r, o + 1_000, (long) idA << 2, o + 2_050, (long) idB << 2, o + 3_000, ((long) excBoom << 32) | ((long) idB << 2) | Session.EXIT | Session.THROW, o + 4_099, ((long) idA << 2) | Session.EXIT);
        r.finish();
        w.close();

        Check.eq(Files.size(w.path()), w.committedBytes(), "committedBytes tracks the file length");
        byte[] all = Files.readAllBytes(w.path());
        Check.eq(Vbtm.RECORD_END, all[all.length - 1] & 0xFF, "file ends with the end-of-recording footer");
        Check.eq(Vbtm.VERSION, all[Vbtm.VERSION_OFFSET] & 0xFF, "the version byte follows the magic");
        Check.eq(Vbtm.RECORD_ANCHOR, all[Vbtm.ANCHOR_OFFSET] & 0xFF, "the anchor record follows the version byte");

        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(w.startEpochMs(), d.startEpochMs, "the anchor carries the writer's start time, so tick 0 has a wall-clock value");
        Check.eq(ZoneId.systemDefault().getRules().getOffset(Instant.ofEpochMilli(w.startEpochMs())).getTotalSeconds(), d.utcOffsetSeconds, "the anchor carries the server's UTC offset at start");
        Check.eq(Thread.currentThread().getName(), d.threadNames.get(Thread.currentThread().threadId()), "thread record");
        Check.eq(1, d.sessions.size(), "one session");
        DecodedTrace.DecodedSession s = d.sessions.get(1);
        Check.eq(idA, s.rootId, "root derived from the first ENTER");
        Check.that(s.ended, "CHUNK_END seen");
        Check.that(d.cleanEnd, "clean end-of-recording footer, so the file is not truncated");
        List<DecodedTrace.Event> ev = s.events;
        Check.eq(4, ev.size(), "event count");
        Check.eq(List.of(10L, 20L, 30L, 40L), ev.stream().map(DecodedTrace.Event::ticks).toList(), "ticks of 1000, 2050, 3000, and 4099 ns are quantized to 100 ns on absolute values");
        Check.eq(List.of(DecodedTrace.TAG_ENTER, DecodedTrace.TAG_ENTER, DecodedTrace.TAG_EXIT_THROW, DecodedTrace.TAG_EXIT), ev.stream().map(DecodedTrace.Event::tag).toList(), "tags");
        Check.eq(idA, ev.get(0).methodId(), "enter carries the method id");
        Check.eq(-1, ev.get(2).methodId(), "exit carries no method id");
        Check.eq(-1, ev.get(3).exceptionId(), "a normal exit carries no exception id and stays at 1-2 bytes");
        List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(s);
        Check.eq(2, nodes.size(), "two frames");
        Check.that(!nodes.get(0).thrown() && nodes.get(1).thrown(), "throw flag lands on the inner frame");
        Check.eq("test.bin.Boom", d.exceptionName(nodes.get(1).exceptionId()), "the throw exit's extra varint names what was thrown");
        Check.eq(0, d.danglingExceptionRefs, "the EXCEPTION record was on file before the chunk");
    }

    private static void defaultChunkStartsSmallAndStaysBelowHumongous(Path tmp, int idA) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-default-chunk.vbtm"));
        Session r = new Session(w, idA, 1);
        Check.that(r.buf.length * 8L <= 4 * 1024, "a root execution starts with a small buffer, because one is allocated on every root execution, and a full chunk for a root of a few calls set off a young GC every few thousand executions. See #88");
        r.enter(idA);
        for (int i = 0; i < 20_000; i++) {
            r.enter(idA);
            r.exit(idA, Session.EXIT);
        }
        r.exit(idA, Session.EXIT);
        long grown = r.buf.length * 8L;
        r.finish();
        w.close();

        Check.that(grown <= 256 * 1024, "the buffer grows with a long root execution but stays below G1's humongous threshold, which is half of the smallest 1 MiB region. See review A-3");
    }

    private static void growingBufferFlushesOnlyAtTheChunkSize(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-grow.vbtm"));
        Session shortRoot = new Session(w, idA, 1, 2, 8);
        shortRoot.enter(idA);
        for (int i = 0; i < 2; i++) {
            shortRoot.enter(idB);
            shortRoot.exit(idB, Session.EXIT);
        }
        shortRoot.exit(idA, Session.EXIT);
        shortRoot.finish();
        Session longRoot = new Session(w, idA, 2, 2, 8);
        longRoot.enter(idA);
        for (int i = 0; i < 4; i++) {
            longRoot.enter(idB);
            longRoot.exit(idB, Session.EXIT);
        }
        longRoot.exit(idA, Session.EXIT);
        longRoot.finish();
        w.close();

        DecodedTrace d = DecodedTrace.decode(w.path());
        DecodedTrace.DecodedSession s1 = d.sessions.get(1);
        Check.eq(6, s1.events.size(), "the events written before each growth are carried into the larger buffer");
        Check.eq(1, s1.chunks, "growing the buffer does not flush it, because every chunk takes the writer's lock and adds a chunk header");
        DecodedTrace.DecodedSession s2 = d.sessions.get(2);
        Check.eq(10, s2.events.size(), "no event is lost where the buffer stops growing and starts flushing");
        Check.eq(2, s2.chunks, "once the buffer reaches the chunk size it flushes, so a long root execution still writes full chunks");
        Check.that(s2.ended && DecodedTrace.toPreorder(s2).stream().noneMatch(DecodedTrace.Node::unclosed), "frames stay paired across the growth and the flush");
    }

    private static void quantization(Path tmp, int idA) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-quant.vbtm"));
        Session r = new Session(w, idA, 1, 512);
        long o = w.originNanos;
        int n = 100;
        for (int i = 0; i < n; i++) {
            long packed = (i % 2 == 0) ? (long) idA << 2 : ((long) idA << 2) | Session.EXIT;
            r.buf[r.pos] = o + i * 37L;
            r.buf[r.pos + 1] = packed;
            r.pos += 2;
        }
        r.finish();
        w.close();
        DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            expected.add(i * 37L / Vbtm.NANOS_PER_TICK);
        }
        Check.eq(expected, s.events.stream().map(DecodedTrace.Event::ticks).toList(), "no quantization drift over " + n + " events");
    }

    private static void rollover(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-rollover.vbtm"));
        Session r = new Session(w, idA, 7, 4);
        r.enter(idA);
        for (int i = 0; i < 20; i++) {
            r.enter(idB);
            r.exit(idB, Session.EXIT);
        }
        r.exit(idA, Session.EXIT);
        r.finish();
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        DecodedTrace.DecodedSession s = d.sessions.get(1);
        Check.eq(42, s.events.size(), "all events across chunks");
        Check.that(s.chunks >= 10, "rolled over into many chunks: " + s.chunks);
        Check.eq(idA, s.rootId, "root survives rollover");
        Check.that(s.ended, "CHUNK_END on the final chunk");
        @Var long prev = -1;
        for (DecodedTrace.Event e : s.events) {
            Check.that(e.ticks() >= prev, "ticks monotonic across chunk boundary");
            prev = e.ticks();
        }
        List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(s);
        Check.eq(21, nodes.size(), "frames survive rollover");
        Check.that(nodes.stream().noneMatch(DecodedTrace.Node::unclosed), "no unclosed frames");
    }

    private static void threadRename(Path tmp, int idA) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-rename.vbtm"));
        String original = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("bw-first");
            Session r = new Session(w, idA, 1, 2);
            r.enter(idA);
            r.exit(idA, Session.EXIT);
            Thread.currentThread().setName("bw-second");
            r.enter(idA);
            r.exit(idA, Session.EXIT);
            r.finish();
        } finally {
            Thread.currentThread().setName(original);
        }
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq("bw-second", d.threadNames.get(Thread.currentThread().threadId()), "latest thread name wins after rename re-emit");
    }

    private static void concurrentThreads(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-mt.vbtm"));
        Thread[] ts = new Thread[2];
        for (int t = 0; t < 2; t++) {
            int seq = t + 1;
            ts[t] = new Thread(() -> {
                Session r = new Session(w, idA, seq, 8);
                r.enter(idA);
                for (int i = 0; i < 1000; i++) {
                    r.enter(idB);
                    r.exit(idB, Session.EXIT);
                }
                r.exit(idA, Session.EXIT);
                r.finish();
            }, "bw-mt-" + seq);
            ts[t].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(2, d.sessions.size(), "two concurrent sessions");
        for (DecodedTrace.DecodedSession s : d.sessions.values()) {
            Check.eq(2002, s.events.size(), "session #" + s.seq + " complete");
            Check.that(s.ended, "session #" + s.seq + " ended");
        }
        Check.eq(2, d.threadNames.size(), "one THREAD record per thread");
    }

    private static void unclosedFlush(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-unclosed.vbtm"));
        Session r = new Session(w, idA, 1, 64);
        r.enter(idA);
        r.enter(idB);
        r.exit(idB, Session.EXIT);
        r.enter(idB);
        w.appendChunk(r, false);
        w.close();
        DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.that(!s.ended, "no CHUNK_END");
        Check.eq(4, s.events.size(), "partial chunk flushed");
        List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(s);
        Check.that(nodes.get(0).unclosed() && nodes.get(2).unclosed(), "open frames are unclosed");
        Check.that(!nodes.get(1).unclosed(), "completed frame is closed");
    }

    private static void stopDuringPushDoesNotRewriteFlushedEvents(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-stop-race.vbtm"));
        Session r = new Session(w, idA, 1, 64);
        long o = w.originNanos;
        fill(r, o + 1000, (long) idA << 2, o + 2000, (long) idB << 2, o + 3000, ((long) idB << 2) | Session.EXIT);
        int stale = r.pos;
        long[] held = r.buf;
        Thread stopper = new Thread(r::flushTruncated, "stopper");
        stopper.start();
        stopper.join();
        held[stale] = o + 4000;
        held[stale + 1] = ((long) idA << 2) | Session.EXIT;
        r.pos = stale + 2;
        r.finish();
        w.close();

        List<long[]> chunks = new ArrayList<>();
        TraceReader.read(Files.readAllBytes(w.path()), new TraceReader.Visitor() {
            @Override
            public void chunk(long tid, long baseTicks, byte[] data, int off, int len,
                    boolean sessionEnd, boolean truncated) {
                chunks.add(new long[] { baseTicks, len, sessionEnd ? 1 : 0 });
            }
        });
        Check.eq(1, chunks.size(), "the owner thread writes nothing for a session the stop already flushed, even when its push read pos before the stop reset it. See review A-7");
        Check.eq(0L, chunks.get(0)[2], "the stop cuts the session, so no END chunk follows the truncated one");
        DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.eq(3, s.events.size(), "the events flushed by the stop appear once, not duplicated in the call tree");
        Check.that(!s.ended, "the session reads as cut by the stop");
        Check.eq(0, r.pos, "the owner still resets its own position so the buffer cannot overflow");
    }

    private static void stopReadingTheBufferFromBeforeItGrewKeepsTheRecording(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-grow-stop.vbtm"));
        Session r = new Session(w, idA, 1, 2, 8);
        r.enter(idA);
        long[] beforeGrowth = r.buf;
        r.enter(idB);
        r.exit(idB, Session.EXIT);
        Check.that(r.buf.length > beforeGrowth.length && r.pos > beforeGrowth.length, "the owner grew the buffer and wrote past the old one's end");
        r.buf = beforeGrowth;
        Thread stopper = new Thread(r::flushTruncated, "stopper");
        stopper.start();
        stopper.join();
        w.close();

        Check.that(!w.hasFailed(), "a stop that sees the buffer from before the owner grew it, with the position from after, still writes the recording, because an index past the old buffer would fail the whole recording and lose every other session still open");
        byte[] all = Files.readAllBytes(w.path());
        Check.eq(Vbtm.RECORD_END, all[all.length - 1] & 0xFF, "the recording keeps its end-of-recording footer");
        DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.eq(2, s.events.size(), "the stop writes the events held by the buffer it saw");
        Check.that(!s.ended, "the session reads as cut by the stop");
    }

    private static void stopReleasesTheBufferOfTheSessionLeftOnItsThread(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-stop-release.vbtm"));
        Session r = new Session(w, idA, 1, 2, 8);
        r.enter(idA);
        for (int i = 0; i < 3; i++) {
            r.enter(idB);
            r.exit(idB, Session.EXIT);
        }
        Thread stopper = new Thread(r::flushTruncated, "stopper");
        stopper.start();
        stopper.join();
        w.close();

        Check.eq(0, r.buf.length, "the stop releases the buffer, because the session stays bound to its thread until that thread runs a root in a later recording, and a pool thread that was waiting inside a root may never do so. See #90");
        DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.eq(7, s.events.size(), "the stop writes the buffered events before it releases the buffer");
        Check.that(!s.ended, "the session reads as cut by the stop");
    }

    private static void emptyEndChunkCarriesTheLastTick(Path tmp, int idA, int idB) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-empty-end.vbtm"));
        Session r = new Session(w, idA, 1, 8);
        r.enter(idA);
        r.enter(idB);
        r.exit(idB, Session.EXIT);
        r.exit(idA, Session.EXIT);
        w.appendChunk(r, false);
        r.finish();
        w.close();

        List<long[]> chunks = new ArrayList<>();
        TraceReader.read(Files.readAllBytes(w.path()), new TraceReader.Visitor() {
            @Override
            public void chunk(long tid, long baseTicks, byte[] data, int off, int len,
                    boolean sessionEnd, boolean truncated) {
                chunks.add(new long[] { baseTicks, len, sessionEnd ? 1 : 0 });
            }
        });
        Check.eq(2, chunks.size(), "one chunk with the events, one empty END chunk");
        long[] last = chunks.get(1);
        Check.that(last[2] == 1 && last[1] == 0, "finish() after a flush writes an END chunk with no payload");
        DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.that(s.ended, "the empty END chunk still closes the session");
        long lastEventTicks = s.events.get(s.events.size() - 1).ticks();
        Check.eq(lastEventTicks, last[0], "the empty END chunk's baseTicks is the last tick of the previous chunk");
        Check.that(chunks.get(0)[0] <= last[0], "baseTicks never decrease within a thread");
    }

    private static void backwardFirstEventIsClampedToTheLastTick(Path tmp, int idA) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-backward-base.vbtm"));
        Session r = new Session(w, idA, 1, 512);
        long o = w.originNanos;
        long enterPacked = (long) idA << 2;
        long exitPacked = ((long) idA << 2) | Session.EXIT;
        r.buf[r.pos] = o + 1_000_000;
        r.buf[r.pos + 1] = enterPacked;
        r.buf[r.pos + 2] = o + 2_000_000;
        r.buf[r.pos + 3] = exitPacked;
        r.pos += 4;
        w.appendChunk(r, false);
        r.buf[r.pos] = o + 1_500_000;
        r.buf[r.pos + 1] = enterPacked;
        r.buf[r.pos + 2] = o + 2_500_000;
        r.buf[r.pos + 3] = exitPacked;
        r.pos += 4;
        r.finish();
        w.close();

        List<Long> bases = new ArrayList<>();
        TraceReader.read(Files.readAllBytes(w.path()), new TraceReader.Visitor() {
            @Override
            public void chunk(long tid, long baseTicks, byte[] data, int off, int len,
                    boolean sessionEnd, boolean truncated) {
                bases.add(baseTicks);
            }
        });
        long lastOfFirst = 2_000_000 / Vbtm.NANOS_PER_TICK;
        Check.eq(2, bases.size(), "two chunks");
        Check.eq(lastOfFirst, bases.get(1).longValue(),
                "a chunk whose first event is behind the previous chunk's last tick starts at that tick, like the in-chunk clamp does");
        DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        List<Long> ticks = s.events.stream().map(DecodedTrace.Event::ticks).toList();
        Check.eq(List.of(1_000_000 / Vbtm.NANOS_PER_TICK, lastOfFirst, lastOfFirst, 2_500_000 / Vbtm.NANOS_PER_TICK), ticks,
                "only the backward event is clamped, and once the clock catches up the real timestamps resume");
    }

    private static void ioFailure(Path tmp, int idA) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-io.vbtm"));
        Field f = TraceFileWriter.class.getDeclaredField("out");
        f.setAccessible(true);
        ((FileOutputStream) f.get(w)).close();
        Session r = new Session(w, idA, 1, 4);
        r.enter(idA);
        r.exit(idA, Session.EXIT);
        r.finish();
        Check.that(w.isStopped(), "writer disabled after IOException");
        Check.that(w.hasFailed(), "writer reports the IO failure");
        Check.eq(0, r.pos, "session buffer position reset so the hot path keeps working");
        long before = w.committedBytes();
        r.enter(idA);
        r.exit(idA, Session.EXIT);
        r.finish();
        Check.eq(before, w.committedBytes(), "writes after disable are no-ops, so nothing reaches the broken file");
        w.writeException(1, "test.bin.Late");
        Check.eq(before, w.committedBytes(), "an EXCEPTION record after disable is dropped like a chunk, so no dangling reference can be written either");
        w.writeGc(w.gcClockAtOriginMs + 5, 3, Vbtm.GC_ACTION_MINOR, "Copy", "Allocation Failure");
        Check.eq(before, w.committedBytes(), "a GC notification after disable is dropped too");
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.that(!d.cleanEnd, "no footer after an IO failure, so the file reads as truncated");
        Check.that(d.exceptionNames.isEmpty(), "nothing reached the file after disable");
        Check.that(d.gcPauses.isEmpty(), "no GC record reached the file after disable");
    }

    private static void errorDuringFullFlush(Path tmp, int idA) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-error.vbtm"));
        Field f = TraceFileWriter.class.getDeclaredField("out");
        f.setAccessible(true);
        ((FileOutputStream) f.get(w)).close();
        f.set(w, new FileOutputStream(w.path().toFile(), true) {
            @Override
            public void write(int b) {
                throw new OutOfMemoryError("injected: full-chunk flush");
            }

            @Override
            public void write(byte[] b) {
                throw new OutOfMemoryError("injected: full-chunk flush");
            }

            @Override
            public void write(byte[] b, int off, int len) {
                throw new OutOfMemoryError("injected: full-chunk flush");
            }
        });
        Session r = new Session(w, idA, 1, 2);
        r.enter(idA);
        try {
            r.exit(idA, Session.EXIT);
        } catch (Throwable t) {
            Check.fail("an Error inside the agent's flush must not surface from an instrumented method: " + t);
        }
        Check.eq(0, r.pos, "the session buffer position is reset even when the flush died with an Error, so the next push cannot write past the buffer");
        Check.that(w.isStopped(), "an Error during a flush disables the writer like an IOException, instead of leaving a hole in the recording");
        Check.that(w.hasFailed(), "the writer reports the failure so the operator learns why the recording stopped");
        long before = w.committedBytes();
        try {
            r.enter(idA);
            r.exit(idA, Session.EXIT);
            r.enter(idA);
            r.exit(idA, Session.EXIT);
            r.finish();
        } catch (Throwable t) {
            Check.fail("after the failed flush the session must keep absorbing events, not throw from the hot path: " + t);
        }
        Check.eq(0, r.pos, "a full buffer after disable is still reset by the early return");
        Check.eq(before, w.committedBytes(), "nothing is written after the failure");
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.that(!d.cleanEnd, "no footer after the failure, so the file reads as truncated rather than as a complete recording with a silent gap");
    }

    private static void validation(Path tmp, int idA) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-validate.vbtm"));
        long o = w.originNanos;
        Session r = new Session(w, idA, 1, 16);
        fill(r, o + 100, (long) idA << 2, o + 200, ((long) idA << 2) | Session.EXIT, 0L, (long) idA << 2, o + 400, ((long) idA << 2) | Session.EXIT);
        w.appendChunk(r, true);
        Session r2 = new Session(w, idA, 2, 16);
        fill(r2, o + 100, (long) idA << 2, o + 200, 999_999L << 2, o + 300, ((long) idA << 2) | Session.EXIT);
        w.appendChunk(r2, false);
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(2, d.sessions.get(1).events.size(), "encoding stopped at the zero timestamp");
        Check.eq(1, d.sessions.get(2).events.size(), "encoding stopped at the unregistered id");

        byte[] full = Files.readAllBytes(w.path());
        DecodedTrace cut = DecodedTrace.decode(java.util.Arrays.copyOf(full, full.length - 3));
        Check.that(!cut.cleanEnd, "truncation detected");
        Check.eq(2, cut.sessions.get(1).events.size(), "records before the cut survive");
    }

    private static void classRecord(Path tmp) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-class.vbtm"));
        int base = MethodRegistry.reserveIds("test.bin.C", List.of("c(I)J", "d()V"));
        w.writeClass(base, "test.bin.C", List.of("c(I)J", "d()V"));
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq("test.bin.C.c(I)J", d.methodNames.get(base), "first method of the block");
        Check.eq("test.bin.C.d()V", d.methodNames.get(base + 1), "second method of the block");
        Check.eq("test.bin.C.c(I)J", MethodRegistry.displayName(base), "registry agrees with the record");
    }

    private static void registrySink(Path tmp) throws Exception {
        int base = MethodRegistry.reserveIds("test.bin.S", List.of("s()V"));
        MethodRegistry.reserveIds("test.bin.Uncommitted", List.of("u()V"));

        TraceFileWriter w1 = TraceFileWriter.open(tmp.resolve("bw-sink1.vbtm"));
        MethodRegistry.attach(w1);
        MethodRegistry.commitClass(base, "test.bin.S", List.of("s()V"));
        MethodRegistry.detach();
        w1.close();
        DecodedTrace d1 = DecodedTrace.decode(w1.path());
        Check.eq("test.bin.S.s()V", d1.methodNames.get(base), "commit lands in the attached sink");
        Check.that(!d1.methodNames.containsValue("test.bin.Uncommitted.u()V"), "uncommitted registration is not written");
        Check.eq(Files.size(w1.path()), w1.committedBytes(), "committedBytes after CLASS records");

        TraceFileWriter w2 = TraceFileWriter.open(tmp.resolve("bw-sink2.vbtm"));
        MethodRegistry.attach(w2);
        MethodRegistry.detach();
        w2.close();
        DecodedTrace d2 = DecodedTrace.decode(w2.path());
        Check.eq("test.bin.S.s()V", d2.methodNames.get(base), "attach replays every committed block");
        Check.that(!d2.methodNames.containsValue("test.bin.Uncommitted.u()V"), "replay skips uncommitted registrations too");
    }

    private static void exceptionRecord(Path tmp) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-exc.vbtm"));
        w.writeException(1, "java.sql.SQLException");
        w.writeException(2, "com.example.ÜberException");
        Check.eq(Files.size(w.path()), w.committedBytes(), "committedBytes after EXCEPTION records");
        Session r = new Session(w, 0, 1, 8);
        long o = w.originNanos;
        fill(r, o + 100, 0L, o + 200, (2L << 32) | Session.EXIT | Session.THROW, o + 300, 0L, o + 400, Session.EXIT | Session.THROW);
        r.finish();
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq("java.sql.SQLException", d.exceptionNames.get(1), "id 1 named");
        Check.eq("com.example.ÜberException", d.exceptionNames.get(2), "class names are UTF-8, like CLASS records");
        List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(d.sessions.get(1));
        Check.eq("com.example.ÜberException", d.exceptionName(nodes.get(0).exceptionId()), "a throw exit resolves through the table");
        Check.eq(ExceptionRegistry.UNKNOWN, nodes.get(1).exceptionId(), "exception id 0 is written when the class could not be registered");
        Check.eq("<unknown>", d.exceptionName(nodes.get(1).exceptionId()), "id 0 reads as unknown without needing a record");
        Check.eq(0, d.danglingExceptionRefs, "id 0 is not a dangling reference");
    }

    private static void exceptionRegistrySink(Path tmp) throws Exception {
        TraceFileWriter w1 = TraceFileWriter.open(tmp.resolve("bw-excsink1.vbtm"));
        ExceptionRegistry.attach(w1);
        int arith = ExceptionRegistry.id(ArithmeticException.class);
        Check.that(arith >= 1, "ids start at 1 so that 0 can mean unknown");
        Check.eq(arith, ExceptionRegistry.id(ArithmeticException.class), "the second throw of a class costs a lookup, not a record");
        ExceptionRegistry.detach();
        int nfe = ExceptionRegistry.id(NumberFormatException.class);
        Check.that(nfe != arith, "a class first seen between recordings still gets its own id");
        w1.close();
        DecodedTrace d1 = DecodedTrace.decode(w1.path());
        Check.eq("java.lang.ArithmeticException", d1.exceptionNames.get(arith), "a first-seen class is written to the attached sink at throw time");
        Check.that(!d1.exceptionNames.containsKey(nfe), "a class seen with no sink attached is not written to a closed recording");
        Check.eq(Files.size(w1.path()), w1.committedBytes(), "committedBytes after EXCEPTION records");
        Check.eq("java.lang.ArithmeticException", ExceptionRegistry.name(arith), "registry agrees with the record");

        TraceFileWriter w2 = TraceFileWriter.open(tmp.resolve("bw-excsink2.vbtm"));
        ExceptionRegistry.attach(w2);
        ExceptionRegistry.detach();
        w2.close();
        DecodedTrace d2 = DecodedTrace.decode(w2.path());
        Check.eq("java.lang.ArithmeticException", d2.exceptionNames.get(arith), "attach replays every id, so a later recording can resolve throws of classes seen earlier");
        Check.eq("java.lang.NumberFormatException", d2.exceptionNames.get(nfe), "ids assigned while no sink was attached are replayed too");
    }

    private static void gcRecord(Path tmp) throws Exception {
        TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-gc.vbtm"));
        long u = w.gcClockAtOriginMs;
        w.writeGc(u + 250, 120, Vbtm.GC_ACTION_MAJOR, "MarkSweepCompact", "System.gc()");
        w.writeGc(u + 12, 3, Vbtm.GC_ACTION_MINOR, "Copy", "Allocation Failure");
        w.writeGc(u + 400, 0, Vbtm.GC_ACTION_UNKNOWN, "ZGC Pauses", "Ünknown cause");
        Check.eq(Files.size(w.path()), w.committedBytes(), "committedBytes after GC records");
        Session r = new Session(w, 0, 1, 8);
        long o = w.originNanos;
        fill(r, o + 100, 0L, o + 200, Session.EXIT);
        r.finish();
        w.close();
        DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(3, d.gcPauses.size(), "every pause after the origin is on file");
        DecodedTrace.GcPause major = d.gcPauses.get(0);
        Check.eq(250 * Vbtm.TICKS_PER_MS, major.startTicks(), "start is the GC clock relative to the origin, on the 100 ns tick axis the chunks use");
        Check.eq(120 * Vbtm.TICKS_PER_MS, major.durTicks(), "duration in ticks");
        Check.eq(Vbtm.GC_ACTION_MAJOR, major.action(), "major/minor survives as a small int");
        Check.eq("MarkSweepCompact", major.collector(), "collector name inline");
        Check.eq("System.gc()", major.cause(), "cause inline");
        Check.eq(12 * Vbtm.TICKS_PER_MS, d.gcPauses.get(1).startTicks(), "file order is notification order, not time order, so readers sort");
        Check.eq(0L, d.gcPauses.get(2).durTicks(), "a sub-millisecond pause reads as 0 ticks rather than being dropped");
        Check.eq("Ünknown cause", d.gcPauses.get(2).cause(), "labels are UTF-8");
        Check.eq(1, d.sessions.size(), "GC records do not disturb the chunk stream");
    }

    private static void gcTicks() {
        long u = 1_000;
        Check.eq(null, TraceFileWriter.toTicks(500, 400, u), "a pause that ended before the origin is not written");
        Check.eq(null, TraceFileWriter.toTicks(990, 10, u), "a pause that ended exactly at the origin has nothing to show either");
        Check.eq(List.of(0L, 5 * Vbtm.TICKS_PER_MS), longs(TraceFileWriter.toTicks(995, 10, u)), "a pause straddling the origin is cut at tick 0 and keeps only the part after it");
        Check.eq(List.of(7 * Vbtm.TICKS_PER_MS, 2 * Vbtm.TICKS_PER_MS), longs(TraceFileWriter.toTicks(1_007, 2, u)), "a pause after the origin maps 1:1");
        Check.eq(List.of(3 * Vbtm.TICKS_PER_MS, 0L), longs(TraceFileWriter.toTicks(1_003, -4, u)), "a negative duration is treated as zero rather than rejected, so a malformed notification cannot hide the pause");
    }

    private static List<Long> longs(long[] a) {
        return a == null ? null : List.of(a[0], a[1]);
    }

    private static void fill(Session r, long... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            r.buf[r.pos] = pairs[i];
            r.buf[r.pos + 1] = pairs[i + 1];
            r.pos += 2;
        }
    }
}
