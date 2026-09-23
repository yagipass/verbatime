package io.github.yagipass.verbatime.agent.probe;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.DecodedTrace;
import io.github.yagipass.verbatime.format.TraceReader;
import io.github.yagipass.verbatime.format.Vbtm;

public final class TraceFileWriterTest {

    private TraceFileWriterTest() {
    }

    public static void run() throws Exception {
        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
        Files.createDirectories(tmp);
        final int idA = MethodRegistry.reserveIds("test.bin.A", List.of("a()V"));
        final int idB = MethodRegistry.reserveIds("test.bin.B", List.of("b()V"));

        roundtrip(tmp, idA, idB);
        defaultChunkStaysBelowHumongous(tmp, idA);
        quantization(tmp, idA);
        rollover(tmp, idA, idB);
        threadRename(tmp, idA);
        concurrentThreads(tmp, idA, idB);
        unclosedFlush(tmp, idA, idB);
        stopDuringPushDoesNotRewriteFlushedEvents(tmp, idA, idB);
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

    private static void roundtrip(final Path tmp, final int idA, final int idB) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-roundtrip.vbtm"));
        final Session r = new Session(w, idA, 1, 8);
        final long o = w.originNanos;
        final int excBoom = 7;
        w.writeException(excBoom, "test.bin.Boom");
        fill(r, o + 1_000, (long) idA << 2, o + 2_050, (long) idB << 2, o + 3_000, ((long) excBoom << 32) | ((long) idB << 2) | Session.EXIT | Session.THROW, o + 4_099, ((long) idA << 2) | Session.EXIT);
        r.finish();
        w.close();

        Check.eq(Files.size(w.path()), w.committedBytes(), "committedBytes tracks the file length");
        final byte[] all = Files.readAllBytes(w.path());
        Check.eq(Vbtm.RECORD_END, all[all.length - 1] & 0xFF, "file ends with the end-of-recording footer");
        Check.eq(Vbtm.VERSION, all[Vbtm.VERSION_OFFSET] & 0xFF, "the version byte follows the magic");
        Check.eq(Vbtm.RECORD_ANCHOR, all[Vbtm.ANCHOR_OFFSET] & 0xFF, "the anchor record follows the version byte");

        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(w.startEpochMs(), d.startEpochMs, "the anchor carries the writer's start time, so tick 0 has a wall-clock value");
        Check.eq(ZoneId.systemDefault().getRules().getOffset(Instant.ofEpochMilli(w.startEpochMs())).getTotalSeconds(), d.utcOffsetSeconds, "the anchor carries the server's UTC offset at start");
        Check.eq(Thread.currentThread().getName(), d.threadNames.get(Thread.currentThread().threadId()), "thread record");
        Check.eq(1, d.sessions.size(), "one session");
        final DecodedTrace.DecodedSession s = d.sessions.get(1);
        Check.eq(idA, s.rootId, "root derived from the first ENTER");
        Check.that(s.ended, "CHUNK_END seen");
        Check.that(d.cleanEnd, "clean end-of-recording footer, so the file is not truncated");
        final List<DecodedTrace.Event> ev = s.events;
        Check.eq(4, ev.size(), "event count");
        Check.eq(List.of(10L, 20L, 30L, 40L), ev.stream().map(DecodedTrace.Event::ticks).toList(), "ticks of 1000, 2050, 3000, and 4099 ns are quantized to 100 ns on absolute values");
        Check.eq(List.of(DecodedTrace.TAG_ENTER, DecodedTrace.TAG_ENTER, DecodedTrace.TAG_EXIT_THROW, DecodedTrace.TAG_EXIT), ev.stream().map(DecodedTrace.Event::tag).toList(), "tags");
        Check.eq(idA, ev.get(0).methodId(), "enter carries the method id");
        Check.eq(-1, ev.get(2).methodId(), "exit carries no method id");
        Check.eq(-1, ev.get(3).exceptionId(), "a normal exit carries no exception id and stays at 1-2 bytes");
        final List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(s);
        Check.eq(2, nodes.size(), "two frames");
        Check.that(!nodes.get(0).thrown() && nodes.get(1).thrown(), "throw flag lands on the inner frame");
        Check.eq("test.bin.Boom", d.exceptionName(nodes.get(1).exceptionId()), "the throw exit's extra varint names what was thrown");
        Check.eq(0, d.danglingExceptionRefs, "the EXCEPTION record was on file before the chunk");
    }

    private static void defaultChunkStaysBelowHumongous(final Path tmp, final int idA) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-default-chunk.vbtm"));
        final Session r = new Session(w, idA, 1);
        w.close();

        Check.that(r.buf.length * 8L <= 256 * 1024, "the per-session buffer is allocated on every root execution, so it must stay below G1's humongous threshold, which is half of the smallest 1 MiB region. See review A-3");
    }

    private static void quantization(final Path tmp, final int idA) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-quant.vbtm"));
        final Session r = new Session(w, idA, 1, 512);
        final long o = w.originNanos;
        final int n = 100;
        for (int i = 0; i < n; i++) {
            final long packed = (i % 2 == 0) ? (long) idA << 2 : ((long) idA << 2) | Session.EXIT;
            r.buf[r.pos] = o + i * 37L;
            r.buf[r.pos + 1] = packed;
            r.pos += 2;
        }
        r.finish();
        w.close();
        final DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        final List<Long> expected = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            expected.add(i * 37L / Vbtm.NANOS_PER_TICK);
        }
        Check.eq(expected, s.events.stream().map(DecodedTrace.Event::ticks).toList(), "no quantization drift over " + n + " events");
    }

    private static void rollover(final Path tmp, final int idA, final int idB) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-rollover.vbtm"));
        final Session r = new Session(w, idA, 7, 4);
        r.enter(idA);
        for (int i = 0; i < 20; i++) {
            r.enter(idB);
            r.exit(idB, Session.EXIT);
        }
        r.exit(idA, Session.EXIT);
        r.finish();
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        final DecodedTrace.DecodedSession s = d.sessions.get(1);
        Check.eq(42, s.events.size(), "all events across chunks");
        Check.that(s.chunks >= 10, "rolled over into many chunks: " + s.chunks);
        Check.eq(idA, s.rootId, "root survives rollover");
        Check.that(s.ended, "CHUNK_END on the final chunk");
        long prev = -1;
        for (final DecodedTrace.Event e : s.events) {
            Check.that(e.ticks() >= prev, "ticks monotonic across chunk boundary");
            prev = e.ticks();
        }
        final List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(s);
        Check.eq(21, nodes.size(), "frames survive rollover");
        Check.that(nodes.stream().noneMatch(DecodedTrace.Node::unclosed), "no unclosed frames");
    }

    private static void threadRename(final Path tmp, final int idA) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-rename.vbtm"));
        final String original = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("bw-first");
            final Session r = new Session(w, idA, 1, 2);
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
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq("bw-second", d.threadNames.get(Thread.currentThread().threadId()), "latest thread name wins after rename re-emit");
    }

    private static void concurrentThreads(final Path tmp, final int idA, final int idB) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-mt.vbtm"));
        final Thread[] ts = new Thread[2];
        for (int t = 0; t < 2; t++) {
            final int seq = t + 1;
            ts[t] = new Thread(() -> {
                final Session r = new Session(w, idA, seq, 8);
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
        for (final Thread t : ts) {
            t.join();
        }
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(2, d.sessions.size(), "two concurrent sessions");
        for (final DecodedTrace.DecodedSession s : d.sessions.values()) {
            Check.eq(2002, s.events.size(), "session #" + s.seq + " complete");
            Check.that(s.ended, "session #" + s.seq + " ended");
        }
        Check.eq(2, d.threadNames.size(), "one THREAD record per thread");
    }

    private static void unclosedFlush(final Path tmp, final int idA, final int idB) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-unclosed.vbtm"));
        final Session r = new Session(w, idA, 1, 64);
        r.enter(idA);
        r.enter(idB);
        r.exit(idB, Session.EXIT);
        r.enter(idB);
        w.appendChunk(r, false);
        w.close();
        final DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.that(!s.ended, "no CHUNK_END");
        Check.eq(4, s.events.size(), "partial chunk flushed");
        final List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(s);
        Check.that(nodes.get(0).unclosed() && nodes.get(2).unclosed(), "open frames are unclosed");
        Check.that(!nodes.get(1).unclosed(), "completed frame is closed");
    }

    private static void stopDuringPushDoesNotRewriteFlushedEvents(final Path tmp, final int idA, final int idB) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-stop-race.vbtm"));
        final Session r = new Session(w, idA, 1, 64);
        final long o = w.originNanos;
        fill(r, o + 1000, (long) idA << 2, o + 2000, (long) idB << 2, o + 3000, ((long) idB << 2) | Session.EXIT);
        final int stale = r.pos;
        final Thread stopper = new Thread(r::flushTruncated, "stopper");
        stopper.start();
        stopper.join();
        r.pos = stale;
        fill(r, o + 4000, ((long) idA << 2) | Session.EXIT);
        r.finish();
        w.close();

        final List<long[]> chunks = new ArrayList<>();
        TraceReader.read(Files.readAllBytes(w.path()), new TraceReader.Visitor() {
            @Override
            public void chunk(final long tid, final long baseTicks, final byte[] data, final int off, final int len,
                    final boolean sessionEnd, final boolean truncated) {
                chunks.add(new long[] { baseTicks, len, sessionEnd ? 1 : 0 });
            }
        });
        Check.eq(1, chunks.size(), "the owner thread writes nothing for a session the stop already flushed, even when its push read pos before the stop reset it. See review A-7");
        Check.eq(0L, chunks.get(0)[2], "the stop cuts the session, so no END chunk follows the truncated one");
        final DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.eq(3, s.events.size(), "the events flushed by the stop appear once, not duplicated in the call tree");
        Check.that(!s.ended, "the session reads as cut by the stop");
        Check.eq(0, r.pos, "the owner still resets its own position so the buffer cannot overflow");
    }

    private static void emptyEndChunkCarriesTheLastTick(final Path tmp, final int idA, final int idB) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-empty-end.vbtm"));
        final Session r = new Session(w, idA, 1, 8);
        r.enter(idA);
        r.enter(idB);
        r.exit(idB, Session.EXIT);
        r.exit(idA, Session.EXIT);
        w.appendChunk(r, false);
        r.finish();
        w.close();

        final List<long[]> chunks = new ArrayList<>();
        TraceReader.read(Files.readAllBytes(w.path()), new TraceReader.Visitor() {
            @Override
            public void chunk(final long tid, final long baseTicks, final byte[] data, final int off, final int len,
                    final boolean sessionEnd, final boolean truncated) {
                chunks.add(new long[] { baseTicks, len, sessionEnd ? 1 : 0 });
            }
        });
        Check.eq(2, chunks.size(), "one chunk with the events, one empty END chunk");
        final long[] last = chunks.get(1);
        Check.that(last[2] == 1 && last[1] == 0, "finish() after a flush writes an END chunk with no payload");
        final DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        Check.that(s.ended, "the empty END chunk still closes the session");
        final long lastEventTicks = s.events.get(s.events.size() - 1).ticks();
        Check.eq(lastEventTicks, last[0], "the empty END chunk's baseTicks is the last tick of the previous chunk");
        Check.that(chunks.get(0)[0] <= last[0], "baseTicks never decrease within a thread");
    }

    private static void backwardFirstEventIsClampedToTheLastTick(final Path tmp, final int idA) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-backward-base.vbtm"));
        final Session r = new Session(w, idA, 1, 512);
        final long o = w.originNanos;
        final long enterPacked = (long) idA << 2;
        final long exitPacked = ((long) idA << 2) | Session.EXIT;
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

        final List<Long> bases = new ArrayList<>();
        TraceReader.read(Files.readAllBytes(w.path()), new TraceReader.Visitor() {
            @Override
            public void chunk(final long tid, final long baseTicks, final byte[] data, final int off, final int len,
                    final boolean sessionEnd, final boolean truncated) {
                bases.add(baseTicks);
            }
        });
        final long lastOfFirst = 2_000_000 / Vbtm.NANOS_PER_TICK;
        Check.eq(2, bases.size(), "two chunks");
        Check.eq(lastOfFirst, bases.get(1).longValue(),
                "a chunk whose first event is behind the previous chunk's last tick starts at that tick, like the in-chunk clamp does");
        final DecodedTrace.DecodedSession s = DecodedTrace.decode(w.path()).sessions.get(1);
        final List<Long> ticks = s.events.stream().map(DecodedTrace.Event::ticks).toList();
        Check.eq(List.of(1_000_000 / Vbtm.NANOS_PER_TICK, lastOfFirst, lastOfFirst, 2_500_000 / Vbtm.NANOS_PER_TICK), ticks,
                "only the backward event is clamped, and once the clock catches up the real timestamps resume");
    }

    private static void ioFailure(final Path tmp, final int idA) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-io.vbtm"));
        final Field f = TraceFileWriter.class.getDeclaredField("out");
        f.setAccessible(true);
        ((FileOutputStream) f.get(w)).close();
        final Session r = new Session(w, idA, 1, 4);
        r.enter(idA);
        r.exit(idA, Session.EXIT);
        r.finish();
        Check.that(w.isStopped(), "writer disabled after IOException");
        Check.that(w.hasFailed(), "writer reports the IO failure");
        Check.eq(0, r.pos, "session buffer position reset so the hot path keeps working");
        final long before = w.committedBytes();
        r.enter(idA);
        r.exit(idA, Session.EXIT);
        r.finish();
        Check.eq(before, w.committedBytes(), "writes after disable are no-ops, so nothing reaches the broken file");
        w.writeException(1, "test.bin.Late");
        Check.eq(before, w.committedBytes(), "an EXCEPTION record after disable is dropped like a chunk, so no dangling reference can be written either");
        w.writeGc(w.uptimeAtOriginMs + 5, 3, Vbtm.GC_ACTION_MINOR, "Copy", "Allocation Failure");
        Check.eq(before, w.committedBytes(), "a GC notification after disable is dropped too");
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.that(!d.cleanEnd, "no footer after an IO failure, so the file reads as truncated");
        Check.that(d.exceptionNames.isEmpty(), "nothing reached the file after disable");
        Check.that(d.gcPauses.isEmpty(), "no GC record reached the file after disable");
    }

    private static void errorDuringFullFlush(final Path tmp, final int idA) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-error.vbtm"));
        final Field f = TraceFileWriter.class.getDeclaredField("out");
        f.setAccessible(true);
        ((FileOutputStream) f.get(w)).close();
        f.set(w, new FileOutputStream(w.path().toFile(), true) {
            @Override
            public void write(final int b) {
                throw new OutOfMemoryError("injected: full-chunk flush");
            }

            @Override
            public void write(final byte[] b) {
                throw new OutOfMemoryError("injected: full-chunk flush");
            }

            @Override
            public void write(final byte[] b, final int off, final int len) {
                throw new OutOfMemoryError("injected: full-chunk flush");
            }
        });
        final Session r = new Session(w, idA, 1, 2);
        r.enter(idA);
        try {
            r.exit(idA, Session.EXIT);
        } catch (final Throwable t) {
            Check.fail("an Error inside the agent's flush must not surface from an instrumented method: " + t);
        }
        Check.eq(0, r.pos, "the session buffer position is reset even when the flush died with an Error, so the next push cannot write past the buffer");
        Check.that(w.isStopped(), "an Error during a flush disables the writer like an IOException, instead of leaving a hole in the recording");
        Check.that(w.hasFailed(), "the writer reports the failure so the operator learns why the recording stopped");
        final long before = w.committedBytes();
        try {
            r.enter(idA);
            r.exit(idA, Session.EXIT);
            r.enter(idA);
            r.exit(idA, Session.EXIT);
            r.finish();
        } catch (final Throwable t) {
            Check.fail("after the failed flush the session must keep absorbing events, not throw from the hot path: " + t);
        }
        Check.eq(0, r.pos, "a full buffer after disable is still reset by the early return");
        Check.eq(before, w.committedBytes(), "nothing is written after the failure");
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.that(!d.cleanEnd, "no footer after the failure, so the file reads as truncated rather than as a complete recording with a silent gap");
    }

    private static void validation(final Path tmp, final int idA) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-validate.vbtm"));
        final long o = w.originNanos;
        final Session r = new Session(w, idA, 1, 16);
        fill(r, o + 100, (long) idA << 2, o + 200, ((long) idA << 2) | Session.EXIT, 0L, (long) idA << 2, o + 400, ((long) idA << 2) | Session.EXIT);
        w.appendChunk(r, true);
        final Session r2 = new Session(w, idA, 2, 16);
        fill(r2, o + 100, (long) idA << 2, o + 200, 999_999L << 2, o + 300, ((long) idA << 2) | Session.EXIT);
        w.appendChunk(r2, false);
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(2, d.sessions.get(1).events.size(), "encoding stopped at the zero timestamp");
        Check.eq(1, d.sessions.get(2).events.size(), "encoding stopped at the unregistered id");

        final byte[] full = Files.readAllBytes(w.path());
        final DecodedTrace cut = DecodedTrace.decode(java.util.Arrays.copyOf(full, full.length - 3));
        Check.that(!cut.cleanEnd, "truncation detected");
        Check.eq(2, cut.sessions.get(1).events.size(), "records before the cut survive");
    }

    private static void classRecord(final Path tmp) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-class.vbtm"));
        final int base = MethodRegistry.reserveIds("test.bin.C", List.of("c(I)J", "d()V"));
        w.writeClass(base, "test.bin.C", List.of("c(I)J", "d()V"));
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq("test.bin.C.c(I)J", d.methodNames.get(base), "first method of the block");
        Check.eq("test.bin.C.d()V", d.methodNames.get(base + 1), "second method of the block");
        Check.eq("test.bin.C.c(I)J", MethodRegistry.displayName(base), "registry agrees with the record");
    }

    private static void registrySink(final Path tmp) throws Exception {
        final int base = MethodRegistry.reserveIds("test.bin.S", List.of("s()V"));
        MethodRegistry.reserveIds("test.bin.Uncommitted", List.of("u()V"));

        final TraceFileWriter w1 = TraceFileWriter.open(tmp.resolve("bw-sink1.vbtm"));
        MethodRegistry.attach(w1);
        MethodRegistry.commitClass(base, "test.bin.S", List.of("s()V"));
        MethodRegistry.detach();
        w1.close();
        final DecodedTrace d1 = DecodedTrace.decode(w1.path());
        Check.eq("test.bin.S.s()V", d1.methodNames.get(base), "commit lands in the attached sink");
        Check.that(!d1.methodNames.containsValue("test.bin.Uncommitted.u()V"), "uncommitted registration is not written");
        Check.eq(Files.size(w1.path()), w1.committedBytes(), "committedBytes after CLASS records");

        final TraceFileWriter w2 = TraceFileWriter.open(tmp.resolve("bw-sink2.vbtm"));
        MethodRegistry.attach(w2);
        MethodRegistry.detach();
        w2.close();
        final DecodedTrace d2 = DecodedTrace.decode(w2.path());
        Check.eq("test.bin.S.s()V", d2.methodNames.get(base), "attach replays every committed block");
        Check.that(!d2.methodNames.containsValue("test.bin.Uncommitted.u()V"), "replay skips uncommitted registrations too");
    }

    private static void exceptionRecord(final Path tmp) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-exc.vbtm"));
        w.writeException(1, "java.sql.SQLException");
        w.writeException(2, "com.example.ÜberException");
        Check.eq(Files.size(w.path()), w.committedBytes(), "committedBytes after EXCEPTION records");
        final Session r = new Session(w, 0, 1, 8);
        final long o = w.originNanos;
        fill(r, o + 100, 0L, o + 200, (2L << 32) | Session.EXIT | Session.THROW, o + 300, 0L, o + 400, Session.EXIT | Session.THROW);
        r.finish();
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq("java.sql.SQLException", d.exceptionNames.get(1), "id 1 named");
        Check.eq("com.example.ÜberException", d.exceptionNames.get(2), "class names are UTF-8, like CLASS records");
        final List<DecodedTrace.Node> nodes = DecodedTrace.toPreorder(d.sessions.get(1));
        Check.eq("com.example.ÜberException", d.exceptionName(nodes.get(0).exceptionId()), "a throw exit resolves through the table");
        Check.eq(ExceptionRegistry.UNKNOWN, nodes.get(1).exceptionId(), "exception id 0 is written when the class could not be registered");
        Check.eq("<unknown>", d.exceptionName(nodes.get(1).exceptionId()), "id 0 reads as unknown without needing a record");
        Check.eq(0, d.danglingExceptionRefs, "id 0 is not a dangling reference");
    }

    private static void exceptionRegistrySink(final Path tmp) throws Exception {
        final TraceFileWriter w1 = TraceFileWriter.open(tmp.resolve("bw-excsink1.vbtm"));
        ExceptionRegistry.attach(w1);
        final int arith = ExceptionRegistry.id(ArithmeticException.class);
        Check.that(arith >= 1, "ids start at 1 so that 0 can mean unknown");
        Check.eq(arith, ExceptionRegistry.id(ArithmeticException.class), "the second throw of a class costs a lookup, not a record");
        ExceptionRegistry.detach();
        final int nfe = ExceptionRegistry.id(NumberFormatException.class);
        Check.that(nfe != arith, "a class first seen between recordings still gets its own id");
        w1.close();
        final DecodedTrace d1 = DecodedTrace.decode(w1.path());
        Check.eq("java.lang.ArithmeticException", d1.exceptionNames.get(arith), "a first-seen class is written to the attached sink at throw time");
        Check.that(!d1.exceptionNames.containsKey(nfe), "a class seen with no sink attached is not written to a closed recording");
        Check.eq(Files.size(w1.path()), w1.committedBytes(), "committedBytes after EXCEPTION records");
        Check.eq("java.lang.ArithmeticException", ExceptionRegistry.name(arith), "registry agrees with the record");

        final TraceFileWriter w2 = TraceFileWriter.open(tmp.resolve("bw-excsink2.vbtm"));
        ExceptionRegistry.attach(w2);
        ExceptionRegistry.detach();
        w2.close();
        final DecodedTrace d2 = DecodedTrace.decode(w2.path());
        Check.eq("java.lang.ArithmeticException", d2.exceptionNames.get(arith), "attach replays every id, so a later recording can resolve throws of classes seen earlier");
        Check.eq("java.lang.NumberFormatException", d2.exceptionNames.get(nfe), "ids assigned while no sink was attached are replayed too");
    }

    private static void gcRecord(final Path tmp) throws Exception {
        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("bw-gc.vbtm"));
        final long u = w.uptimeAtOriginMs;
        w.writeGc(u + 250, 120, Vbtm.GC_ACTION_MAJOR, "MarkSweepCompact", "System.gc()");
        w.writeGc(u + 12, 3, Vbtm.GC_ACTION_MINOR, "Copy", "Allocation Failure");
        w.writeGc(u + 400, 0, Vbtm.GC_ACTION_UNKNOWN, "ZGC Pauses", "Ünknown cause");
        Check.eq(Files.size(w.path()), w.committedBytes(), "committedBytes after GC records");
        final Session r = new Session(w, 0, 1, 8);
        final long o = w.originNanos;
        fill(r, o + 100, 0L, o + 200, Session.EXIT);
        r.finish();
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(3, d.gcPauses.size(), "every pause after the origin is on file");
        final DecodedTrace.GcPause major = d.gcPauses.get(0);
        Check.eq(250 * Vbtm.TICKS_PER_MS, major.startTicks(), "start is uptime relative to the origin, on the 100 ns tick axis the chunks use");
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
        final long u = 1_000;
        Check.eq(null, TraceFileWriter.toTicks(500, 400, u), "a pause that ended before the origin is not written");
        Check.eq(null, TraceFileWriter.toTicks(990, 10, u), "a pause that ended exactly at the origin has nothing to show either");
        Check.eq(List.of(0L, 5 * Vbtm.TICKS_PER_MS), longs(TraceFileWriter.toTicks(995, 10, u)), "a pause straddling the origin is cut at tick 0 and keeps only the part after it");
        Check.eq(List.of(7 * Vbtm.TICKS_PER_MS, 2 * Vbtm.TICKS_PER_MS), longs(TraceFileWriter.toTicks(1_007, 2, u)), "a pause after the origin maps 1:1");
        Check.eq(List.of(3 * Vbtm.TICKS_PER_MS, 0L), longs(TraceFileWriter.toTicks(1_003, -4, u)), "a negative duration is treated as zero rather than rejected, so a malformed notification cannot hide the pause");
    }

    private static List<Long> longs(final long[] a) {
        return a == null ? null : List.of(a[0], a[1]);
    }

    private static void fill(final Session r, final long... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            r.buf[r.pos] = pairs[i];
            r.buf[r.pos + 1] = pairs[i + 1];
            r.pos += 2;
        }
    }
}
