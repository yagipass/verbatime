package io.github.yagipass.verbatime.cli;

import java.util.Arrays;

final class NodeStack {

    private int[] nodes = new int[64];

    private int[] states = new int[64];

    private int size;

    void push(final int node) {
        if (size == nodes.length) {
            nodes = Arrays.copyOf(nodes, size * 2);
            states = Arrays.copyOf(states, size * 2);
        }
        nodes[size] = node;
        states[size] = -1;
        size++;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    int node() {
        return nodes[size - 1];
    }

    int state() {
        return states[size - 1];
    }

    void setState(final int state) {
        states[size - 1] = state;
    }

    void pop() {
        size--;
    }
}
