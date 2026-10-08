package io.github.yagipass.verbatime.jmc;

import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record SelectedCall(
    long tid,
    long startNs,
    long durNs,
    long selfNs,
    int depth,
    int methodId,
    int exceptionId,
    boolean unclosed,
    List<Ancestor> ancestors,
    @Nullable SubtreeAggregate subtree,
    @Nullable String subtreeError) {

  static final String NOT_FOUND = "Selected call was not found in the recording";

  public record Ancestor(long startNs, long durNs, int depth, int methodId) {}

  // @Var on the reassigned component would also land on its final field, which Error Prone rejects.
  @SuppressWarnings("Var")
  public SelectedCall {
    ancestors = List.copyOf(ancestors);
  }

  public boolean thrown() {
    return exceptionId >= 0;
  }

  SelectedCall withSubtree(SubtreeAggregate a) {
    if (!a.found()) {
      return withSubtreeError(NOT_FOUND);
    }
    return new SelectedCall(
        tid, startNs, durNs, selfNs, depth, methodId, exceptionId, unclosed, ancestors, a, null);
  }

  SelectedCall withSubtreeError(String error) {
    return new SelectedCall(
        tid,
        startNs,
        durNs,
        selfNs,
        depth,
        methodId,
        exceptionId,
        unclosed,
        ancestors,
        null,
        error);
  }

  public long effectiveSelfNs() {
    return subtree != null ? subtree.selfNs(0) : selfNs;
  }

  public List<Ancestor> pathFromRoot() {
    List<Ancestor> out = new ArrayList<>(ancestors.size() + 1);
    out.addAll(ancestors);
    out.add(new Ancestor(startNs, durNs, depth, methodId));
    return out;
  }

  static List<Ancestor> parseAncestors(Object raw, int selectedDepth) {
    if (!(raw instanceof Object[] a)) {
      return List.of();
    }
    int n = a.length / 3;
    List<Ancestor> out = new ArrayList<>(n);
    for (int j = 0; j < n; j++) {
      if (!(a[3 * j] instanceof Number ts)
          || !(a[3 * j + 1] instanceof Number dur)
          || !(a[3 * j + 2] instanceof Number nm)) {
        return List.of();
      }
      out.add(
          new Ancestor(
              ts.longValue(), dur.longValue(), selectedDepth - (n - j), (int) nm.doubleValue()));
    }
    return out;
  }
}
