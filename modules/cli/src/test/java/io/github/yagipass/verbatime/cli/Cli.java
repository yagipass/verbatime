package io.github.yagipass.verbatime.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Cli {

    record Result(int code, String out, String err) {

        List<String> lines() {
            return Arrays.asList(out.split("\n"));
        }

        String line(final String prefix) {
            for (final String l : lines()) {
                if (l.startsWith(prefix)) {
                    return l;
                }
            }
            throw new AssertionError("no line starting with '" + prefix + "' in:\n" + out);
        }

        List<String> rowsAfter(final String headerPrefix) {
            final List<String> lines = lines();
            return lines.subList(lines.indexOf(line(headerPrefix)) + 1, lines.size());
        }
    }

    private Cli() {
    }

    static Result vbtm(final Object... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final List<String> argv = new ArrayList<>();
        for (final Object a : args) {
            argv.add(a.toString());
        }
        final int code;
        try (PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = Main.run(argv, o, e);
        }
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
}
