package io.github.yagipass.verbatime.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Out {

    final boolean json;

    private final PrintStream stream;

    Out(final PrintStream stream, final boolean json) {
        this.stream = stream;
        this.json = json;
    }

    void text(final String line) {
        if (!json) {
            stream.println(line);
        }
    }

    void json(final Json object) {
        if (json) {
            stream.println(object);
        }
    }

    void more(final long count, final String what, final String next) {
        if (count <= 0) {
            return;
        }
        if (json) {
            stream.println(new Json("more").put("count", count).put("what", what).put("next", next));
        } else {
            stream.println("# " + Formats.grouped(count) + " more " + what + ". next: " + next);
        }
    }

    void status(final TraceFile file) {
        if (file.status == TraceFile.Status.COMPLETE) {
            return;
        }
        if (json) {
            stream.println(new Json("status").put("status", file.status.name().toLowerCase(Locale.ROOT))
                    .put("detail", file.statusText()));
        } else {
            stream.println("# status: " + file.statusText()
                    + (file.status == TraceFile.Status.TRUNCATED
                            ? ", the recording stops mid-record and calls still open there are marked ~"
                            : ", everything after that offset is missing"));
        }
    }

    static final class Table {

        private final String[] headers;

        private final boolean[] right;

        private final List<String[]> rows = new ArrayList<>();

        Table(final String... spec) {
            headers = new String[spec.length];
            right = new boolean[spec.length];
            for (int i = 0; i < spec.length; i++) {
                right[i] = spec[i].startsWith(">");
                headers[i] = right[i] ? spec[i].substring(1) : spec[i];
            }
        }

        void add(final String... row) {
            rows.add(row);
        }

        void print(final Out out) {
            if (out.json) {
                return;
            }
            final int[] widths = new int[headers.length];
            for (int i = 0; i < headers.length; i++) {
                widths[i] = headers[i].length();
            }
            for (final String[] r : rows) {
                for (int i = 0; i < r.length; i++) {
                    widths[i] = Math.max(widths[i], r[i].length());
                }
            }
            out.text(line(headers, widths));
            for (final String[] r : rows) {
                out.text(line(r, widths));
            }
        }

        private String line(final String[] cells, final int[] widths) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    sb.append("  ");
                }
                if (i == cells.length - 1 && !right[i]) {
                    sb.append(cells[i]);
                    break;
                }
                final int pad = widths[i] - cells[i].length();
                if (right[i]) {
                    sb.append(" ".repeat(pad)).append(cells[i]);
                } else {
                    sb.append(cells[i]).append(" ".repeat(pad));
                }
            }
            return sb.toString().stripTrailing();
        }
    }
}
