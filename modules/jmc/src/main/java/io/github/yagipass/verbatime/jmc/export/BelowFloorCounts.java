package io.github.yagipass.verbatime.jmc.export;

import java.util.Arrays;

final class BelowFloorCounts {

    private int[] keys = new int[16];

    private int[] counts = new int[16];

    private int[] touched = new int[8];

    private int size;

    void increment(final int methodId) {
        final int key = methodId + 1;
        int mask = keys.length - 1;
        int slot = mix(key) & mask;
        while (true) {
            final int k = keys[slot];
            if (k == key) {
                counts[slot]++;
                return;
            }
            if (k == 0) {
                break;
            }
            slot = (slot + 1) & mask;
        }
        if ((size + 1) * 2 > keys.length) {
            allocate();
            mask = keys.length - 1;
            slot = mix(key) & mask;
            while (keys[slot] != 0) {
                slot = (slot + 1) & mask;
            }
        }
        keys[slot] = key;
        counts[slot] = 1;
        if (size == touched.length) {
            touched = Arrays.copyOf(touched, size * 2);
        }
        touched[size++] = slot;
    }

    int size() {
        return size;
    }

    int methodId(final int i) {
        return keys[touched[i]] - 1;
    }

    int count(final int i) {
        return counts[touched[i]];
    }

    void clear() {
        for (int i = 0; i < size; i++) {
            keys[touched[i]] = 0;
        }
        size = 0;
    }

    private void allocate() {
        final int[] oldKeys = keys;
        final int[] oldCounts = counts;
        final int[] oldTouched = touched;
        keys = new int[oldKeys.length * 2];
        counts = new int[oldKeys.length * 2];
        final int mask = keys.length - 1;
        for (int i = 0; i < size; i++) {
            final int from = oldTouched[i];
            int slot = mix(oldKeys[from]) & mask;
            while (keys[slot] != 0) {
                slot = (slot + 1) & mask;
            }
            keys[slot] = oldKeys[from];
            counts[slot] = oldCounts[from];
            touched[i] = slot;
        }
    }

    private static int mix(final int x) {
        final int h = x * 0x9E3779B9;
        return h ^ (h >>> 16);
    }
}
