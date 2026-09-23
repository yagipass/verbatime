package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParsedExport {

    static final Pattern BODY = Pattern.compile("^(\\d+\\.\\d{4}) +(\\d+\\.\\d{4}) +(\\d+) +(?:·(\\d+) calls <(\\S+), "
            + "(\\d+) incl\\. nested(?: \\[!(\\d+)\\])?: (.*)|(\\S+)(?: +self +(\\d+\\.\\d{4}))?( +!(?:e(\\d+))?)?( +~)?) *$");

    static final Pattern OUTLINE = Pattern.compile("^L(\\d+) +(\\d+\\.\\d{4}) +(\\d+\\.\\d{4}) +(\\d+\\.\\d)% {2}( *)(\\S+)"
            + "(?: self (\\d+\\.\\d{4}))?(?: hidden (\\d+) calls (\\d+\\.\\d{4}))?( !(?:e(\\d+))?)?( ~)? {2}\\[(\\d+) lines\\]$");

    static final Pattern HOT = Pattern.compile("^ *(\\d+\\.\\d{4}) +(\\d+\\.\\d{4}) +(\\d+) {2}(\\S+)$");

    static final Pattern CONTENTS = Pattern.compile("^L(\\d+)-L(\\d+) {2,}(\\S.*)$");

    static final int LINE_FILE = 3;

    static final int LINE_SESSION = 4;

    static final int LINE_DURATION = 5;

    static final int LINE_GC = 7;

    static final int LINE_FLOOR = 8;

    static final Pattern GC = Pattern.compile("^(\\d+\\.\\d{4}) (\\d+\\.\\d{4}) (minor|major|pause) (\\S.*): (\\S.*)$");

    record GcRow(long start, long dur) {
    }

    record BodyLine(long abs, long start, long dur, int depth, boolean accounting, String name, long self,
            boolean thrown, long excNo, boolean unclosed, long belowFloorCalls, String floor, long tinyNested, long tinyThrown,
            List<String[]> list, String raw) {

        boolean hasSelf() {
            return self >= 0;
        }
    }

    record OutlineRow(long anchor, long start, long dur, String pct, int depth, String name, long self, long hidden,
            long hiddenDur, boolean thrown, long excNo, boolean unclosed, long subLines, String raw) {
    }

    record HotRow(long self, long total, long calls, String name) {
    }

    final List<String> lines;

    final Map<String, String> methods = new LinkedHashMap<>();

    final Map<String, String> exceptions = new LinkedHashMap<>();

    final long outlineStart;

    final long bodyStart;

    final List<GcRow> gc = new ArrayList<>();

    final List<BodyLine> body = new ArrayList<>();

    final List<OutlineRow> outline = new ArrayList<>();

    final List<HotRow> hotBySelf = new ArrayList<>();

    final List<HotRow> hotByCalls = new ArrayList<>();

    private ParsedExport(final List<String> lines) {
        this.lines = lines;
        assertEquals("# verbatime session export v1", line(1));
        assertEquals("", line(2), "blank line after the title");
        assertTrue(line(LINE_GC).startsWith("gc: "), line(LINE_GC));
        assertEquals("", line(15), "blank line after the header");
        assertEquals("## contents", line(16));
        assertEquals("", line(17), "blank line after the contents heading");
        final long[][] r = new long[7][];
        final String[] what = new String[7];
        for (int i = 0; i < 7; i++) {
            final Matcher m = CONTENTS.matcher(line(18 + i));
            assertTrue(m.matches(), "contents row L" + (18 + i) + ": " + line(18 + i));
            r[i] = new long[] { Long.parseLong(m.group(1)), Long.parseLong(m.group(2)) };
            what[i] = m.group(3);
        }
        assertEquals("", line(25), "blank line after the contents");
        outlineStart = r[0][0];
        final long outlineEnd = r[0][1];
        final long hotSelfStart = r[1][0];
        final long hotSelfEnd = r[1][1];
        final long hotCallsStart = r[2][0];
        final long hotCallsEnd = r[2][1];
        final long methodsStart = r[3][0];
        final long methodsEnd = r[3][1];
        final long exceptionsStart = r[4][0];
        final long exceptionsEnd = r[4][1];
        final long gcStart = r[5][0];
        final long gcEnd = r[5][1];
        bodyStart = r[6][0];
        final long bodyEnd = r[6][1];
        assertEquals(26, outlineStart, "the outline follows the contents");
        assertEquals(bodyEnd, lines.size(), "the contents cover the whole file");

        assertTrue(line(outlineStart).startsWith("## outline: nodes >= "), line(outlineStart));
        assertEquals("", line(outlineStart + 1), "blank line after the outline heading");
        for (long l = outlineStart + 2; l <= outlineEnd; l++) {
            final Matcher m = OUTLINE.matcher(line(l));
            assertTrue(m.matches(), "outline row L" + l + ": " + line(l));
            outline.add(new OutlineRow(Long.parseLong(m.group(1)), ticks(m.group(2)), ticks(m.group(3)), m.group(4),
                    m.group(5).length(), m.group(6), m.group(7) == null ? -1 : ticks(m.group(7)),
                    m.group(8) == null ? 0 : Long.parseLong(m.group(8)), m.group(9) == null ? 0 : ticks(m.group(9)),
                    m.group(10) != null, m.group(11) == null ? 0 : Long.parseLong(m.group(11)), m.group(12) != null,
                    Long.parseLong(m.group(13)), line(l)));
        }
        assertEquals("", line(outlineEnd + 1), "blank line after the outline");
        assertTrue(what[0].startsWith("outline: the " + grouped(outline.size()) + " longest calls"), what[0]);

        hot(hotSelfStart, hotSelfEnd, "## hot methods by self, top ", hotBySelf);
        assertTrue(what[1].startsWith("hot methods by self: " + grouped(hotBySelf.size()) + " methods"), what[1]);
        hot(hotCallsStart, hotCallsEnd, "## hot methods by calls, top ", hotByCalls);
        assertTrue(what[2].startsWith("hot methods by calls: " + grouped(hotByCalls.size()) + " methods"), what[2]);

        assertTrue(line(methodsStart).startsWith("## methods: "), line(methodsStart));
        assertEquals("", line(methodsStart + 1), "blank line after the methods heading");
        for (long l = methodsStart + 2; l <= methodsEnd; l++) {
            final String s = line(l);
            final int eq = s.indexOf(" = ");
            assertTrue(eq > 0, "methods row L" + l + ": " + s);
            final String prev = methods.put(s.substring(0, eq), s.substring(eq + 3));
            assertEquals(null, prev, "display names are unique: " + s);
        }
        assertEquals("", line(methodsEnd + 1), "blank line after the methods");
        assertTrue(what[3].startsWith("methods: " + grouped(methods.size()) + " short names"), what[3]);

        assertTrue(line(exceptionsStart).startsWith("## exceptions: "), line(exceptionsStart));
        assertEquals("", line(exceptionsStart + 1), "blank line after the exceptions heading");
        for (long l = exceptionsStart + 2; l <= exceptionsEnd; l++) {
            final String s = line(l);
            final int eq = s.indexOf(" = ");
            assertTrue(eq > 0, "exceptions row L" + l + ": " + s);
            assertEquals("e" + (exceptions.size() + 1), s.substring(0, eq), "exception numbers are dense and in order: " + s);
            exceptions.put(s.substring(0, eq), s.substring(eq + 3));
        }
        assertEquals("", line(exceptionsEnd + 1), "blank line after the exceptions");
        assertTrue(what[4].startsWith("exceptions: " + grouped(exceptions.size()) + " classes thrown"), what[4]);

        assertTrue(line(gcStart).startsWith("## gc pauses: "), line(gcStart));
        assertEquals("", line(gcStart + 1), "blank line after the gc pauses heading");
        long prevStart = -1;
        for (long l = gcStart + 2; l <= gcEnd; l++) {
            final Matcher m = GC.matcher(line(l));
            assertTrue(m.matches(), "gc row L" + l + ": " + line(l));
            final GcRow row = new GcRow(ticks(m.group(1)), ticks(m.group(2)));
            assertTrue(row.start >= prevStart, "gc rows are in start order: " + line(l));
            assertTrue(row.dur > 0, "a listed pause has time inside the session: " + line(l));
            prevStart = row.start;
            gc.add(row);
        }
        assertEquals("", line(gcEnd + 1), "blank line after the gc pauses");
        assertTrue(what[5].startsWith("gc pauses: " + grouped(gc.size()) + " stop-the-world pauses"), what[5]);
        assertTrue(line(LINE_GC).startsWith("gc: " + grouped(gc.size()) + (gc.size() == 1 ? " pause" : " pauses") + " in this session, "),
                "the header's pause count is the table's: " + line(LINE_GC));

        assertTrue(line(bodyStart).startsWith("## body: "), line(bodyStart));
        if (bodyEnd > bodyStart) {
            assertEquals("", line(bodyStart + 1), "blank line after the body heading");
            for (long l = bodyStart + 2; l <= bodyEnd; l++) {
                body.add(parseBody(l, line(l)));
            }
        }
        assertEquals("body: " + grouped(body.size()) + " lines, one call per line in time order", what[6]);
        for (final BodyLine b : body) {
            if (b.excNo > 0) {
                assertTrue(exceptions.containsKey("e" + b.excNo), "every !eN on a body line is in the table: " + b.raw);
            }
        }
    }

    private void hot(final long start, final long end, final String heading, final List<HotRow> target) {
        assertTrue(line(start).startsWith(heading), line(start));
        assertEquals("", line(start + 1), "blank line after the heading at L" + start);
        assertTrue(line(start + 2).trim().startsWith("self_ms "), line(start + 2));
        for (long l = start + 3; l <= end; l++) {
            final Matcher m = HOT.matcher(line(l));
            assertTrue(m.matches(), "hot row L" + l + ": " + line(l));
            target.add(new HotRow(ticks(m.group(1)), ticks(m.group(2)), Long.parseLong(m.group(3)), m.group(4)));
        }
        assertEquals("", line(end + 1), "blank line after the table ending at L" + end);
    }

    private static String grouped(final long v) {
        return String.format(java.util.Locale.US, "%,d", v);
    }

    long bodyLineOf(final long anchor) {
        return anchor - bodyStart - 1;
    }

    BodyLine bodyAt(final long anchor) {
        return body.get((int) bodyLineOf(anchor) - 1);
    }

    static ParsedExport read(final Path file) throws IOException {
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\n"), "file ends with a newline");
        assertEquals(-1, text.indexOf('?'), "no placeholder was left unpatched");
        return new ParsedExport(List.of(text.substring(0, text.length() - 1).split("\n", -1)));
    }

    String line(final long oneBased) {
        return lines.get((int) (oneBased - 1));
    }

    static long ticks(final String ms) {
        return Long.parseLong(ms.replace(".", ""));
    }

    private static BodyLine parseBody(final long abs, final String s) {
        final Matcher m = BODY.matcher(s);
        if (!m.matches()) {
            fail("body line L" + abs + " does not match the grammar: " + s);
        }
        final long start = ticks(m.group(1));
        final long dur = ticks(m.group(2));
        final int depth = Integer.parseInt(m.group(3));
        if (m.group(4) != null) {
            final List<String[]> list = new ArrayList<>();
            for (final String item : m.group(8).split(", ", -1)) {
                final int x = item.lastIndexOf('×');
                list.add(x < 0 ? new String[] { item, "1" } : new String[] { item.substring(0, x), item.substring(x + 1) });
            }
            return new BodyLine(abs, start, dur, depth, true, null, -1, false, 0, false, Long.parseLong(m.group(4)),
                    m.group(5), Long.parseLong(m.group(6)), m.group(7) == null ? 0 : Long.parseLong(m.group(7)), list,
                    s);
        }
        return new BodyLine(abs, start, dur, depth, false, m.group(9), m.group(10) == null ? -1 : ticks(m.group(10)),
                m.group(11) != null, m.group(12) == null ? 0 : Long.parseLong(m.group(12)), m.group(13) != null, 0,
                null, 0, 0, List.of(), s);
    }

    String fullName(final String display) {
        final String full = methods.get(display);
        assertTrue(full != null, "display name in the methods table: " + display);
        return full;
    }

    String exceptionClass(final long excNo) {
        if (excNo <= 0) {
            return null;
        }
        final String cls = exceptions.get("e" + excNo);
        assertTrue(cls != null, "exception number in the exceptions table: e" + excNo);
        return cls;
    }

    void checkInvariants(final String ctx) {
        final Deque<long[]> stack = new ArrayDeque<>();
        for (final BodyLine b : body) {
            while (!stack.isEmpty() && stack.peek()[0] >= b.depth) {
                closeTop(stack, ctx);
            }
            if (b.accounting) {
                assertTrue(!stack.isEmpty() && stack.peek()[0] == b.depth - 1, ctx + " accounting line under its parent: " + b.raw);
                stack.peek()[3] += b.dur;
                assertEquals(stack.peek()[4], b.start, ctx + " accounting line starts with its parent: " + b.raw);
                continue;
            }
            if (!stack.isEmpty()) {
                assertEquals(stack.peek()[0] + 1, b.depth, ctx + " depth grows by one: " + b.raw);
                stack.peek()[3] += b.dur;
                assertTrue(b.start >= stack.peek()[4], ctx + " child starts after its parent: " + b.raw);
            } else {
                assertEquals(0, b.depth, ctx + " a top-level line has depth 0: " + b.raw);
            }
            stack.push(new long[] { b.depth, b.dur, b.self, 0, b.start, b.abs });
        }
        while (!stack.isEmpty()) {
            closeTop(stack, ctx);
        }
    }

    private static void closeTop(final Deque<long[]> stack, final String ctx) {
        final long[] t = stack.pop();
        if (t[2] >= 0) {
            assertEquals(t[1], t[2] + t[3], ctx + " L" + t[5] + ": dur = self + children + accounting");
        } else {
            assertEquals(0, t[3], ctx + " L" + t[5] + ": a line without self has no children");
        }
    }
}
