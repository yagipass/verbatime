package io.github.yagipass.verbatime.cli;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class PathTrie {

    static final int MAX_NODES = 1 << 20;

    int size = 1;

    int[] methodId = new int[1024];

    int[] parent = new int[1024];

    int[] depth = new int[1024];

    long[] calls = new long[1024];

    long[] totalTicks = new long[1024];

    long[] selfTicks = new long[1024];

    long[] thrown = new long[1024];

    long[] rootCalls = new long[1024];

    long[] slowestTicks = new long[1024];

    int[] slowestSession = new int[1024];

    long[] slowestOrdinal = new long[1024];

    long dropped;

    private final Map<Long, Integer> index = new HashMap<>();

    PathTrie() {
        methodId[0] = -1;
        parent[0] = -1;
        depth[0] = -1;
    }

    int child(final int p, final int method, final int d) {
        final long key = ((long) p << 22) | method;
        final Integer n = index.get(key);
        if (n != null) {
            return n;
        }
        if (size >= MAX_NODES) {
            dropped++;
            return -1;
        }
        if (size == methodId.length) {
            allocate(size * 2);
        }
        final int node = size++;
        methodId[node] = method;
        parent[node] = p;
        depth[node] = d;
        index.put(key, node);
        return node;
    }

    void record(final int node, final int session, final long ordinal, final long durTicks, final long self,
            final boolean threw) {
        calls[node]++;
        totalTicks[node] += durTicks;
        selfTicks[node] += self;
        if (threw) {
            thrown[node]++;
        }
        if (calls[node] == 1 || durTicks > slowestTicks[node]) {
            slowestTicks[node] = durTicks;
            slowestSession[node] = session;
            slowestOrdinal[node] = ordinal;
        }
    }

    CallId slowest(final int node) {
        return new CallId(slowestSession[node], slowestOrdinal[node]);
    }

    int[][] childrenByTotal() {
        final int[] counts = new int[size];
        for (int n = 1; n < size; n++) {
            counts[parent[n]]++;
        }
        final int[][] children = new int[size][];
        for (int n = 0; n < size; n++) {
            children[n] = new int[counts[n]];
            counts[n] = 0;
        }
        for (int n = 1; n < size; n++) {
            children[parent[n]][counts[parent[n]]++] = n;
        }
        for (final int[] k : children) {
            final Integer[] boxed = new Integer[k.length];
            for (int i = 0; i < k.length; i++) {
                boxed[i] = k[i];
            }
            Arrays.sort(boxed, (x, y) -> {
                final int c = Long.compare(totalTicks[y], totalTicks[x]);
                return c != 0 ? c : Integer.compare(x, y);
            });
            for (int i = 0; i < k.length; i++) {
                k[i] = boxed[i];
            }
        }
        return children;
    }

    private void allocate(final int capacity) {
        methodId = Arrays.copyOf(methodId, capacity);
        parent = Arrays.copyOf(parent, capacity);
        depth = Arrays.copyOf(depth, capacity);
        calls = Arrays.copyOf(calls, capacity);
        totalTicks = Arrays.copyOf(totalTicks, capacity);
        selfTicks = Arrays.copyOf(selfTicks, capacity);
        thrown = Arrays.copyOf(thrown, capacity);
        rootCalls = Arrays.copyOf(rootCalls, capacity);
        slowestTicks = Arrays.copyOf(slowestTicks, capacity);
        slowestSession = Arrays.copyOf(slowestSession, capacity);
        slowestOrdinal = Arrays.copyOf(slowestOrdinal, capacity);
    }
}
