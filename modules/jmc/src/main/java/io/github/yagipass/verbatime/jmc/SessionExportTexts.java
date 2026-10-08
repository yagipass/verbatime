package io.github.yagipass.verbatime.jmc;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.jmc.export.SessionExporter;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class SessionExportTexts {

  private SessionExportTexts() {}

  static String exportFileName(String recordingFileName, int seq, int floorUs) {
    @Var String base = recordingFileName;
    if (base.length() > 5 && base.regionMatches(true, base.length() - 5, ".vbtm", 0, 5)) {
      base = base.substring(0, base.length() - 5);
    }
    return base + "-session" + seq + "-" + (floorUs > 0 ? floorUs + "us" : "nofloor") + ".txt";
  }

  static long floorNs(int floorUs) {
    return floorUs * 1_000L;
  }

  static @Nullable Session defaultSession(TraceSnapshot d, @Nullable SelectedCall f) {
    if (f != null) {
      Session s = d.sessionAt(f.tid(), f.startNs());
      if (s != null) {
        return s;
      }
    }
    @Var Session best = null;
    for (Session s : d.sessions) {
      if (best == null || s.durNs() > best.durNs()) {
        best = s;
      }
    }
    return best;
  }

  public static String rootName(TraceSnapshot d, Session s) {
    return s.rootMethodId >= 0 ? d.methodName(s.rootMethodId) : "<no enter>";
  }

  static boolean isExportable(Session s) {
    return s.callCount > 0;
  }

  static String flags(Session s) {
    List<String> f = new ArrayList<>(3);
    if (!s.ended) {
      f.add("unclosed");
    }
    if (s.rootMethodId < 0) {
      f.add("no enter");
    }
    if (s.callCount == 0) {
      f.add("empty");
    }
    return String.join(", ", f);
  }

  static String summary(Path dest, Session s, long floorNs, SessionExporter.Result r) {
    String floor =
        floorNs > 0 ? " with a floor of " + SessionExporter.floorLabel(floorNs) : " with no floor";
    return "Exported session #"
        + s.seq
        + floor
        + " to\n"
        + dest
        + "\n\n"
        + Formats.fmtInt(r.bodyLines())
        + " body lines, "
        + Formats.fmtBytes(r.bytes())
        + "\n"
        + Formats.fmtInt(r.listedCalls())
        + " calls listed, "
        + Formats.fmtInt(r.belowFloorCalls())
        + " calls below the floor kept as counts";
  }
}
