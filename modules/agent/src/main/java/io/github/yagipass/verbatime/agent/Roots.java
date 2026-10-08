package io.github.yagipass.verbatime.agent;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.Probe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class Roots {

  private volatile List<RootSpec> specs = List.of();

  private final Map<RootSpec, Integer> matchCounts = new HashMap<>();

  Roots() {}

  public List<RootSpec> specs() {
    return specs;
  }

  public synchronized boolean isResolved(RootSpec spec) {
    return matchCounts.getOrDefault(spec, 0) > 0;
  }

  public synchronized List<RootSpec> unresolved() {
    List<RootSpec> l = new ArrayList<>();
    for (RootSpec s : specs) {
      if (matchCounts.getOrDefault(s, 0) == 0) {
        l.add(s);
      }
    }
    return l;
  }

  void classCommitted(String binaryName, int baseId, List<String> sigs) {
    @Var boolean candidate = false;
    for (RootSpec s : specs) {
      if (s.className().equals(binaryName)) {
        candidate = true;
        break;
      }
    }
    if (!candidate) {
      return;
    }
    synchronized (this) {
      for (int k = 0; k < sigs.size(); k++) {
        RootSpec s = specMatching(binaryName, sigs.get(k));
        if (s != null) {
          Probe.addRootId(baseId + k);
          matchCounts.merge(s, 1, Integer::sum);
          Log.info("instrumented root " + s + " with method id " + (baseId + k));
        }
      }
    }
  }

  public synchronized void presetRoots(List<RootSpec> newSpecs) {
    matchAgainstLoadedClasses(newSpecs);
    StringBuilder sb =
        new StringBuilder("roots from the agent arguments, matched as their classes load:");
    for (RootSpec s : specs) {
      sb.append(' ').append(s);
    }
    Log.info(sb.toString());
  }

  public synchronized void replaceRoots(List<RootSpec> newSpecs) {
    matchAgainstLoadedClasses(newSpecs);
    StringBuilder sb = new StringBuilder("roots set:");
    for (RootSpec s : specs) {
      sb.append(' ')
          .append(s)
          .append('=')
          .append(Log.plural(matchCounts.getOrDefault(s, 0), "method"));
    }
    Log.info(specs.isEmpty() ? "roots cleared" : sb.toString());
  }

  private void matchAgainstLoadedClasses(List<RootSpec> newSpecs) {
    specs = List.copyOf(newSpecs);
    matchCounts.clear();
    long[] bits = new long[(MethodRegistry.size() + 63) >>> 6];
    for (MethodRegistry.CommittedClass c : MethodRegistry.committed()) {
      for (int k = 0; k < c.sigs().size(); k++) {
        RootSpec s = specMatching(c.className(), c.sigs().get(k));
        if (s != null) {
          int id = c.baseId() + k;
          bits[id >>> 6] |= 1L << id;
          matchCounts.merge(s, 1, Integer::sum);
        }
      }
    }
    Probe.replaceRootBits(bits);
  }

  private @Nullable RootSpec specMatching(String binaryName, String sig) {
    String name = MethodRegistry.methodNameOf(sig);
    String desc = sig.substring(name.length());
    for (RootSpec s : specs) {
      if (s.className().equals(binaryName) && s.matches(name, desc)) {
        return s;
      }
    }
    return null;
  }
}
