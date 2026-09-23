package io.github.yagipass.verbatime.cli;

import java.util.Arrays;

final class CallStack {

    private int[] methodIds = new int[64];

    void push(final int depth, final int methodId) {
        if (depth == methodIds.length) {
            methodIds = Arrays.copyOf(methodIds, depth * 2);
        }
        methodIds[depth] = methodId;
    }

    int methodId(final int depth) {
        return methodIds[depth];
    }

    int callerOf(final int depth) {
        return depth > 0 ? methodIds[depth - 1] : -1;
    }

    int[] methodIds(final int count) {
        return Arrays.copyOf(methodIds, count);
    }
}
