import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Load {

    record Result(long requests, double seconds, double requestsPerSec, double p50, double p90, double p99, double p999,
            double max) {
    }

    private static final Pattern ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([^,\\s}]+)");

    private Load() {
    }

    static List<String> fixedRate(final String url, final int seconds, final int rate) {
        return args(url, "-z", seconds + "s", "-w", "-q", Integer.toString(rate), "--latency-correction", "-c", "16");
    }

    static List<String> closedLoop(final String url, final int seconds, final int connections) {
        return args(url, "-z", seconds + "s", "-w", "-c", Integer.toString(connections));
    }

    static List<String> count(final String url, final int requests) {
        return args(url, "-n", Integer.toString(requests), "-c", "1");
    }

    private static List<String> args(final String url, final String... options) {
        final List<String> args = new ArrayList<>(List.of(options));
        args.addAll(List.of("--no-tui", "--output-format", "json", url));
        return args;
    }

    static Result parse(final String json) {
        final String errors = section(json, "errorDistribution");
        if (!errors.isBlank()) {
            throw new IllegalStateException("oha reported request errors: " + errors.strip());
        }
        long requests = 0;
        final Matcher codes = ENTRY.matcher(section(json, "statusCodeDistribution"));
        while (codes.find()) {
            if (!codes.group(1).startsWith("2")) {
                throw new IllegalStateException("oha got HTTP " + codes.group(1) + " for " + codes.group(2) + " requests");
            }
            requests += Long.parseLong(codes.group(2));
        }
        if (requests == 0) {
            throw new IllegalStateException("oha completed no request");
        }
        final String summary = section(json, "summary");
        final String latency = section(json, "latencyPercentiles");
        return new Result(requests, number(summary, "total"), number(summary, "requestsPerSec"), number(latency, "p50"),
                number(latency, "p90"), number(latency, "p99"), number(latency, "p99.9"), number(summary, "slowest"));
    }

    private static String section(final String json, final String name) {
        final Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\\{([^{}]*)\\}").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("oha output has no flat \"" + name + "\" object");
        }
        return m.group(1);
    }

    private static double number(final String section, final String key) {
        final Matcher m = ENTRY.matcher(section);
        while (m.find()) {
            if (m.group(1).equals(key)) {
                return Double.parseDouble(m.group(2));
            }
        }
        throw new IllegalStateException("oha output has no \"" + key + "\" in " + section.strip());
    }
}
