package io.github.yagipass.verbatime.cli;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Args {

    final String command;

    final List<String> positionals = new ArrayList<>();

    private final Map<String, String> options = new LinkedHashMap<>();

    private Args(final String command) {
        this.command = command;
    }

    static Args parse(final String command, final List<String> argv, final Set<String> valued,
            final Set<String> flags) {
        final Args a = new Args(command);
        for (int i = 0; i < argv.size(); i++) {
            final String t = argv.get(i);
            if (!t.startsWith("--") || t.length() == 2) {
                a.positionals.add(t);
                continue;
            }
            final int eq = t.indexOf('=');
            final String name = eq < 0 ? t.substring(2) : t.substring(2, eq);
            if (flags.contains(name)) {
                if (eq >= 0) {
                    throw a.usage("--" + name + " takes no value");
                }
                a.options.put(name, "");
            } else if (valued.contains(name)) {
                final String v;
                if (eq >= 0) {
                    v = t.substring(eq + 1);
                } else if (i + 1 < argv.size()) {
                    v = argv.get(++i);
                } else {
                    throw a.usage("--" + name + " needs a value");
                }
                a.options.put(name, v);
            } else {
                throw a.usage("unknown option --" + name + " for " + command);
            }
        }
        return a;
    }

    boolean has(final String name) {
        return options.containsKey(name);
    }

    String value(final String name) {
        return options.get(name);
    }

    String choice(final String name, final String def, final String... allowed) {
        final String v = options.getOrDefault(name, def);
        for (final String a : allowed) {
            if (a.equals(v)) {
                return v;
            }
        }
        throw CliException.usage("--" + name + " must be one of " + String.join(", ", allowed) + ", not '" + v + "'",
                null);
    }

    int positiveInt(final String name, final int def) {
        return integer(name, def, 1, "a positive integer");
    }

    int nonNegativeInt(final String name, final int def) {
        return integer(name, def, 0, "0 or a positive integer");
    }

    private int integer(final String name, final int def, final int min, final String what) {
        final String v = options.get(name);
        if (v == null) {
            return def;
        }
        try {
            final int n = Integer.parseInt(v);
            if (n >= min) {
                return n;
            }
        } catch (final NumberFormatException notANumber) {
            throw CliException.usage("--" + name + " must be " + what + ", not '" + v + "'", null);
        }
        throw CliException.usage("--" + name + " must be " + what + ", not '" + v + "'", null);
    }

    long ticks(final String name, final long def) {
        final String v = options.get(name);
        return v == null ? def : parseTicks("--" + name, v);
    }

    String positional(final int i, final String what) {
        if (i >= positionals.size()) {
            throw usage("missing " + what);
        }
        return positionals.get(i);
    }

    String positionalOrNull(final int i) {
        return i < positionals.size() ? positionals.get(i) : null;
    }

    void rejectPositionalsBeyond(final int max) {
        if (positionals.size() > max) {
            throw usage("unexpected argument '" + positionals.get(max) + "'");
        }
    }

    String commandWith(final String name, final long value) {
        return commandWith(name, String.valueOf(value));
    }

    String commandWith(final String... pairs) {
        final Map<String, String> o = new LinkedHashMap<>(options);
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] == null) {
                o.remove(pairs[i]);
            } else {
                o.put(pairs[i], pairs[i + 1]);
            }
        }
        final StringBuilder sb = new StringBuilder("vbtm ").append(command);
        for (final String p : positionals) {
            sb.append(' ').append(shellQuote(p));
        }
        for (final Map.Entry<String, String> e : o.entrySet()) {
            sb.append(" --").append(e.getKey());
            if (!e.getValue().isEmpty()) {
                sb.append(' ').append(shellQuote(e.getValue()));
            }
        }
        return sb.toString();
    }

    private CliException usage(final String message) {
        return CliException.usage(message, "vbtm " + command + " --help");
    }

    static long parseTicks(final String what, final String v) {
        final String s = v.trim().toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
            i++;
        }
        final String num = s.substring(0, i);
        final String unit = s.substring(i);
        final long nsPerUnit = switch (unit) {
            case "ns" -> 1L;
            case "us", "µs" -> 1_000L;
            case "", "ms" -> 1_000_000L;
            case "s" -> 1_000_000_000L;
            default -> -1L;
        };
        if (num.isEmpty() || nsPerUnit < 0) {
            throw notADuration(what, v);
        }
        try {
            final BigDecimal ns = new BigDecimal(num).multiply(BigDecimal.valueOf(nsPerUnit));
            return ns.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).longValueExact();
        } catch (final NumberFormatException | ArithmeticException e) {
            throw notADuration(what, v);
        }
    }

    private static CliException notADuration(final String what, final String v) {
        return CliException.usage(what + " must be a duration such as 500us, 1ms or 2s, not '" + v + "'", null);
    }

    static String shellQuote(final String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || "-_./:=@,+%#".indexOf(c) >= 0)) {
                return "'" + s.replace("'", "'\\''") + "'";
            }
        }
        return s.isEmpty() ? "''" : s;
    }
}
