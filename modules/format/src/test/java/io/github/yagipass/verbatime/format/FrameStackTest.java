package io.github.yagipass.verbatime.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FrameStackTest {

    private static final long T = Vbtm.NANOS_PER_TICK;

    @Test
    void poppingAChildChargesItsDurationToTheParentSoSelfIsTotalMinusChildren() {
        final FrameStack s = new FrameStack();
        s.push(100, 1);
        s.push(120, 2);
        assertEquals(2, s.depth());

        s.pop(150);
        assertEquals(120 * T, s.startNs());
        assertEquals(30 * T, s.durNs());
        assertEquals(0, s.childNs());
        assertEquals(30 * T, s.selfNs());
        assertEquals(2, s.methodId());

        s.pop(200);
        assertEquals(100 * T, s.durNs());
        assertEquals(30 * T, s.childNs());
        assertEquals(70 * T, s.selfNs());
        assertEquals(1, s.methodId());
        assertEquals(0, s.depth());
    }

    @Test
    void selfNeverGoesNegativeWhenQuantisedChildrenOutlastTheParent() {
        final FrameStack s = new FrameStack();
        s.push(100, 1);
        s.push(100, 2);
        s.pop(110);
        s.pop(105);
        assertEquals(5 * T, s.durNs());
        assertEquals(10 * T, s.childNs());
        assertEquals(0, s.selfNs());
    }

    @Test
    void aCopyKeepsTheOpenFramesButIsIndependentOfLaterPushesAndPops() {
        final FrameStack s = new FrameStack();
        for (int i = 0; i < 100; i++) {
            s.push(i, i);
        }
        final FrameStack c = s.copy();
        s.pop(1_000);
        s.pop(1_000);
        assertEquals(98, s.depth());
        assertEquals(100, c.depth());
        c.pop(500);
        assertEquals(99, c.methodId());
        assertEquals((500 - 99) * T, c.durNs());
        s.clear();
        assertEquals(0, s.depth());
        assertEquals(99, c.depth());
    }

    @Test
    void aCopyTakenRightAfterAPopStillReportsThatFrameEvenBeyondTheInitialCapacity() {
        final FrameStack s = new FrameStack();
        for (int i = 0; i < 65; i++) {
            s.push(i, i);
        }
        s.pop(1_000);
        final FrameStack c = s.copy();
        assertEquals(64, c.depth());
        assertEquals(64, c.methodId());
        assertEquals(64 * T, c.startNs());
        assertEquals((1_000 - 64) * T, c.durNs());
        assertEquals(0, c.childNs());
        assertEquals(c.durNs(), c.selfNs());

        final FrameStack shallow = new FrameStack();
        shallow.push(100, 1);
        shallow.push(120, 2);
        shallow.pop(150);
        final FrameStack sc = shallow.copy();
        assertEquals(30 * T, sc.durNs());
        assertEquals(2, sc.methodId());
    }
}
