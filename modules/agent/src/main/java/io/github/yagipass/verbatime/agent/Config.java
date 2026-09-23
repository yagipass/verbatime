package io.github.yagipass.verbatime.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.yagipass.verbatime.agent.probe.Log;

public record Config(List<String> includes, List<String> excludes, List<RootSpec> roots, String out, String spoolDir, long waitStartMs, RecordStart recordStart) {

    static Config parse(final String args) {
        return parse(args, Log::warn);
    }

    static Config parse(final String args, final Consumer<String> warn) {
        final List<String> includes = new ArrayList<>();
        final List<String> excludes = new ArrayList<>();
        final List<RootSpec> roots = new ArrayList<>();
        boolean includeIgnored = false;
        String out = null;
        String spoolDir = null;
        long waitStartMs = 0;
        RecordStart recordStart = RecordStart.ONDEMAND;

        if (args != null) {
            for (final String part : args.split(",", -1)) {
                final String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                final int eq = p.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("expected key=value, got '" + p + "'");
                }
                final String key = p.substring(0, eq).trim();
                final String value = p.substring(eq + 1).trim();
                switch (key) {
                    case "include" -> includeIgnored |= addPrefixesReportingIgnored(includes, value, key, warn);
                    case "exclude" -> addPrefixesReportingIgnored(excludes, value, key, warn);
                    case "out" -> {
                        if (value.isEmpty()) {
                            throw new IllegalArgumentException("out= needs a file path");
                        }
                        out = value;
                    }
                    case "spool" -> {
                        if (value.isEmpty()) {
                            throw new IllegalArgumentException("spool= needs a directory path");
                        }
                        spoolDir = value;
                    }
                    case "roots" -> addRoots(roots, value);
                    case "waitstart" -> waitStartMs = parseWaitStart(value);
                    case "record" -> recordStart = RecordStart.parse(value);
                    default -> throw new IllegalArgumentException("unknown argument: " + key);
                }
            }
        }
        if (out != null && spoolDir != null) {
            throw new IllegalArgumentException("out= and spool= are mutually exclusive");
        }
        if (recordStart == RecordStart.STARTUP) {
            rejectStartupWithout(roots, out, spoolDir, waitStartMs);
        }
        if (includeIgnored && includes.isEmpty()) {
            warn.accept("no include= prefix is left after ignoring the ones above, so every class is instrumented as if include= were omitted");
        }
        final Config cfg = new Config(List.copyOf(includes), List.copyOf(excludes), List.copyOf(roots), out, spoolDir, waitStartMs, recordStart);
        for (final RootSpec spec : cfg.roots()) {
            cfg.requireInstrumentable(spec);
        }
        return cfg;
    }

    private static void addRoots(final List<RootSpec> target, final String value) {
        for (final String s : value.split("\\+", -1)) {
            final String spec = s.trim();
            if (spec.isEmpty()) {
                continue;
            }
            target.add(RootSpec.parse(spec));
        }
        if (target.isEmpty()) {
            throw new IllegalArgumentException("roots= needs a method such as roots=pkg.Cls::method");
        }
    }

    private static void rejectStartupWithout(final List<RootSpec> roots, final String out, final String spoolDir, final long waitStartMs) {
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("record=startup needs roots=, as in roots=pkg.Cls::method: no JMX client sets them in this mode");
        }
        if (spoolDir != null) {
            throw new IllegalArgumentException("record=startup and spool= are mutually exclusive: a spool file is deleted when the JVM exits, so write the recording with out=");
        }
        if (out == null) {
            throw new IllegalArgumentException("record=startup needs out=, as in out=path/to/file.vbtm: the recording has to outlive the JVM");
        }
        if (waitStartMs > 0) {
            throw new IllegalArgumentException("record=startup and waitstart= are mutually exclusive: the recording starts at once, so there is nothing to wait for");
        }
    }

    public void requireInstrumentable(final RootSpec spec) {
        final String internal = spec.internalClassName();
        if (Transformer.isNeverInstrumented(internal) || !selects(internal)) {
            throw new IllegalArgumentException("root " + spec + " is not instrumentable: " + spec.className() + " is excluded from instrumentation");
        }
    }

    private static long parseWaitStart(final String value) {
        final String digits = value.endsWith("s") ? value.substring(0, value.length() - 1) : value;
        final long seconds;
        try {
            seconds = Long.parseLong(digits);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("waitstart= needs a number of seconds such as waitstart=60s, got '" + value + "'");
        }
        if (seconds <= 0) {
            throw new IllegalArgumentException("waitstart= needs a positive number of seconds, got '" + value + "'");
        }
        return seconds * 1000L;
    }

    private static boolean addPrefixesReportingIgnored(final List<String> target, final String value, final String key, final Consumer<String> warn) {
        boolean ignored = false;
        for (final String s : value.split("\\+", -1)) {
            final String p = s.trim();
            if (p.isEmpty()) {
                continue;
            }
            final String internal = p.replace('.', '/');
            rejectMalformedPrefix(internal, p, key);
            final String never = Transformer.neverInstrumentedPrefix(internal + "/");
            if (never != null) {
                warn.accept(key + "=" + p + " is ignored: classes under " + never.replace('/', '.') + "* are never instrumented, whichever jar they come from");
                ignored = true;
                continue;
            }
            target.add(internal);
        }
        return ignored;
    }

    private static void rejectMalformedPrefix(final String internal, final String given, final String key) {
        final String suggested = key + "=" + stripToSuggestion(given);
        if (internal.indexOf('*') >= 0) {
            final String everything = key.equals("include") ? " To instrument everything, omit include=." : "";
            throw new IllegalArgumentException(key + "=" + given + " is not a package or class prefix: wildcards are not supported, write "
                    + suggested + ". Sub-packages and nested classes are matched automatically." + everything);
        }
        if (internal.startsWith("/") || internal.endsWith("/") || internal.contains("//")) {
            throw new IllegalArgumentException(key + "=" + given + " is not a package or class prefix: empty package segment, write " + suggested);
        }
    }

    private static String stripToSuggestion(final String given) {
        String s = given.replace("*", "");
        while (s.startsWith(".") || s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith(".") || s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "com.example" : s;
    }

    public boolean selects(final String internalName) {
        if (!includes.isEmpty() && !matchesAny(includes, internalName)) {
            return false;
        }
        return !matchesAny(excludes, internalName);
    }

    private static boolean matchesAny(final List<String> prefixes, final String internalName) {
        for (int i = 0, n = prefixes.size(); i < n; i++) {
            final String p = prefixes.get(i);
            final int len = p.length();
            if (internalName.length() == len) {
                if (internalName.equals(p)) {
                    return true;
                }
            } else if (internalName.length() > len && internalName.startsWith(p)) {
                final char c = internalName.charAt(len);
                if (c == '/' || c == '$') {
                    return true;
                }
            }
        }
        return false;
    }

    public String includeText() {
        return includes.isEmpty() ? "*" : dotted(includes);
    }

    public String excludeText() {
        return dotted(excludes);
    }

    String describe() {
        final StringBuilder sb = new StringBuilder();
        sb.append("include=").append(includeText());
        sb.append(" exclude=").append(excludeText());
        if (!roots.isEmpty()) {
            sb.append(" roots=").append(rootsText());
        }
        if (out != null) {
            sb.append(" out=").append(out);
        }
        if (spoolDir != null) {
            sb.append(" spool=").append(spoolDir);
        }
        if (waitStartMs > 0) {
            sb.append(" waitstart=").append(waitStartMs / 1000).append('s');
        }
        sb.append(" record=").append(recordStart);
        return sb.toString();
    }

    public String rootsText() {
        final StringBuilder sb = new StringBuilder();
        for (final RootSpec s : roots) {
            if (sb.length() > 0) {
                sb.append('+');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static String dotted(final List<String> internal) {
        final StringBuilder sb = new StringBuilder();
        for (final String s : internal) {
            if (sb.length() > 0) {
                sb.append('+');
            }
            sb.append(s.replace('/', '.'));
        }
        return sb.toString();
    }
}
