package io.github.yagipass.verbatime.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.Probe;

public final class Roots {

    private volatile List<RootSpec> specs = List.of();

    private final Map<RootSpec, Integer> matchCounts = new HashMap<>();

    Roots() {
    }

    public List<RootSpec> specs() {
        return specs;
    }

    public synchronized boolean isResolved(final RootSpec spec) {
        return matchCounts.getOrDefault(spec, 0) > 0;
    }

    public synchronized List<RootSpec> unresolved() {
        final List<RootSpec> l = new ArrayList<>();
        for (final RootSpec s : specs) {
            if (matchCounts.getOrDefault(s, 0) == 0) {
                l.add(s);
            }
        }
        return l;
    }

    void classCommitted(final String binaryName, final int baseId, final List<String> sigs) {
        boolean candidate = false;
        for (final RootSpec s : specs) {
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
                final RootSpec s = specMatching(binaryName, sigs.get(k));
                if (s != null) {
                    Probe.addRootId(baseId + k);
                    matchCounts.merge(s, 1, Integer::sum);
                    Log.info("instrumented root " + s + " with method id " + (baseId + k));
                }
            }
        }
    }

    public synchronized void presetRoots(final List<RootSpec> newSpecs) {
        matchAgainstLoadedClasses(newSpecs);
        final StringBuilder sb = new StringBuilder("roots from the agent arguments, matched as their classes load:");
        for (final RootSpec s : specs) {
            sb.append(' ').append(s);
        }
        Log.info(sb.toString());
    }

    public synchronized void replaceRoots(final List<RootSpec> newSpecs) {
        matchAgainstLoadedClasses(newSpecs);
        final StringBuilder sb = new StringBuilder("roots set:");
        for (final RootSpec s : specs) {
            sb.append(' ').append(s).append('=').append(Log.plural(matchCounts.getOrDefault(s, 0), "method"));
        }
        Log.info(specs.isEmpty() ? "roots cleared" : sb.toString());
    }

    private void matchAgainstLoadedClasses(final List<RootSpec> newSpecs) {
        specs = List.copyOf(newSpecs);
        matchCounts.clear();
        final long[] bits = new long[(MethodRegistry.size() + 63) >>> 6];
        for (final MethodRegistry.CommittedClass c : MethodRegistry.committed()) {
            for (int k = 0; k < c.sigs().size(); k++) {
                final RootSpec s = specMatching(c.className(), c.sigs().get(k));
                if (s != null) {
                    final int id = c.baseId() + k;
                    bits[id >>> 6] |= 1L << id;
                    matchCounts.merge(s, 1, Integer::sum);
                }
            }
        }
        Probe.replaceRootBits(bits);
    }

    private RootSpec specMatching(final String binaryName, final String sig) {
        final String name = MethodRegistry.methodNameOf(sig);
        final String desc = sig.substring(name.length());
        for (final RootSpec s : specs) {
            if (s.className().equals(binaryName) && s.matches(name, desc)) {
                return s;
            }
        }
        return null;
    }
}
