package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.SelectedCall.Ancestor;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;

final class SelectedCallTest {

    @Test
    void ancestorsArriveAsFlatTriplesRootFirstAndGetTheirRealDepths() {
        final Object[] raw = { 10_000.0, 20_000.0, 1.0, 10_500.0, 3_000.0, 4.0 };
        assertEquals(List.of(new Ancestor(10_000, 20_000, 0, 1), new Ancestor(10_500, 3_000, 1, 4)),
                SelectedCall.parseAncestors(raw, 2), "the page sends doubles, and the chain ends one above the frame");
    }

    @Test
    void aChainCutShortByTheLoadedWindowKeepsRealDepths() {
        final Object[] raw = { 10_500.0, 3_000.0, 4.0 };
        assertEquals(List.of(new Ancestor(10_500, 3_000, 4, 4)), SelectedCall.parseAncestors(raw, 5),
                "only the parent was in the window, so the chain starts at depth 4, not 0");
    }

    @Test
    void chainEndsWithTheFrameItself() {
        final SelectedCall f = new SelectedCall(7, 10_500, 3_000, 1_000, 1, 4, -1, false,
                List.of(new Ancestor(10_000, 20_000, 0, 1)), null, null);
        assertEquals(List.of(new Ancestor(10_000, 20_000, 0, 1), new Ancestor(10_500, 3_000, 1, 4)), f.pathFromRoot());
        assertEquals(List.of(new Ancestor(10_500, 3_000, 1, 4)),
                new SelectedCall(7, 10_500, 3_000, 1_000, 1, 4, -1, false, List.of(), null, null).pathFromRoot(),
                "a frame whose parents all lie outside the loaded window still yields itself");
    }

    @Test
    void thrownIsDerivedFromTheExceptionIdSoAnUnknownClassStillCountsAsAThrow() {
        assertEquals(false, new SelectedCall(7, 1, 2, 3, 0, 4, -1, false, List.of(), null, null).thrown(),
                "-1 means the call returned normally");
        assertEquals(true, new SelectedCall(7, 1, 2, 3, 0, 4, 0, false, List.of(), null, null).thrown(),
                "0 is a throw whose class the agent could not record, and the frame is still marked as thrown");
        assertEquals(true, new SelectedCall(7, 1, 2, 3, 0, 4, 12, false, List.of(), null, null).thrown());
    }

    @Test
    void malformedChainsDegradeToNoAncestorsInsteadOfFailingTheSelection() {
        assertEquals(List.of(), SelectedCall.parseAncestors(null, 3));
        assertEquals(List.of(), SelectedCall.parseAncestors("1,2,3", 3));
        assertEquals(List.of(), SelectedCall.parseAncestors(new Object[] { 1.0, "x", 3.0 }, 3));
        assertEquals(List.of(new Ancestor(1, 2, 2, 3)), SelectedCall.parseAncestors(new Object[] { 1.0, 2.0, 3.0, 9.0 }, 3),
                "a dangling element is ignored rather than breaking the whole chain");
    }

    @Test
    void aFoundAggregateReplacesTheChartSelfTimeAndClearsAnEarlierError() throws IOException {
        final SelectedCall f = new SelectedCall(1, 10_000, 10_000, 10_000, 0, 0, -1, false, List.of(), null, null)
                .withSubtreeError("Aggregation failed: boom");
        final SelectedCall done = f.withSubtree(SubtreeAggregate.compute(rootWithChild(), 1, 10_000, 10_000, 0, 0));
        assertNull(done.subtreeError(), "the views treat subtree and subtreeError as exclusive, so a stale error must go");
        assertEquals(6_000, done.effectiveSelfNs(),
                "the aggregate knows the children the chart window may have dropped, so its self wins");
    }

    @Test
    void aNotFoundAggregateBecomesAnErrorInsteadOfAPlaceholderTree() throws IOException {
        final SelectedCall f = new SelectedCall(1, 50_000, 1_000, 400, 0, 0, -1, false, List.of(), null, null);
        final SubtreeAggregate agg = SubtreeAggregate.compute(rootWithChild(), 1, 50_000, 1_000, 0, 0);
        assertEquals(false, agg.found());
        final SelectedCall done = f.withSubtree(agg);
        assertNull(done.subtree(), "a placeholder root would draw as a one-row tree with self == total == durNs");
        assertSame(SelectedCall.NOT_FOUND, done.subtreeError());
        assertEquals(400, done.effectiveSelfNs(), "Call Details keeps the chart self rather than the seeded durNs");
    }

    private static TraceSnapshot rootWithChild() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "root()V", "child()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0).enter(110, 1).exit(150).exit(200);
        w.chunk(1, 100, p.bytes(), true);
        return TestTraces.index(w);
    }
}
