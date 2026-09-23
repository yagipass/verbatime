package io.github.yagipass.verbatime.cli;

final class FloorHeap {

    private final long[] heap;

    private int size;

    FloorHeap(final int capacity) {
        heap = new long[Math.max(capacity, 1)];
    }

    void offer(final long ticks) {
        if (size < heap.length) {
            heap[size] = ticks;
            siftUp(size++);
        } else if (ticks > heap[0]) {
            heap[0] = ticks;
            siftDown(0);
        }
    }

    long floor() {
        return size < heap.length ? 0 : Math.max(heap[0], 1);
    }

    private void siftUp(int i) {
        while (i > 0) {
            final int parent = (i - 1) >>> 1;
            if (heap[parent] <= heap[i]) {
                return;
            }
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        while (true) {
            final int l = 2 * i + 1;
            final int r = l + 1;
            int m = i;
            if (l < size && heap[l] < heap[m]) {
                m = l;
            }
            if (r < size && heap[r] < heap[m]) {
                m = r;
            }
            if (m == i) {
                return;
            }
            swap(i, m);
            i = m;
        }
    }

    private void swap(final int a, final int b) {
        final long t = heap[a];
        heap[a] = heap[b];
        heap[b] = t;
    }
}
