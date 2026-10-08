package io.github.yagipass.verbatime.cli;

import com.google.errorprone.annotations.Var;

final class FloorHeap {

  private final long[] heap;

  private int size;

  FloorHeap(int capacity) {
    heap = new long[Math.max(capacity, 1)];
  }

  void offer(long ticks) {
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

  private void siftUp(@Var int i) {
    while (i > 0) {
      int parent = (i - 1) >>> 1;
      if (heap[parent] <= heap[i]) {
        return;
      }
      swap(i, parent);
      i = parent;
    }
  }

  private void siftDown(@Var int i) {
    while (true) {
      int l = 2 * i + 1;
      int r = l + 1;
      @Var int m = i;
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

  private void swap(int a, int b) {
    long t = heap[a];
    heap[a] = heap[b];
    heap[b] = t;
  }
}
