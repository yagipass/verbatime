package io.github.yagipass.verbatime.jmc.query;

import java.util.Arrays;

import io.github.yagipass.verbatime.jmc.index.ChunkWalker;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

public final class SubtreeAggregate {

    public static final int MAX_NODES = 1 << 20;

    private final boolean found;

    private final boolean truncated;

    private final int nodeCount;

    private final int[] nodeMethod;

    private final int[] nodeParent;

    private final long[] nodeCalls;

    private final long[] nodeTotalNs;

    private final long[] nodeSelfNs;

    private final int[] childStart;

    private final int[] childIndex;

    private final int[] byMethodStart;

    private final int[] byMethodIndex;

    private final long[] methodCalls;

    private final long[] methodTotalNs;

    private final long[] methodSelfNs;

    private SubtreeAggregate(final Collector b) {
        found = b.done;
        truncated = b.truncated;
        nodeCount = b.n;
        nodeMethod = Arrays.copyOf(b.nodeMethod, b.n);
        nodeParent = Arrays.copyOf(b.nodeParent, b.n);
        nodeCalls = Arrays.copyOf(b.nodeCalls, b.n);
        nodeTotalNs = Arrays.copyOf(b.nodeTotalNs, b.n);
        nodeSelfNs = Arrays.copyOf(b.nodeSelfNs, b.n);
        final int m = b.methodIdLimit;
        methodCalls = Arrays.copyOf(b.methodCalls, m);
        methodTotalNs = Arrays.copyOf(b.methodTotalNs, m);
        methodSelfNs = Arrays.copyOf(b.methodSelfNs, m);

        childStart = new int[b.n + 1];
        for (int i = 1; i < b.n; i++) {
            childStart[nodeParent[i] + 1]++;
        }
        for (int i = 0; i < b.n; i++) {
            childStart[i + 1] += childStart[i];
        }
        childIndex = new int[Math.max(b.n - 1, 0)];
        final int[] fill = Arrays.copyOf(childStart, b.n);
        for (int i = 1; i < b.n; i++) {
            childIndex[fill[nodeParent[i]]++] = i;
        }

        byMethodStart = new int[m + 1];
        for (int i = 0; i < b.n; i++) {
            byMethodStart[nodeMethod[i] + 1]++;
        }
        for (int i = 0; i < m; i++) {
            byMethodStart[i + 1] += byMethodStart[i];
        }
        byMethodIndex = new int[b.n];
        final int[] mfill = Arrays.copyOf(byMethodStart, m);
        for (int i = 0; i < b.n; i++) {
            byMethodIndex[mfill[nodeMethod[i]]++] = i;
        }
    }

    public static SubtreeAggregate compute(final TraceSnapshot data, final long tid, final long startNs, final long durNs,
            final int depth, final int methodId) {
        return compute(data, tid, startNs, durNs, depth, methodId, MAX_NODES);
    }

    static SubtreeAggregate compute(final TraceSnapshot data, final long tid, final long startNs, final long durNs,
            final int depth, final int methodId, final int maxNodes) {
        final Collector b = new Collector(data, startNs, durNs, depth, methodId, maxNodes);
        final ThreadIndex m = data.thread(tid);
        if (m != null) {
            ChunkWalker.walkRange(data, m, startNs, startNs + durNs, b);
            b.sessionEnd();
        }
        return new SubtreeAggregate(b);
    }

    public boolean found() {
        return found;
    }

    public boolean truncated() {
        return truncated;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int method(final int node) {
        return nodeMethod[node];
    }

    public int parent(final int node) {
        return nodeParent[node];
    }

    public long calls(final int node) {
        return nodeCalls[node];
    }

    public long totalNs(final int node) {
        return nodeTotalNs[node];
    }

    public long selfNs(final int node) {
        return nodeSelfNs[node];
    }

    public int childCount(final int node) {
        return childStart[node + 1] - childStart[node];
    }

    public int child(final int node, final int i) {
        return childIndex[childStart[node] + i];
    }

    public int methodIdLimit() {
        return methodCalls.length;
    }

    public int methodNodeCount(final int methodId) {
        return methodId < 0 || methodId >= methodCalls.length ? 0 : byMethodStart[methodId + 1] - byMethodStart[methodId];
    }

    public int methodNode(final int methodId, final int i) {
        return byMethodIndex[byMethodStart[methodId] + i];
    }

    public long methodCalls(final int methodId) {
        return methodId < 0 || methodId >= methodCalls.length ? 0 : methodCalls[methodId];
    }

    public long methodTotalNs(final int methodId) {
        return methodId < 0 || methodId >= methodCalls.length ? 0 : methodTotalNs[methodId];
    }

    public long methodSelfNs(final int methodId) {
        return methodId < 0 || methodId >= methodCalls.length ? 0 : methodSelfNs[methodId];
    }

    private static final class Collector implements ChunkWalker.Visitor {

        private final long rootStartNs;

        private final long rootDurNs;

        private final long rootEndNs;

        private final int rootDepth;

        private final int rootMethodId;

        private final int maxNodes;

        private int n;

        private boolean truncated;

        private boolean done;

        private int[] nodeMethod = new int[256];

        private int[] nodeParent = new int[256];

        private long[] nodeCalls = new long[256];

        private long[] nodeTotalNs = new long[256];

        private long[] nodeSelfNs = new long[256];

        private int methodIdLimit;

        private long[] methodCalls;

        private long[] methodTotalNs;

        private long[] methodSelfNs;

        private long[] keys = new long[512];

        private int[] vals = new int[512];

        private int mapSize;

        private boolean collecting;

        private int rootSp;

        private int top;

        private int[] nodeAtDepth = new int[256];

        private int[] spMethodId = new int[256];

        private long[] spStart = new long[256];

        private long[] spChild = new long[256];

        private Collector(final TraceSnapshot data, final long startNs, final long durNs, final int depth, final int methodId,
                final int maxNodes) {
            this.rootStartNs = startNs;
            this.rootDurNs = durNs;
            this.rootEndNs = startNs + durNs;
            this.rootDepth = depth;
            this.rootMethodId = methodId;
            this.maxNodes = Math.max(maxNodes, 1);
            final int ml = Math.max(Math.max(data.methodNames.length, methodId + 1), 16);
            methodCalls = new long[ml];
            methodTotalNs = new long[ml];
            methodSelfNs = new long[ml];
            Arrays.fill(keys, -1L);
            reset();
        }

        private void reset() {
            n = 1;
            nodeMethod[0] = rootMethodId;
            nodeParent[0] = -1;
            nodeCalls[0] = 1;
            nodeTotalNs[0] = rootDurNs;
            nodeSelfNs[0] = rootDurNs;
            methodIdLimit = rootMethodId + 1;
            Arrays.fill(methodCalls, 0);
            Arrays.fill(methodTotalNs, 0);
            Arrays.fill(methodSelfNs, 0);
            methodCalls[rootMethodId] = 1;
            methodTotalNs[rootMethodId] = rootDurNs;
            methodSelfNs[rootMethodId] = rootDurNs;
            if (mapSize > 0) {
                Arrays.fill(keys, -1L);
                mapSize = 0;
            }
            truncated = false;
            collecting = false;
        }

        @Override
        public boolean enter(final long startNs, final int methodId, final int sessionDepth, final int sp) {
            if (done) {
                return false;
            }
            if (!collecting) {
                if (startNs > rootEndNs) {
                    return false;
                }
                if (startNs == rootStartNs && sessionDepth == rootDepth && methodId == rootMethodId) {
                    collecting = true;
                    rootSp = sp;
                    ensureSp(sp);
                    nodeAtDepth[sp] = 0;
                    spMethodId[sp] = rootMethodId;
                    spStart[sp] = startNs;
                    spChild[sp] = 0;
                    top = sp + 1;
                    nodeTotalNs[0] = 0;
                    nodeSelfNs[0] = 0;
                    methodTotalNs[rootMethodId] -= rootDurNs;
                    methodSelfNs[rootMethodId] -= rootDurNs;
                }
                return true;
            }
            ensureSp(sp);
            final int parent = nodeAtDepth[sp - 1];
            nodeAtDepth[sp] = parent < 0 ? -1 : childOrCreate(parent, methodId);
            spMethodId[sp] = methodId;
            spStart[sp] = startNs;
            spChild[sp] = 0;
            top = sp + 1;
            ensureMethod(methodId);
            methodCalls[methodId]++;
            return true;
        }

        @Override
        public boolean exit(final long startNs, final long durNs, final long childNs, final int methodId, final int sessionDepth,
                final int sp, final int exc) {
            if (!collecting || sp < rootSp) {
                return true;
            }
            account(sp, methodId, durNs, childNs);
            top = sp;
            if (sp == rootSp) {
                if (durNs == rootDurNs) {
                    done = true;
                    return false;
                }
                reset();
            }
            return true;
        }

        @Override
        public void sessionEnd() {
            if (collecting && !done) {
                closeOpenAt();
                done = true;
            }
        }

        private void closeOpenAt() {
            for (int sp = top - 1; sp >= rootSp; sp--) {
                final long durNs = rootEndNs - spStart[sp];
                account(sp, spMethodId[sp], durNs, spChild[sp]);
            }
            top = rootSp;
        }

        private void account(final int sp, final int methodId, final long durNs, final long childNs) {
            final long self = Math.max(durNs - childNs, 0);
            if (sp > rootSp) {
                spChild[sp - 1] += durNs;
            }
            ensureMethod(methodId);
            methodTotalNs[methodId] += durNs;
            methodSelfNs[methodId] += self;
            final int node = nodeAtDepth[sp];
            if (node >= 0) {
                nodeTotalNs[node] += durNs;
                nodeSelfNs[node] += self;
            } else {
                final int parent = nodeAtDepth[sp - 1];
                if (parent >= 0) {
                    nodeSelfNs[parent] += durNs;
                }
            }
        }

        private int childOrCreate(final int parent, final int methodId) {
            final long key = (long) parent << 32 | methodId;
            int mask = keys.length - 1;
            int i = hash(key) & mask;
            while (true) {
                final long k = keys[i];
                if (k == key) {
                    nodeCalls[vals[i]]++;
                    return vals[i];
                }
                if (k == -1L) {
                    break;
                }
                i = (i + 1) & mask;
            }
            if (n == maxNodes) {
                truncated = true;
                return -1;
            }
            if (n == nodeMethod.length) {
                final int cap = n * 2;
                nodeMethod = Arrays.copyOf(nodeMethod, cap);
                nodeParent = Arrays.copyOf(nodeParent, cap);
                nodeCalls = Arrays.copyOf(nodeCalls, cap);
                nodeTotalNs = Arrays.copyOf(nodeTotalNs, cap);
                nodeSelfNs = Arrays.copyOf(nodeSelfNs, cap);
            }
            final int node = n++;
            nodeMethod[node] = methodId;
            nodeParent[node] = parent;
            nodeCalls[node] = 1;
            if ((mapSize + 1) * 2 > keys.length) {
                allocate();
                mask = keys.length - 1;
                i = hash(key) & mask;
                while (keys[i] != -1L) {
                    i = (i + 1) & mask;
                }
            }
            keys[i] = key;
            vals[i] = node;
            mapSize++;
            return node;
        }

        private void allocate() {
            final long[] ok = keys;
            final int[] ov = vals;
            keys = new long[ok.length * 2];
            vals = new int[ok.length * 2];
            Arrays.fill(keys, -1L);
            final int mask = keys.length - 1;
            for (int j = 0; j < ok.length; j++) {
                if (ok[j] != -1L) {
                    int i = hash(ok[j]) & mask;
                    while (keys[i] != -1L) {
                        i = (i + 1) & mask;
                    }
                    keys[i] = ok[j];
                    vals[i] = ov[j];
                }
            }
        }

        private static int hash(final long key) {
            return (int) ((key * 0x9E3779B97F4A7C15L) >>> 32);
        }

        private void ensureSp(final int sp) {
            if (sp >= nodeAtDepth.length) {
                final int cap = Math.max(sp + 1, nodeAtDepth.length * 2);
                nodeAtDepth = Arrays.copyOf(nodeAtDepth, cap);
                spMethodId = Arrays.copyOf(spMethodId, cap);
                spStart = Arrays.copyOf(spStart, cap);
                spChild = Arrays.copyOf(spChild, cap);
            }
        }

        private void ensureMethod(final int methodId) {
            if (methodId >= methodCalls.length) {
                final int cap = Math.max(methodId + 1, methodCalls.length * 2);
                methodCalls = Arrays.copyOf(methodCalls, cap);
                methodTotalNs = Arrays.copyOf(methodTotalNs, cap);
                methodSelfNs = Arrays.copyOf(methodSelfNs, cap);
            }
            if (methodId + 1 > methodIdLimit) {
                methodIdLimit = methodId + 1;
            }
        }
    }
}
