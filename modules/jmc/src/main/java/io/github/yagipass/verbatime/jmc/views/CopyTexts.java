package io.github.yagipass.verbatime.jmc.views;

import io.github.yagipass.verbatime.jmc.Formats;
import io.github.yagipass.verbatime.jmc.SelectedCall;
import io.github.yagipass.verbatime.jmc.SessionExportTexts;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import java.util.Locale;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class CopyTexts {

  private static final String TREE_ROW = "%10s  %10s  %6s  %8s  %s";

  private static final String ANCESTOR_ROW = "%5s  %10s  %12s  %s";

  private CopyTexts() {}

  static @Nullable String gcText(TraceSnapshot.GcPauses.Overlap o, long durNs) {
    if (o.pauses() == 0) {
      return null;
    }
    StringBuilder sb =
        new StringBuilder("GC ")
            .append(Formats.fmtDur(o.ns()))
            .append(" in ")
            .append(o.pauses())
            .append(o.pauses() == 1 ? " pause" : " pauses");
    if (durNs > 0) {
      sb.append(", ")
          .append(Formats.fmtPct(Math.min(o.ns() * 100.0 / durNs, 100)))
          .append(" of total");
    }
    return sb.toString();
  }

  static long sessionDurNs(TraceSnapshot d, long tid, long ts) {
    Session s = d.sessionAt(tid, ts);
    return s == null ? -1 : s.durNs();
  }

  static String pctOfSession(long durNs, long sessionDurNs) {
    return sessionDurNs > 0 ? Formats.fmtPct(durNs * 100.0 / sessionDurNs) : "";
  }

  static String ancestorText(TraceSnapshot d, SelectedCall f) {
    long sessionDur = sessionDurNs(d, f.tid(), f.startNs());
    StringBuilder sb = new StringBuilder();
    sb.append(sessionText(d, f.tid(), f.startNs())).append(" on ").append(d.threadName(f.tid()));
    if (sessionDur >= 0) {
      sb.append(", ").append(Formats.fmtDur(sessionDur));
    }
    sb.append('\n')
        .append(
            String.format(Locale.ROOT, ANCESTOR_ROW, "depth", "total", "% of session", "method"));
    for (SelectedCall.Ancestor a : f.pathFromRoot()) {
      sb.append('\n')
          .append(
              String.format(
                  Locale.ROOT,
                  ANCESTOR_ROW,
                  a.depth(),
                  Formats.fmtDur(a.durNs()),
                  pctOfSession(a.durNs(), sessionDur),
                  d.methodName(a.methodId())));
    }
    return sb.toString();
  }

  static String bottomUpText(BottomUpModel m, Predicate<BottomUpModel.Row> expanded) {
    StringBuilder sb = new StringBuilder();
    String order =
        m.sortKey().name().toLowerCase(Locale.ROOT)
            + (m.descending() ? " descending" : " ascending");
    sb.append("bottom-up of ")
        .append(Formats.signature(m.rootName()))
        .append(", total ")
        .append(Formats.fmtDur(m.rootTotalNs()))
        .append(", ")
        .append(Formats.fmtInt(m.size()))
        .append(" methods, by ")
        .append(order);
    if (m.truncated()) {
      sb.append(", call tree truncated");
    }
    appendTreeHeader(sb);
    for (BottomUpModel.Row r : m.rows()) {
      appendBottomUpRow(sb, m, r, 0, expanded);
    }
    return sb.toString();
  }

  private static void appendBottomUpRow(
      StringBuilder sb,
      BottomUpModel m,
      BottomUpModel.Row r,
      int level,
      Predicate<BottomUpModel.Row> expanded) {
    appendTreeRow(sb, level, r.selfNs(), r.totalNs(), m.pct(r.selfNs()), r.calls(), r.name());
    if (expanded.test(r)) {
      for (int i = 0; i < r.childCount(); i++) {
        appendBottomUpRow(sb, m, r.child(i), level + 1, expanded);
      }
    }
  }

  static String topDownText(TopDownModel m, Predicate<TopDownModel.Row> expanded) {
    StringBuilder sb = new StringBuilder();
    TopDownModel.Row root = m.root();
    sb.append("top-down of ")
        .append(Formats.signature(root.name()))
        .append(", total ")
        .append(Formats.fmtDur(root.totalNs()))
        .append(", ")
        .append(Formats.fmtInt(m.size()))
        .append(" call paths");
    if (m.truncated()) {
      sb.append(", truncated");
    }
    appendTreeHeader(sb);
    appendTopDownRow(sb, m, root, 0, expanded);
    return sb.toString();
  }

  private static void appendTopDownRow(
      StringBuilder sb,
      TopDownModel m,
      TopDownModel.Row r,
      int level,
      Predicate<TopDownModel.Row> expanded) {
    appendTreeRow(sb, level, r.selfNs(), r.totalNs(), m.pct(r.totalNs()), r.calls(), r.name());
    if (expanded.test(r)) {
      for (int i = 0; i < r.childCount(); i++) {
        appendTopDownRow(sb, m, m.child(r.node(), i), level + 1, expanded);
      }
    }
  }

  private static void appendTreeHeader(StringBuilder sb) {
    sb.append('\n')
        .append(String.format(Locale.ROOT, TREE_ROW, "self", "total", "%", "calls", "method"));
  }

  private static void appendTreeRow(
      StringBuilder sb, int level, long selfNs, long totalNs, double pct, long calls, String name) {
    sb.append('\n')
        .append(
            String.format(
                Locale.ROOT,
                TREE_ROW,
                Formats.fmtDur(selfNs),
                Formats.fmtDur(totalNs),
                Formats.fmtPct(pct),
                Formats.fmtInt(calls),
                "  ".repeat(level) + name));
  }

  static String sessionText(TraceSnapshot d, long tid, long ts) {
    Session s = d.sessionAt(tid, ts);
    if (s == null) {
      return "not in a session";
    }
    return "session #" + s.seq + " " + Formats.shortName(SessionExportTexts.rootName(d, s));
  }
}
