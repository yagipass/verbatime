import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

final class Report {

    record Startup(int round, String variant, double seconds) {
    }

    record Sample(int round, String variant, String endpoint, int targetRate, Load.Result fixed, double cpuMsPerRequest,
            double gcCountPer1k, double gcMsPer1k, double saturationRps, double bytesPerRequest, double eventsPerRequest) {

        boolean saturated() {
            return fixed.requestsPerSec() < SATURATION_RATIO * targetRate;
        }
    }

    static final String BASELINE = "A";

    static final String RECORDING = "D";

    private static final double SATURATION_RATIO = 0.97;

    private final List<String> header;

    private final List<Startup> startups;

    private final List<Sample> samples;

    private final StringBuilder out = new StringBuilder();

    private Report(final List<String> header, final List<Startup> startups, final List<Sample> samples) {
        this.header = header;
        this.startups = startups;
        this.samples = samples;
    }

    static String render(final List<String> header, final List<Startup> startups, final List<Sample> samples) {
        return new Report(header, startups, samples).render();
    }

    static String csv(final List<Sample> samples) {
        final StringBuilder csv = new StringBuilder("round,variant,endpoint,targetRate,requests,achievedRps,p50s,p90s,p99s,p999s,maxs,"
                + "cpuMsPerRequest,gcCountPer1k,gcMsPer1k,saturationRps,bytesPerRequest,eventsPerRequest\n");
        for (final Sample s : samples) {
            final Load.Result f = s.fixed();
            csv.append(String.join(",", Integer.toString(s.round()), s.variant(), s.endpoint(), Integer.toString(s.targetRate()),
                    Long.toString(f.requests()), Double.toString(f.requestsPerSec()), Double.toString(f.p50()),
                    Double.toString(f.p90()), Double.toString(f.p99()), Double.toString(f.p999()), Double.toString(f.max()),
                    Double.toString(s.cpuMsPerRequest()), Double.toString(s.gcCountPer1k()), Double.toString(s.gcMsPer1k()),
                    Double.toString(s.saturationRps()), Double.toString(s.bytesPerRequest()),
                    Double.toString(s.eventsPerRequest()))).append('\n');
        }
        return csv.toString();
    }

    private String render() {
        line("# Verbatime agent overhead");
        line("");
        header.forEach(h -> line("- " + h));
        line("");
        line("Cells are the median over rounds with [min–max]. A = no agent, B = agent attached and idle,");
        line("D = agent recording every request. Deltas are taken against A within the same round.");
        line("");
        line("## Startup");
        line("");
        line("| variant | process running for, s |");
        line("|---|---|");
        for (final String variant : variants()) {
            line("| " + variant + " | " + cell(startups.stream().filter(s -> s.variant().equals(variant)).mapToDouble(Startup::seconds).toArray(), "%.2f") + " |");
        }
        for (final String endpoint : samples.stream().map(Sample::endpoint).distinct().toList()) {
            endpoint(endpoint);
        }
        costModel();
        return out.toString();
    }

    private void endpoint(final String endpoint) {
        final List<Sample> here = samples.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
        line("");
        line("## " + endpoint);
        line("");
        line("### Fixed rate, " + here.get(0).targetRate() + " req/s");
        line("");
        line("| variant | req/s | p50 ms | p90 ms | p99 ms | p99.9 ms | max ms | Δp50 ms | CPU ms/req | ΔCPU ms/req | GCs /1k req | GC ms /1k req |");
        line("|---|---|---|---|---|---|---|---|---|---|---|---|");
        for (final String variant : variants()) {
            final List<Sample> rows = of(here, variant);
            line("| " + variant + flag(here, variant)
                    + " | " + cell(rows, s -> s.fixed().requestsPerSec(), "%.0f")
                    + " | " + cell(rows, s -> s.fixed().p50() * 1e3, "%.3f")
                    + " | " + cell(rows, s -> s.fixed().p90() * 1e3, "%.3f")
                    + " | " + cell(rows, s -> s.fixed().p99() * 1e3, "%.3f")
                    + " | " + cell(rows, s -> s.fixed().p999() * 1e3, "%.3f")
                    + " | " + cell(rows, s -> s.fixed().max() * 1e3, "%.1f")
                    + " | " + cell(deltas(here, variant, s -> s.fixed().p50() * 1e3), "%+.3f")
                    + " | " + cell(rows, Sample::cpuMsPerRequest, "%.3f")
                    + " | " + cell(deltas(here, variant, Sample::cpuMsPerRequest), "%+.3f")
                    + " | " + cell(rows, Sample::gcCountPer1k, "%.2f")
                    + " | " + cell(rows, Sample::gcMsPer1k, "%.2f") + " |");
        }
        line("");
        line("### Saturation, 64 connections");
        line("");
        line("| variant | req/s | vs A |");
        line("|---|---|---|");
        for (final String variant : variants()) {
            line("| " + variant + " | " + cell(of(here, variant), Sample::saturationRps, "%.0f") + " | "
                    + cell(ratios(here, variant), "%.2f×") + " |");
        }
        final List<Sample> recorded = of(here, RECORDING);
        if (recorded.isEmpty()) {
            return;
        }
        line("");
        line("### Recording, variant D");
        line("");
        line("| bytes/request | events/request |");
        line("|---|---|");
        line("| " + cell(recorded, Sample::bytesPerRequest, "%.0f") + " | " + cell(recorded, Sample::eventsPerRequest, "%.0f") + " |");
    }

    private void costModel() {
        final List<double[]> latency = points(s -> s.fixed().p50() * 1e6);
        final List<double[]> cpu = points(s -> s.cpuMsPerRequest() * 1e3);
        if (latency.stream().mapToDouble(p -> p[0]).distinct().count() < 2) {
            return;
        }
        line("");
        line("## Cost of recording one request, D − A");
        line("");
        line("A least-squares line over every unsaturated round and endpoint pair: extra µs per request =");
        line("per-request + per-event × events/request. With two endpoints the line passes through two");
        line("clusters of points, so read it as an estimate, not as a measured law.");
        line("");
        line("| measure | points | µs per request | ns per event |");
        line("|---|---|---|---|");
        line(fit("p50 latency", latency));
        line(fit("CPU time", cpu));
    }

    private List<double[]> points(final ToDoubleFunction<Sample> micros) {
        final List<double[]> points = new ArrayList<>();
        for (final Sample s : samples) {
            for (final Sample base : samples) {
                if (s.variant().equals(RECORDING) && base.variant().equals(BASELINE) && base.round() == s.round()
                        && base.endpoint().equals(s.endpoint()) && !s.saturated() && !base.saturated()) {
                    points.add(new double[] { s.eventsPerRequest(), micros.applyAsDouble(s) - micros.applyAsDouble(base) });
                }
            }
        }
        return points;
    }

    private static String fit(final String measure, final List<double[]> points) {
        final double meanX = points.stream().mapToDouble(p -> p[0]).average().orElseThrow();
        final double meanY = points.stream().mapToDouble(p -> p[1]).average().orElseThrow();
        final double sxy = points.stream().mapToDouble(p -> (p[0] - meanX) * (p[1] - meanY)).sum();
        final double sxx = points.stream().mapToDouble(p -> (p[0] - meanX) * (p[0] - meanX)).sum();
        final double slope = sxy / sxx;
        return String.format(Locale.ROOT, "| %s | %d | %.0f | %.1f |", measure, points.size(), meanY - slope * meanX, slope * 1e3);
    }

    private List<String> variants() {
        return samples.stream().map(Sample::variant).distinct().sorted().toList();
    }

    private static List<Sample> of(final List<Sample> here, final String variant) {
        return here.stream().filter(s -> s.variant().equals(variant)).toList();
    }

    private static String flag(final List<Sample> here, final String variant) {
        return of(here, variant).stream().anyMatch(Sample::saturated) ? " SATURATED" : "";
    }

    private static double[] deltas(final List<Sample> here, final String variant, final ToDoubleFunction<Sample> value) {
        final List<Double> deltas = new ArrayList<>();
        if (!variant.equals(BASELINE)) {
            for (final Sample s : of(here, variant)) {
                for (final Sample base : of(here, BASELINE)) {
                    if (base.round() == s.round() && !s.saturated() && !base.saturated()) {
                        deltas.add(value.applyAsDouble(s) - value.applyAsDouble(base));
                    }
                }
            }
        }
        return deltas.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[] ratios(final List<Sample> here, final String variant) {
        final List<Double> ratios = new ArrayList<>();
        for (final Sample s : of(here, variant)) {
            for (final Sample base : of(here, BASELINE)) {
                if (base.round() == s.round()) {
                    ratios.add(s.saturationRps() / base.saturationRps());
                }
            }
        }
        return ratios.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static String cell(final List<Sample> rows, final ToDoubleFunction<Sample> value, final String format) {
        return cell(rows.stream().mapToDouble(value).toArray(), format);
    }

    private static String cell(final double[] values, final String format) {
        if (values.length == 0) {
            return "—";
        }
        final double[] sorted = values.clone();
        Arrays.sort(sorted);
        final double median = median(sorted);
        if (sorted.length == 1) {
            return String.format(Locale.ROOT, format, median);
        }
        return String.format(Locale.ROOT, format + " [" + format + "–" + format + "]", median, sorted[0], sorted[sorted.length - 1]);
    }

    static double median(final double[] sorted) {
        final int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    private void line(final String text) {
        out.append(text).append('\n');
    }
}
