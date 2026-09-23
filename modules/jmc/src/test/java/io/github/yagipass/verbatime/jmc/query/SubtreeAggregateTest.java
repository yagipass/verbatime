package io.github.yagipass.verbatime.jmc.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.jmc.index.RandomTraces;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder.Call;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;

final class SubtreeAggregateTest {

    @Test
    void subtreeMatchesBruteForce() throws IOException {
        for (long seed = 1; seed <= 6; seed++) {
            final byte[] bytes = RandomTraces.random(seed);
            final ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
            final int[] parentIdx = reconstructParents(ref);
            final Random rng = new Random(seed * 29);
            for (final int budget : new int[] { 1 << 30, 200 }) {
                final TraceSnapshot data = TestTraces.index(bytes, budget);
                for (int pick = 0; pick < 25; pick++) {
                    final int fIdx = canonicalIndex(ref, rng.nextInt(ref.calls.size()));
                    final Call f = ref.calls.get(fIdx);
                    final String ctx = "seed " + seed + " budget " + budget + " frame " + f;
                    final SubtreeAggregate agg = SubtreeAggregate.compute(data, f.tid(), f.startNs(), f.durNs(),
                            f.depth(), f.methodId());

                    assertTrue(agg.found(), ctx + " should locate the selected frame");
                    assertFalse(agg.truncated(), ctx + " should not truncate under the default node cap");
                    assertEquals(f.durNs(), agg.totalNs(0), ctx + " root total equals the frame duration");
                    assertEquals(1, agg.calls(0), ctx + " the root is a single call");

                    long selfSum = 0;
                    for (int node = 0; node < agg.nodeCount(); node++) {
                        selfSum += agg.selfNs(node);
                        long childTotal = 0;
                        for (int i = 0; i < agg.childCount(node); i++) {
                            final int c = agg.child(node, i);
                            childTotal += agg.totalNs(c);
                            assertEquals(node, agg.parent(c), ctx + " child " + c + " points back at its parent");
                        }
                        assertEquals(agg.totalNs(node), agg.selfNs(node) + childTotal,
                                ctx + " node " + node + " total splits into self plus children");
                    }
                    assertEquals(f.durNs(), selfSum, ctx + " self over the whole subtree sums to the frame duration");

                    final Map<Integer, long[]> per = bruteForce(ref, parentIdx, fIdx);
                    for (final Map.Entry<Integer, long[]> e : per.entrySet()) {
                        final int methodId = e.getKey();
                        assertEquals(e.getValue()[0], agg.methodCalls(methodId), ctx + " calls for method " + methodId);
                        assertEquals(e.getValue()[1], agg.methodTotalNs(methodId), ctx + " total for method " + methodId);
                        assertEquals(e.getValue()[2], agg.methodSelfNs(methodId), ctx + " self for method " + methodId);
                        long nodeTotal = 0;
                        long nodeCalls = 0;
                        for (int i = 0; i < agg.methodNodeCount(methodId); i++) {
                            nodeTotal += agg.totalNs(agg.methodNode(methodId, i));
                            nodeCalls += agg.calls(agg.methodNode(methodId, i));
                        }
                        assertEquals(e.getValue()[1], nodeTotal, ctx + " method " + methodId + " nodes sum to its total");
                        assertEquals(e.getValue()[0], nodeCalls, ctx + " method " + methodId + " nodes sum to its calls");
                    }
                    for (int methodId = 0; methodId < agg.methodIdLimit(); methodId++) {
                        if (!per.containsKey(methodId)) {
                            assertEquals(0, agg.methodCalls(methodId),
                                    ctx + " method " + methodId + " outside the subtree has no calls");
                        }
                    }
                }
            }
        }
    }

    private static int canonicalIndex(final ReferenceDecoder.Result ref, final int picked) {
        final Call p = ref.calls.get(picked);
        for (int i = 0; i < ref.calls.size(); i++) {
            final Call c = ref.calls.get(i);
            if (c.tid() == p.tid() && c.startNs() == p.startNs() && c.depth() == p.depth()
                    && c.methodId() == p.methodId()) {
                return i;
            }
        }
        return picked;
    }

    private static int[] reconstructParents(final ReferenceDecoder.Result ref) {
        final int[] parent = new int[ref.calls.size()];
        final Map<Long, Deque<Integer>> byTid = new HashMap<>();
        for (int i = 0; i < ref.calls.size(); i++) {
            final Call c = ref.calls.get(i);
            final Deque<Integer> stack = byTid.computeIfAbsent(c.tid(), k -> new ArrayDeque<>());
            while (!stack.isEmpty() && ref.calls.get(stack.peek()).depth() == c.depth() + 1) {
                parent[stack.pop()] = i;
            }
            parent[i] = -1;
            stack.push(i);
        }
        return parent;
    }

    private static Map<Integer, long[]> bruteForce(final ReferenceDecoder.Result ref, final int[] parent,
            final int fIdx) {
        final Set<Integer> subtree = new HashSet<>();
        subtree.add(fIdx);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < parent.length; i++) {
                if (!subtree.contains(i) && parent[i] >= 0 && subtree.contains(parent[i])) {
                    subtree.add(i);
                    grew = true;
                }
            }
        }
        final Map<Integer, long[]> per = new HashMap<>();
        for (final int i : subtree) {
            final Call c = ref.calls.get(i);
            final long[] x = per.computeIfAbsent(c.methodId(), k -> new long[3]);
            x[0]++;
            x[1] += c.durNs();
            x[2] += c.selfNs();
        }
        return per;
    }

    @Test
    void recursionCountsEachCall() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "a()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0).enter(110, 0).enter(120, 0).exit(130).exit(150).exit(200);
        w.chunk(1, 100, p.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, 10_000, 10_000, 0, 0);
        assertEquals(3, agg.methodCalls(0), "a recursive method counts once per call in the flat figures");
        assertEquals(10_000, agg.methodSelfNs(0), "recursive self is exact and equals the outermost frame's duration");
        assertEquals(3, agg.nodeCount(), "each recursion level is its own node in the call tree");
        assertEquals(10_000, agg.totalNs(0), "the outermost node keeps the full duration");
    }

    @Test
    void unclosedFramesCloseAtTheSelectedFrameEnd() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "root()V", "child()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0).enter(140, 1);
        w.chunk(1, 100, p.bytes(), false);
        final TraceSnapshot d = TestTraces.index(w);

        final Call root = ReferenceDecoder.decode(w.bytes()).calls.stream().filter(fr -> fr.depth() == 0).findFirst()
                .orElseThrow();
        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, root.startNs(), root.durNs(), 0, 0);
        assertTrue(agg.found(), "an unclosed root still aggregates");
        assertEquals(root.durNs(), agg.totalNs(0), "the unclosed root is closed at the recording's coverage end");
        long selfSum = 0;
        for (int node = 0; node < agg.nodeCount(); node++) {
            selfSum += agg.selfNs(node);
        }
        assertEquals(root.durNs(), selfSum, "closing open descendants keeps the self sum equal to the total");
        assertEquals(2, agg.nodeCount(), "the open child appears as a node closed at the same end");
    }

    @Test
    void zeroDurationTwinsPickTheFirst() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "root()V", "twin()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0).enter(150, 1).exit(150).enter(150, 1).exit(150).exit(200);
        w.chunk(1, 100, p.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, 15_000, 0, 1, 1);
        assertTrue(agg.found(), "a zero-duration frame is still found");
        assertEquals(0, agg.totalNs(0), "the picked twin has zero duration");
        assertEquals(1, agg.nodeCount(), "a zero-duration leaf has no children");
    }

    @Test
    void spansTwoChunksAtDepthTwo() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "root()V", "mid()V", "leaf()V");
        final TraceBuilder.Payload p1 = new TraceBuilder.Payload(100);
        p1.enter(100, 0).enter(110, 1).enter(120, 2);
        w.chunk(1, 100, p1.bytes(), false);
        final TraceBuilder.Payload p2 = new TraceBuilder.Payload(130);
        p2.exit(130).exit(140).exit(200);
        w.chunk(1, 130, p2.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, 11_000, 3_000, 1, 1);
        assertTrue(agg.found(), "a frame whose events span two chunks is found from the mid-chunk enter");
        assertEquals(3_000, agg.totalNs(0), "its total is measured across the chunk boundary");
        assertEquals(2, agg.nodeCount(), "its single leaf child is captured");
        assertEquals(1_000, agg.methodTotalNs(2), "the leaf duration is exact across chunks");
    }

    @Test
    void missingFrameIsNotFound() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "root()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0).exit(200);
        w.chunk(1, 100, p.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, 50_000, 1_000, 0, 0);
        assertFalse(agg.found(), "a start that matches no frame is reported as not found");
        assertEquals(1, agg.nodeCount(), "a not-found result is the placeholder root only");
    }

    @Test
    void nodeCapFoldsIntoAncestorSelf() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "root()V", "a()V", "b()V", "c()V", "e()V");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 0);
        p.enter(110, 1).exit(120);
        p.enter(120, 2).exit(130);
        p.enter(130, 3).exit(140);
        p.enter(140, 4).exit(150);
        p.exit(200);
        w.chunk(1, 100, p.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);

        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, 10_000, 10_000, 0, 0, 3);
        assertTrue(agg.truncated(), "more distinct paths than the cap marks the result truncated");
        assertEquals(3, agg.nodeCount(), "no nodes are created beyond the cap");
        long selfSum = 0;
        for (int node = 0; node < agg.nodeCount(); node++) {
            selfSum += agg.selfNs(node);
        }
        assertEquals(10_000, selfSum, "folding capped frames into ancestor self keeps the self sum exact");

        final Set<Integer> mids = new HashSet<>();
        for (int node = 0; node < agg.nodeCount(); node++) {
            mids.add(agg.method(node));
        }
        assertTrue(mids.contains(0) && mids.contains(1) && mids.contains(2),
                "the earliest distinct paths keep their own nodes");
        assertEquals(4_000, agg.methodTotalNs(1) + agg.methodTotalNs(2) + agg.methodTotalNs(3) + agg.methodTotalNs(4),
                "every child's flat total stays exact even past the cap");
    }
}
