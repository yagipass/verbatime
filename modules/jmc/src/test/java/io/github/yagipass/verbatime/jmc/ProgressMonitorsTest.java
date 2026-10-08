package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Test;

final class ProgressMonitorsTest {

  private static final class Counting extends NullProgressMonitor {
    int worked;

    @Override
    public void worked(int work) {
      worked += work;
    }
  }

  @Test
  void reportsGrowthOnlyAndSumsToTheTaskTotal() {
    Counting m = new Counting();
    TraceIndexer.ProgressListener p = ProgressMonitors.of(m);
    assertFalse(p.report(10, 100));
    assertEquals(100, m.worked);
    assertFalse(p.report(10, 100));
    assertEquals(100, m.worked, "repeating the same position adds nothing");
    assertFalse(p.report(50, 100));
    assertFalse(p.report(100, 100));
    assertEquals(ProgressMonitors.TICKS, m.worked);
  }

  @Test
  void neverOvershootsEvenIfTheFileGrewUnderneath() {
    Counting m = new Counting();
    TraceIndexer.ProgressListener p = ProgressMonitors.of(m);
    p.report(150, 100);
    assertEquals(ProgressMonitors.TICKS, m.worked);
    p.report(0, 0);
    assertEquals(ProgressMonitors.TICKS, m.worked, "an empty total is not a division by zero");
  }

  @Test
  void theMonitorsCancelButtonBecomesTheReadersCancelRequest() {
    Counting m = new Counting();
    TraceIndexer.ProgressListener p = ProgressMonitors.of(m);
    assertFalse(p.report(1, 10));
    m.setCanceled(true);
    assertTrue(p.report(2, 10));
  }
}
