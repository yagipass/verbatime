package io.github.yagipass.verbatime.jmc.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;
import io.github.yagipass.verbatime.jmc.views.TopDownModel.Row;

final class TopDownModelTest {

    private static TraceSnapshot trace() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.Root", "root()V", "a()V", "b()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0).enter(110, 2).exit(120).enter(120, 1).exit(160).exit(200);
        w.chunk(1, 100, p.bytes(), true);
        return TestTraces.index(w);
    }

    private static SubtreeAggregate rootOf(final TraceSnapshot d) {
        return SubtreeAggregate.compute(d, 1, 10_000, 10_000, 0, 0);
    }

    private static TopDownModel fixture() throws IOException {
        final TraceSnapshot d = trace();
        return TopDownModel.of(d, rootOf(d));
    }

    @Test
    void aggregateIsTheOneItWasBuiltFromSoTheViewCanSkipARebuild() throws IOException {
        final TraceSnapshot d = trace();
        final SubtreeAggregate a = rootOf(d);
        assertSame(a, TopDownModel.of(d, a).aggregate(),
                "AggregateTreeView compares the selection's aggregate by identity to decide whether the rows are current");
    }

    @Test
    void theRootRowIsTheSelectedFrame() throws IOException {
        final Row root = fixture().root();
        assertEquals(0, root.node(), "node 0 is the selected frame");
        assertEquals(10_000, root.totalNs(), "the root total is the frame duration");
        assertEquals(2, root.childCount(), "the root has its two direct children");
    }

    @Test
    void childrenAreHeaviestFirst() throws IOException {
        final TopDownModel m = fixture();
        final Row first = m.child(m.root().node(), 0);
        final Row second = m.child(m.root().node(), 1);
        assertEquals("pkg.Root.a()V", first.name(), "the 4 µs child a sorts before the 1 µs child b");
        assertEquals("pkg.Root.b()V", second.name(), "the lighter child follows");
    }

    @Test
    void percentIsShareOfTheRootTotal() throws IOException {
        final TopDownModel m = fixture();
        assertEquals("100%", Formats.fmtPct(m.pct(m.root().totalNs())), "the root is the whole subtree");
        final Row a = m.child(m.root().node(), 0);
        assertEquals("40%", Formats.fmtPct(m.pct(a.totalNs())), "child a is 4 µs of the 10 µs subtree");
    }

    @Test
    void parentRoundTrips() throws IOException {
        final TopDownModel m = fixture();
        final Row a = m.child(m.root().node(), 0);
        assertEquals(m.root().node(), m.parent(a).node(), "a child's parent is the row it came from");
        assertNull(m.parent(m.root()), "the root has no parent");
    }

    @Test
    void copyTextIndentsTheExpandedTree() throws IOException {
        final TopDownModel m = fixture();
        assertEquals("top-down of Root.root(), total 10.00 µs, 3 call paths\n"
                + "      self       total       %     calls  method\n"
                + "   5.00 µs    10.00 µs    100%         1  pkg.Root.root()V\n"
                + "   4.00 µs     4.00 µs     40%         1    pkg.Root.a()V\n"
                + "   1.00 µs     1.00 µs     10%         1    pkg.Root.b()V", CopyTexts.topDownText(m, r -> true),
                "the fully expanded tree is copied with two spaces of indent per level");
    }
}
