package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer.ProgressListener;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.MatchSearch;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor;

final class ViewerJsonTest {

    @Test
    void initAndWindowJsonAreWellFormed() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main \"quoted\" <!--<script>\n");
        w.clazz(1, "pkg.Root", "root(Ljava/lang/String;)V", "häl<!--<script>(I)V", "open()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).enter(110, 2).exit(150).enter(160, 3);
        w.chunk(7, 100, p.bytes(), false);
        final TraceSnapshot d = TestTraces.index(w);

        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final String init = ViewerJson.metaJson(d, sent, new ViewerJson.SentSessions());
        MiniJson.parse(init);
        assertFalse(init.contains("<"),
                "the HTML tokenizer treats <!--<script> inside a <script> block as double-escaped and swallows the"
                        + " real </script>, so no raw < may survive in the init payload");
        assertTrue(init.contains("\\u003c!--\\u003cscript>"), "the < is escaped as \\u003c, not dropped");
        assertTrue(sent.methods.get(1), "the session root's name rides with the init payload");
        assertTrue(init.contains("\"rootId\":1"), "the chart selects a session's root frame by its method id");
        assertTrue(init.contains("\"startEpochMs\":" + TestTraces.DEFAULT_START_EPOCH_MS + ",\"utcOffsetSeconds\":32400"),
                "the page derives wall-clock times from the file's anchor, in the server's offset");
        assertFalse(sent.methods.get(2) || sent.methods.get(3), "every other name is deferred to the first window that shows it");
        assertFalse(init.contains("},\"coverage\":["), "coverage lives in meta.threads, not at the top level");
        assertFalse(init.contains("\"root\":") || init.contains("\"thread\":"),
                "sessions carry ids only, and the page derives names from the names map and the thread list");

        final var res = WindowExtractor.extract(d, 0, 40_000, 1000, 1000);
        final String win = ViewerJson.windowJson(d, res, 42, sent);
        MiniJson.parse(win);
        assertTrue(win.contains("\"reqId\":42"));
        assertFalse(win.contains("\"hidden\""), "the page never reads a hidden array, so none is sent");
        assertFalse(win.contains("pkg.Root.root"), "already-sent names are not resent");
        assertTrue(win.contains("[2,") && win.contains("[3,"), "new names ride along with the window");
        final String win2 = ViewerJson.windowJson(d, res, 43, sent);
        assertTrue(win2.contains("\"methodNames\":[]"), "second window resends nothing");
    }

    @Test
    void queryPayloadsAreWellFormed() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "root()V", "kid(I)V");
        w.exception(1, "java.lang.IllegalStateException");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).enter(110, 2).exitThrow(150, 1).exit(300);
        w.chunk(7, 100, p.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final BitSet ids = new BitSet();
        ids.set(2);
        MiniJson.parse(ViewerJson.searchJson(9, ids, 5));

        final var m = MatchSearch.nextMatch(d, ids, 0);
        MiniJson.parse(ViewerJson.matchJson(10, m));
        MiniJson.parse(ViewerJson.matchJson(11, null));
    }

    @Test
    void windowCarriesExceptionIdsAndTheirNamesIncrementally() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "root()V", "a()V", "b()V", "c()V");
        w.exception(1, "java.sql.SQLException");
        w.exception(3, "pkg.Wrapped</script>Exception");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).enter(110, 2).exitThrow(150, 1).enter(160, 3).exitThrow(200, 0).enter(210, 4).exitThrow(250, 3).exitThrow(300, 3);
        w.chunk(7, 100, p.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final String init = ViewerJson.metaJson(d, sent, new ViewerJson.SentSessions());
        MiniJson.parse(init);
        assertTrue(init.contains("\"exceptionNames\":[]"), "sessions reference no exceptions, so init ships none");

        final var res = WindowExtractor.extract(d, 0, 40_000, 1000, 1000);
        final String win = ViewerJson.windowJson(d, res, 42, sent);
        MiniJson.parse(win);
        assertFalse(win.contains("<"), "exception names are escaped like method names");
        assertTrue(win.contains("\"exc\":[0,3,1,1,2,0,3,3]"),
                "frames are listed as index and exception id pairs in frame order so the page can label each throw");
        assertTrue(win.contains("[1,\"java.sql.SQLException\"]") && win.contains("[3,\"pkg.Wrapped"),
                "the names of the referenced exceptions ride with the first window that shows them");
        assertTrue(sent.exceptions.get(1) && sent.exceptions.get(3));
        assertFalse(sent.exceptions.get(0), "id 0 stands for an unknown class and has no name to send");
        final String win2 = ViewerJson.windowJson(d, res, 43, sent);
        assertTrue(win2.contains("\"exceptionNames\":[]"), "a second window resends no exception names");
    }

    @Test
    void anExceptionNameThatHasNotArrivedYetIsNotMarkedAsSent() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "root()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exitThrow(200, 5);
        w.chunk(7, 100, p.bytes(), true);
        final byte[] first = w.bytes();
        w.exception(5, "pkg.LateException");
        final byte[] all = w.bytes();

        final Path f = TestTraces.tempFile();
        TestTraces.append(f, all, 0, first.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final TraceSnapshot d1 = ix.snapshot();
        final String win1 = ViewerJson.windowJson(d1, WindowExtractor.extract(d1, 0, 40_000, 1000, 1000), 1, sent);
        assertTrue(win1.contains("\"exc\":[0,5]"), "the frame's exception id is known before its name");
        assertTrue(win1.contains("\"exceptionNames\":[]"));
        assertFalse(sent.exceptions.get(5), "an id whose name a live transfer has not delivered yet must stay unsent");

        TestTraces.append(f, all, first.length, all.length);
        ix.advance(ProgressListener.NONE);
        final TraceSnapshot d2 = ix.snapshot();
        final String win2 = ViewerJson.windowJson(d2, WindowExtractor.extract(d2, 0, 40_000, 1000, 1000), 2, sent);
        assertTrue(win2.contains("[5,\"pkg.LateException\"]"), "the next window that shows the frame brings the late name");
        assertTrue(sent.exceptions.get(5));
    }

    @Test
    void gcPausesRideWithTheInitPayloadAsAppendOnlyDeltas() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "root()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200);
        w.chunk(7, 100, p.bytes(), true);
        w.gc(150, 20, Vbtm.GC_ACTION_MINOR, "G1 Young Generation", "G1 Evacuation \"Pause\"");
        w.gc(90, 15, Vbtm.GC_ACTION_MAJOR, "G1 Old Generation", "System.gc()");
        final byte[] first = w.bytes();
        w.gc(190, 5, Vbtm.GC_ACTION_UNKNOWN, "ZGC Pauses", "Warmup");
        final byte[] all = w.bytes();

        final Path f = TestTraces.tempFile();
        TestTraces.append(f, all, 0, first.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        final ViewerJson.SentSessions cursor = new ViewerJson.SentSessions();
        final String init1 = ViewerJson.metaJson(ix.snapshot(), new ViewerJson.SentNames(), cursor);
        MiniJson.parse(init1);
        assertTrue(init1.contains("\"gc\":{\"reset\":true,\"totalNs\":3500,\"items\":[[15000,2000,1,\"G1 Young Generation\",\"G1 Evacuation \\\"Pause\\\"\"],[9000,1500,2,\"G1 Old Generation\",\"System.gc()\"]]}"),
                "the first payload carries every pause in file order with its labels inline, escaped like every other string: " + init1);

        final String init2 = ViewerJson.metaJson(ix.snapshot(), new ViewerJson.SentNames(), cursor);
        assertTrue(init2.contains("\"gc\":{\"reset\":false,\"totalNs\":3500,\"items\":[]}"),
                "a live tick with no new pause sends none again: " + init2);

        TestTraces.append(f, all, first.length, all.length);
        ix.advance(ProgressListener.NONE);
        final String init3 = ViewerJson.metaJson(ix.snapshot(), new ViewerJson.SentNames(), cursor);
        assertTrue(init3.contains("\"gc\":{\"reset\":false,\"totalNs\":4000,\"items\":[[19000,500,0,\"ZGC Pauses\",\"Warmup\"]]}"),
                "only the pause that arrived since the last tick is sent, so the page appends: " + init3);

        final String fresh = ViewerJson.metaJson(TestTraces.index(all, 1 << 20), new ViewerJson.SentNames(), cursor);
        assertTrue(fresh.contains("\"gc\":{\"reset\":true,\"totalNs\":4000,\"items\":[[15000,"),
                "a different index from a new generation resets the page's list along with the sessions: " + fresh);
    }

    @Test
    void coverIsPerThreadSoBandsShowWhichThreadIsActiveWhen() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "early");
        w.thread(2, "late");
        w.clazz(1, "pkg.A", "run()V");
        final TraceBuilder.Payload a = new TraceBuilder.Payload(100);
        a.enter(100, 1).exit(1000);
        w.chunk(1, 100, a.bytes(), true);
        final TraceBuilder.Payload b = new TraceBuilder.Payload(9100);
        b.enter(9100, 1).exit(10_000);
        w.chunk(2, 9100, b.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final String init = ViewerJson.metaJson(d, new ViewerJson.SentNames(), new ViewerJson.SentSessions());
        MiniJson.parse(init);
        final int[] early = expand(coverRle(init, 1));
        final int[] late = expand(coverRle(init, 2));
        assertEquals(100, early[0], "thread 1 saturates the first bucket");
        assertTrue(Arrays.stream(early, 800, 1600).allMatch(v -> v == 0), "thread 1 is idle in the second half");
        assertEquals(100, late[1599], "thread 2 saturates the last bucket");
        assertTrue(Arrays.stream(late, 0, 800).allMatch(v -> v == 0), "thread 2 is idle in the first half");
    }

    @Test
    void coverEncodingIsBoundedByActivityChangesNotBuckets() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "busy");
        w.thread(2, "blip");
        w.clazz(1, "pkg.A", "run()V");
        final TraceBuilder.Payload a = new TraceBuilder.Payload(100);
        a.enter(100, 1).exit(100_000);
        w.chunk(1, 100, a.bytes(), true);
        final TraceBuilder.Payload b = new TraceBuilder.Payload(50_000);
        b.enter(50_000, 1).exit(50_010);
        w.chunk(2, 50_000, b.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final String init = ViewerJson.metaJson(d, new ViewerJson.SentNames(), new ViewerJson.SentSessions());
        MiniJson.parse(init);
        assertArrayEquals(new int[] { 100, ViewerJson.COVERAGE_BUCKETS }, coverRle(init, 1),
                "a thread busy throughout is a single run");
        final int[] blip = coverRle(init, 2);
        assertTrue(blip.length <= 8, "a single short frame costs at most a few runs, got " + blip.length);
        assertTrue(Arrays.stream(expand(blip)).filter(v -> v != 0).count() <= 2,
                "the blip touches at most two buckets");
    }

    @Test
    void firstInitSendsEverySessionAndMarksReset() throws IOException {
        final TraceBuilder w = twoSessionsOneOpen();
        final TraceSnapshot d = TestTraces.index(w);
        assertFalse(d.sessions.get(1).ended, "precondition: session #2 is still open");

        final ViewerJson.SentSessions c = new ViewerJson.SentSessions();
        final String init = ViewerJson.metaJson(d, new ViewerJson.SentNames(), c);
        MiniJson.parse(init);
        assertTrue(init.contains("\"sessionsReset\":true"), "a fresh page needs the full list, so the first payload replaces");
        assertEquals(List.of(1, 2), seqs(init));
        assertTrue(session(init, 2).contains("\"unclosed\":true"));
        assertEquals(2, c.sent);
        assertEquals(BitSet.valueOf(new long[] { 0b10 }), c.open, "only the open session is a candidate for re-emission");
        assertEquals(d.generation, c.generation);
        assertTrue(c.lastReset());
    }

    @Test
    void laterInitSendsOnlyOpenAndNewSessions() throws IOException {
        final TraceBuilder w = twoSessionsOneOpen();
        final byte[] first = w.bytes();
        final TraceBuilder.Payload close = new TraceBuilder.Payload(500);
        close.exit(500);
        w.chunk(2, 500, close.bytes(), true);
        final TraceBuilder.Payload third = new TraceBuilder.Payload(600);
        third.enter(600, 3).exit(700);
        w.chunk(1, 600, third.bytes(), true);
        final byte[] all = w.bytes();

        final Path f = TestTraces.tempFile();
        TestTraces.append(f, all, 0, first.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final ViewerJson.SentSessions c = new ViewerJson.SentSessions();
        final String init1 = ViewerJson.metaJson(ix.snapshot(), sent, c);
        assertTrue(session(init1, 2).contains("\"unclosed\":true"), "precondition: session #2 is open on the first tick");

        TestTraces.append(f, all, first.length, all.length);
        ix.advance(ProgressListener.NONE);
        final String init2 = ViewerJson.metaJson(ix.snapshot(), sent, c);
        MiniJson.parse(init2);
        assertTrue(init2.contains("\"sessionsReset\":false"), "a growing recording patches the page's list instead of replacing it");
        assertEquals(List.of(2, 3), seqs(init2),
                "the long-ended session stays out, while the one that was open and the new one go");
        assertTrue(session(init2, 2).contains("\"unclosed\":false}"), "the page learns that the open session closed");
        assertTrue(session(init2, 2).contains("\"durNs\":20000,"), "and gets its final duration");
        assertTrue(init2.contains("[3,\"pkg.A.three()V\"]"), "the new root's name rides along");
        assertFalse(init2.contains("pkg.A.one()V"), "names already on the page are not resent");
        assertFalse(c.lastReset());
        assertEquals(3, c.sent);
        assertTrue(c.open.isEmpty());
    }

    @Test
    void anIdleTickSendsNoSessions() throws IOException {
        final TraceBuilder w = twoSessionsOneOpen();
        final Path f = TestTraces.tempFile();
        Files.write(f, w.bytes());
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final ViewerJson.SentSessions c = new ViewerJson.SentSessions();
        ViewerJson.metaJson(ix.snapshot(), sent, c);

        ix.advance(ProgressListener.NONE);
        final String init2 = ViewerJson.metaJson(ix.snapshot(), sent, c);
        MiniJson.parse(init2);
        assertTrue(init2.contains("\"sessionsReset\":false"));
        assertEquals(List.of(2), seqs(init2), "only the still-open session is refreshed while nothing else changed");
        assertTrue(init2.contains("\"methodNames\":[]"), "an idle tick ships no names");
    }

    @Test
    void aReplacedFileResendsEverySessionEvenIfItHasMore() throws IOException {
        final TraceBuilder big = TestTraces.writer();
        big.thread(1, "a-thread-name-long-enough-to-make-this-recording-the-larger-file");
        big.clazz(1, "pkg.with.a.rather.long.ClassName", "andALongMethodName(Ljava/lang/String;)V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200);
        big.chunk(1, 100, p.bytes(), true);
        big.end();
        final TraceBuilder small = TestTraces.writer();
        small.thread(1, "t");
        small.clazz(1, "P", "m()V");
        for (int k = 0; k < 3; k++) {
            final TraceBuilder.Payload q = new TraceBuilder.Payload(100 + k * 10);
            q.enter(100 + k * 10, 1).exit(105 + k * 10);
            small.chunk(1, 100 + k * 10, q.bytes(), true);
        }
        small.end();
        assertTrue(small.bytes().length < big.bytes().length, "precondition: the replacement is shorter but has more sessions");

        final Path f = TestTraces.tempFile();
        Files.write(f, big.bytes());
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final ViewerJson.SentSessions c = new ViewerJson.SentSessions();
        ViewerJson.metaJson(ix.snapshot(), sent, c);
        assertTrue(sent.methods.get(1), "precondition: the first recording's root name was shipped as id 1");

        Files.write(f, small.bytes());
        ix.advance(ProgressListener.NONE);
        final String init2 = ViewerJson.metaJson(ix.snapshot(), sent, c);
        MiniJson.parse(init2);
        assertTrue(init2.contains("\"sessionsReset\":true"),
                "a replaced file is a new recording: stale entries indexed by seq must not survive as its sessions");
        assertEquals(List.of(1, 2, 3), seqs(init2));
        assertTrue(init2.contains("[1,\"P.m()V\"]"), "id 1 now names a different method, so it is resent");
        assertTrue(c.lastReset());
    }

    @Test
    void aLateRootNameRidesWithTheTickThatReportsIt() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "a");
        w.clazz(1, "pkg.A", "one()V");
        w.chunk(1, 100, new byte[0], false);
        final byte[] first = w.bytes();
        final TraceBuilder.Payload p = new TraceBuilder.Payload(200);
        p.enter(200, 1).exit(300);
        w.chunk(1, 200, p.bytes(), true);
        final byte[] all = w.bytes();

        final Path f = TestTraces.tempFile();
        TestTraces.append(f, all, 0, first.length);
        final TraceIndexer ix = TraceIndexer.open(f, 1 << 20);
        ix.advance(ProgressListener.NONE);
        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final ViewerJson.SentSessions c = new ViewerJson.SentSessions();
        final String init1 = ViewerJson.metaJson(ix.snapshot(), sent, c);
        assertTrue(session(init1, 1).contains("\"rootId\":-1,"), "precondition: the session has no root yet");
        assertFalse(sent.methods.get(1));

        TestTraces.append(f, all, first.length, all.length);
        ix.advance(ProgressListener.NONE);
        final String init2 = ViewerJson.metaJson(ix.snapshot(), sent, c);
        MiniJson.parse(init2);
        assertTrue(session(init2, 1).contains("\"rootId\":1,"), "the open session is re-emitted with its root");
        assertTrue(init2.contains("[1,\"pkg.A.one()V\"]"), "and the chip label can be derived from the root's name");
    }

    private static TraceBuilder twoSessionsOneOpen() {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "a");
        w.thread(2, "b");
        w.clazz(1, "pkg.A", "one()V", "two()V", "three()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200);
        w.chunk(1, 100, p.bytes(), true);
        final TraceBuilder.Payload q = new TraceBuilder.Payload(300);
        q.enter(300, 2);
        w.chunk(2, 300, q.bytes(), false);
        return w;
    }

    private static String sessionsArray(final String init) {
        final int a = init.indexOf("\"sessions\":[");
        final int b = init.indexOf("],\"threads\":[", a);
        assertTrue(a >= 0 && b > a, "the sessions array is present");
        return init.substring(a, b + 1);
    }

    private static List<Integer> seqs(final String init) {
        final String arr = sessionsArray(init);
        final List<Integer> out = new ArrayList<>();
        int i = 0;
        while ((i = arr.indexOf("{\"seq\":", i)) >= 0) {
            i += 7;
            out.add(Integer.parseInt(arr.substring(i, arr.indexOf(',', i))));
        }
        return out;
    }

    private static String session(final String init, final int seq) {
        final String arr = sessionsArray(init);
        final int a = arr.indexOf("{\"seq\":" + seq + ",");
        assertTrue(a >= 0, "session #" + seq + " is emitted");
        return arr.substring(a, arr.indexOf('}', a) + 1);
    }

    private static int[] coverRle(final String init, final long tid) {
        final int at = init.indexOf("{\"tid\":" + tid + ",");
        assertTrue(at >= 0, "thread " + tid + " is listed");
        final String key = "\"coverage\":[";
        final int a = init.indexOf(key, at) + key.length();
        return Arrays.stream(init.substring(a, init.indexOf(']', a)).split(",")).mapToInt(Integer::parseInt).toArray();
    }

    private static int[] expand(final int[] rle) {
        final int[] out = new int[ViewerJson.COVERAGE_BUCKETS];
        int b = 0;
        for (int i = 0; i < rle.length; i += 2) {
            Arrays.fill(out, b, b + rle[i + 1], rle[i]);
            b += rle[i + 1];
        }
        assertEquals(out.length, b, "runs tile every bucket exactly once");
        return out;
    }

    static final class MiniJson {
        private final String s;

        private int i;

        private MiniJson(final String s) {
            this.s = s;
        }

        static void parse(final String s) {
            final MiniJson p = new MiniJson(s);
            p.value();
            p.ws();
            if (p.i != s.length()) {
                throw new IllegalArgumentException("trailing garbage at " + p.i);
            }
        }

        private void value() {
            ws();
            final char c = peek();
            switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> keyword("true");
                case 'f' -> keyword("false");
                case 'n' -> keyword("null");
                default -> number();
            }
        }

        private void object() {
            expect('{');
            ws();
            if (peek() == '}') {
                i++;
                return;
            }
            while (true) {
                ws();
                string();
                ws();
                expect(':');
                value();
                ws();
                final char c = next();
                if (c == '}') {
                    return;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected , or } at " + (i - 1));
                }
            }
        }

        private void array() {
            expect('[');
            ws();
            if (peek() == ']') {
                i++;
                return;
            }
            while (true) {
                value();
                ws();
                final char c = next();
                if (c == ']') {
                    return;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected , or ] at " + (i - 1));
                }
            }
        }

        private void string() {
            expect('"');
            while (true) {
                final char c = next();
                if (c == '"') {
                    return;
                }
                if (c == '\\') {
                    final char e = next();
                    if (e == 'u') {
                        for (int k = 0; k < 4; k++) {
                            final char h = next();
                            if (Character.digit(h, 16) < 0) {
                                throw new IllegalArgumentException("bad \\u at " + i);
                            }
                        }
                    } else if ("\"\\/bfnrt".indexOf(e) < 0) {
                        throw new IllegalArgumentException("bad escape \\" + e + " at " + i);
                    }
                } else if (c < 0x20) {
                    throw new IllegalArgumentException("raw control char at " + (i - 1));
                }
            }
        }

        private void number() {
            final int start = i;
            if (peek() == '-') {
                i++;
            }
            while (i < s.length() && "0123456789+-.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (i == start) {
                throw new IllegalArgumentException("expected a value at " + start);
            }
            Double.parseDouble(s.substring(start, i));
        }

        private void keyword(final String kw) {
            if (!s.startsWith(kw, i)) {
                throw new IllegalArgumentException("expected " + kw + " at " + i);
            }
            i += kw.length();
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw new IllegalArgumentException("unexpected end");
            }
            return s.charAt(i);
        }

        private char next() {
            final char c = peek();
            i++;
            return c;
        }

        private void expect(final char c) {
            if (next() != c) {
                throw new IllegalArgumentException("expected " + c + " at " + (i - 1));
            }
        }
    }
}
