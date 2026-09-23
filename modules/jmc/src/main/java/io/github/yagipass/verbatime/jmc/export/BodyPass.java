package io.github.yagipass.verbatime.jmc.export;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;

import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.ChunkCursor;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

final class BodyPass {

    private final TraceSnapshot data;

    private final Session session;

    private final long floorTicks;

    private final TraceIndexer.ProgressListener progress;

    private final BodyLines body;

    private final OutlineHeap top;

    private final OpenCalls stack = new OpenCalls();

    private final BitSet used = new BitSet();

    private long[] calls;

    private long[] totalTicks;

    private long[] selfTicks;

    private int[] excIndexOf;

    private int[] excOrder = new int[16];

    private int exceptionCount;

    private int placeholderDepth;

    private long totalCalls;

    private long listed;

    BodyPass(final TraceSnapshot data, final Session session, final long floorTicks,
            final TraceIndexer.ProgressListener progress, final BodyLines body, final OutlineHeap top) {
        this.data = data;
        this.session = session;
        this.floorTicks = floorTicks;
        this.progress = progress;
        this.body = body;
        this.top = top;
        this.excIndexOf = new int[Math.max(data.exceptionNames.length + 1, 16)];
        final int methods = Math.max(data.methodNames.length + 1, 16);
        calls = new long[methods];
        totalTicks = new long[methods];
        selfTicks = new long[methods];
    }

    long totalCalls() {
        return totalCalls;
    }

    long listedCalls() {
        return listed;
    }

    int maxDepth() {
        return Math.max(stack.maxSize() - 1, 0);
    }

    BitSet used() {
        return used;
    }

    long[] calls() {
        return calls;
    }

    long[] totalTicks() {
        return totalTicks;
    }

    long[] selfTicks() {
        return selfTicks;
    }

    int exceptionCount() {
        return exceptionCount;
    }

    int exceptionIdAt(final int i) {
        return excOrder[i];
    }

    void run() throws IOException {
        final ThreadIndex m = data.thread(session.tid);
        if (m == null) {
            progress.report(0, 0);
            return;
        }
        final int c0 = session.firstChunk;
        final int c1 = session.lastChunk;
        long total = 0;
        for (int c = c0; c <= c1; c++) {
            total += m.chunks.payloadLen(c);
        }
        long done = 0;
        final ChunkCursor chunks = new ChunkCursor(data.buffer, m, c0, c1);
        try {
            final EventCursor cur = new EventCursor();
            while (chunks.next()) {
                if (progress.report(done, total)) {
                    throw new TraceIndexer.CancelledException();
                }
                if (chunks.payloadLen() <= 0) {
                    continue;
                }
                chunks.open(cur);
                decodeChunk(cur);
                done += chunks.payloadLen();
            }
        } finally {
            chunks.release();
        }
        if (progress.report(total, total)) {
            throw new TraceIndexer.CancelledException();
        }
        final long endTicks = session.endNs / Vbtm.NANOS_PER_TICK;
        while (stack.size() > 0) {
            exit(endTicks, -1, true);
        }
    }

    private void decodeChunk(final EventCursor cur) throws IOException {
        while (true) {
            final EventCursor.Event e = cur.next();
            if (e == EventCursor.Event.ENTER) {
                final int methodId = cur.methodId();
                stack.push(methodId, cur.ticks());
                used.set(methodId);
            } else if (e == EventCursor.Event.EXIT) {
                if (stack.size() > 0) {
                    exit(cur.ticks(), cur.exceptionId(), false);
                }
            } else {
                return;
            }
        }
    }

    private void exit(final long endTicks, final int exc, final boolean unclosed) throws IOException {
        final int k = stack.pop();
        final int methodId = stack.methodId[k];
        final long dur = Math.max(endTicks - stack.startTicks[k], 0);
        final long self = Math.max(dur - stack.childTicks[k], 0);
        final boolean isListed = unclosed || k == 0 || dur >= floorTicks;
        final boolean thrown = exc >= 0;
        final int excNo = exc > 0 ? exceptionNo(exc) : 0;
        totalCalls++;
        if (!unclosed) {
            ensureMethod(methodId);
            calls[methodId]++;
            totalTicks[methodId] += dur;
            selfTicks[methodId] += self;
        }
        if (isListed) {
            final boolean wasEmitted = k < placeholderDepth;
            if (!wasEmitted) {
                writePlaceholdersBelow(k);
            }
            final long lineNo;
            if (wasEmitted) {
                lineNo = stack.lineNo[k];
                body.patchPlaceholder(stack, k, dur, self, thrown, excNo, unclosed);
            } else {
                lineNo = body.nextLine();
                body.writeLine(stack, k, dur, self, thrown, excNo, unclosed);
            }
            if (stack.belowFloorCalls[k] > 0) {
                body.writeBelowFloorLine(stack, k);
            }
            listed++;
            final long subLines = body.lines() - lineNo + 1;
            top.offer(dur, totalCalls, lineNo, stack.startTicks[k], self, k, methodId, stack.directChildren[k], subLines,
                    (byte) ((thrown ? 1 : 0) | (unclosed ? 2 : 0)), excNo);
        }
        if (k > 0) {
            final int p = k - 1;
            stack.childTicks[p] += dur;
            stack.directChildren[p]++;
            stack.descendants[p] += stack.descendants[k] + 1;
            stack.thrownDescendants[p] += stack.thrownDescendants[k] + (thrown ? 1 : 0);
            if (!isListed) {
                stack.belowFloorCalls[p]++;
                stack.belowFloorDescendants[p] += stack.descendants[k] + 1;
                stack.belowFloorThrown[p] += stack.thrownDescendants[k] + (thrown ? 1 : 0);
                stack.belowFloorTicks[p] += dur;
                stack.belowFloorAt(p).increment(methodId);
            }
        }
        final BelowFloorCounts own = stack.belowFloorOrNull(k);
        if (own != null) {
            own.clear();
        }
        if (placeholderDepth > k) {
            placeholderDepth = k;
        }
    }

    private void writePlaceholdersBelow(final int k) throws IOException {
        for (int i = placeholderDepth; i < k; i++) {
            body.writePlaceholder(stack, i);
        }
        if (placeholderDepth < k) {
            placeholderDepth = k;
        }
    }

    private int exceptionNo(final int exc) {
        if (exc >= excIndexOf.length) {
            excIndexOf = Arrays.copyOf(excIndexOf, Math.max(exc + 1, excIndexOf.length * 2));
        }
        int n = excIndexOf[exc];
        if (n == 0) {
            if (exceptionCount == excOrder.length) {
                excOrder = Arrays.copyOf(excOrder, exceptionCount * 2);
            }
            excOrder[exceptionCount++] = exc;
            n = exceptionCount;
            excIndexOf[exc] = n;
        }
        return n;
    }

    private void ensureMethod(final int methodId) {
        if (methodId >= calls.length) {
            final int n = Math.max(methodId + 1, calls.length * 2);
            calls = Arrays.copyOf(calls, n);
            totalTicks = Arrays.copyOf(totalTicks, n);
            selfTicks = Arrays.copyOf(selfTicks, n);
        }
    }
}
