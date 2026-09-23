package io.github.yagipass.verbatime.jmc.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;
import io.github.yagipass.verbatime.jmc.views.BottomUpModel.Row;
import io.github.yagipass.verbatime.jmc.views.BottomUpModel.SortKey;

final class BottomUpModelTest {

    private static TraceSnapshot fixture() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "root()V", "a()V", "b()V", "never()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).enter(110, 2).exit(150).enter(160, 2).exit(200).enter(210, 3).exit(220).exit(300);
        w.chunk(7, 100, p.bytes(), true);
        return TestTraces.index(w);
    }

    private static SubtreeAggregate rootOf(final TraceSnapshot d) {
        return SubtreeAggregate.compute(d, 7, 10_000, 20_000, 0, 1);
    }

    private static BottomUpModel model(final TraceSnapshot d) {
        return BottomUpModel.of(d, rootOf(d));
    }

    private static List<String> names(final BottomUpModel m) {
        return m.rows().stream().map(Row::name).map(Formats::shortName).toList();
    }

    @Test
    void listsEveryMethodOfTheSubtreeAndMatchesTheIndexerOverTheWholeTrace() throws IOException {
        final TraceSnapshot d = fixture();
        final BottomUpModel m = model(d);
        assertEquals(3, m.size(), "a registered but never-called method is not a row");
        assertEquals(List.of("Root.root", "Root.a", "Root.b"), names(m), "default order is self time, descending");

        final Row root = m.row(0);
        assertEquals(1, root.calls());
        assertEquals(20_000, root.totalNs());
        assertEquals(11_000, root.selfNs(), "self excludes the 9 µs spent in a, a and b");
        final Row a = m.row(1);
        assertEquals(2, a.calls());
        assertEquals(8_000, a.totalNs());
        assertEquals(8_000, a.selfNs());
        for (final Row r : m.rows()) {
            assertEquals(d.callsByMethod[r.methodId()], r.calls(), "root's subtree is the whole trace, so the indexer agrees");
            assertEquals(d.methodName(r.methodId()), r.name());
        }
    }

    @Test
    void copyTextMirrorsTheTableColumnsAndFollowsTheCurrentSort() throws IOException {
        final BottomUpModel m = model(fixture());
        assertEquals("bottom-up of Root.root(), total 20.00 µs, 3 methods, by self descending\n"
                + "      self       total       %     calls  method\n"
                + "  11.00 µs    20.00 µs     55%         1  pkg.Root.root()V\n"
                + "   8.00 µs     8.00 µs     40%         2  pkg.Root.a()V\n"
                + "   1.00 µs     1.00 µs    5.0%         1  pkg.Root.b()V", CopyTexts.bottomUpText(m, r -> false),
                "what is copied is what the table shows, same columns in the same order, with full names for grepping");
        m.toggleSort(SortKey.METHOD);
        assertTrue(CopyTexts.bottomUpText(m, r -> false)
                .startsWith("bottom-up of Root.root(), total 20.00 µs, 3 methods, by method descending\n"),
                "the header states the order so a pasted table is self-describing");
    }

    @Test
    void headerClicksFlipTheDirectionOrSwitchTheColumn() throws IOException {
        final BottomUpModel m = model(fixture());
        assertEquals(SortKey.SELF, m.sortKey());
        assertTrue(m.descending());

        m.toggleSort(SortKey.SELF);
        assertFalse(m.descending(), "the same column again flips the direction");
        assertEquals(List.of("Root.b", "Root.a", "Root.root"), names(m));

        m.toggleSort(SortKey.CALLS);
        assertTrue(m.descending(), "a new column starts descending");
        assertEquals(List.of("Root.a", "Root.root", "Root.b"), names(m), "equal call counts keep method-id order");

        m.toggleSort(SortKey.METHOD);
        assertEquals(List.of("Root.root", "Root.b", "Root.a"), names(m));
        m.toggleSort(SortKey.METHOD);
        assertEquals(List.of("Root.a", "Root.b", "Root.root"), names(m));
    }

    @Test
    void aRebuiltModelKeepsTheSortOfTheOneItReplaces() throws IOException {
        final BottomUpModel before = model(fixture());
        before.toggleSort(SortKey.CALLS);
        before.toggleSort(SortKey.CALLS);
        assertFalse(before.descending(), "calls, ascending: not the default order");
        final TraceSnapshot d = fixture();
        final BottomUpModel after = BottomUpModel.of(d, rootOf(d), before.sortKey(), before.descending());
        assertEquals(names(before), names(after), "a live reload must not reset the user's sort");
    }

    private static BottomUpModel diamond() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.Root", "root()V", "a()V", "b()V", "c()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0).enter(110, 1).enter(120, 2).exit(130).exit(140);
        p.enter(150, 3).enter(160, 2).exit(170).exit(180).exit(200);
        w.chunk(1, 100, p.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);
        final SubtreeAggregate a = SubtreeAggregate.compute(d, 1, 10_000, 10_000, 0, 0);
        return BottomUpModel.of(d, a);
    }

    private static Row named(final BottomUpModel m, final String shortName) {
        for (final Row r : m.rows()) {
            if (Formats.shortName(r.name()).equals(shortName)) {
                return r;
            }
        }
        throw new AssertionError("no row " + shortName);
    }

    private static Row childNamed(final Row r, final String shortName) {
        for (int i = 0; i < r.childCount(); i++) {
            if (Formats.shortName(r.child(i).name()).equals(shortName)) {
                return r.child(i);
            }
        }
        throw new AssertionError("no child " + shortName + " of " + r.name());
    }

    @Test
    void subtreeFlattensAMethodReachedThroughSeveralCallers() throws IOException {
        final BottomUpModel m = diamond();
        final Row b = named(m, "Root.b");
        assertEquals(2, b.calls(), "b is called once on each of the two paths");
        assertEquals(2_000, b.totalNs(), "the flat b row sums both calls");
        assertTrue(b.hasChildren(), "b has callers to expand");
        assertEquals(20.0, m.pct(b.selfNs()), 1e-9, "b's self is a fifth of the 10 µs subtree");
    }

    @Test
    void expandingBottomUpWalksUpTheCallers() throws IOException {
        final BottomUpModel m = diamond();
        final Row b = named(m, "Root.b");
        assertEquals(2, b.childCount(), "b's callers are a and c");
        final Row viaA = childNamed(b, "Root.a");
        final Row viaC = childNamed(b, "Root.c");
        assertEquals(1_000, viaA.totalNs(), "the caller row carries b's time reached through a");
        assertEquals(1_000, viaC.totalNs(), "the caller row carries b's time reached through c");
        assertEquals(b.calls(), viaA.calls() + viaC.calls(), "the caller rows sum back to the callee row");
        assertEquals(b.totalNs(), viaA.totalNs() + viaC.totalNs(), "no time is lost splitting b across its callers");

        final Row root = childNamed(viaA, "Root.root");
        assertFalse(root.hasChildren(), "the subtree root is the top of every caller chain");
    }

    @Test
    void resortingKeepsTheCachedCallerRowObjects() throws IOException {
        final BottomUpModel m = diamond();
        final Row b = named(m, "Root.b");
        final Row viaA = childNamed(b, "Root.a");
        m.toggleSort(SortKey.CALLS);
        assertSame(viaA, childNamed(b, "Root.a"),
                "re-sorting reorders the cached children in place so the tree keeps its expansion");
    }
}
