package io.github.yagipass.verbatime.agent.probe;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.format.Vbtm;

public final class GcPausesTest {

    private GcPausesTest() {
    }

    public static void run() throws Exception {
        Check.that(GcPauses.reportsPauses("G1 Young Generation") && GcPauses.reportsPauses("ZGC Pauses") && GcPauses.reportsPauses("Copy"), "every collector's pause bean is subscribed");
        Check.that(!GcPauses.reportsPauses("ZGC Cycles") && !GcPauses.reportsPauses("Shenandoah Cycles"), "concurrent-cycle beans are not pauses and are skipped");
        Check.eq(Vbtm.GC_ACTION_MINOR, GcPauses.actionCode("end of minor GC"), "minor");
        Check.eq(Vbtm.GC_ACTION_MAJOR, GcPauses.actionCode("end of major GC"), "major");
        Check.eq(Vbtm.GC_ACTION_UNKNOWN, GcPauses.actionCode(null), "a missing action is unknown, not an error");
        GcPauses.attach(null);
        GcPauses.detach();
        GcPauses.detach();
    }
}
