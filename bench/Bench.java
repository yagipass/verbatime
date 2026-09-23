import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Bench {

    private record Endpoint(String name, String path) {
    }

    private static final String EXAMPLE_COMPOSE = "examples/jdbc-hibernate/compose.yaml";

    private static final String BENCH_COMPOSE = "bench/compose.bench.yaml";

    private static final String JMX_ADDRESS = "localhost:7091";

    private static final String JMX_FLAGS = "-Dcom.sun.management.jmxremote.port=7091 -Dcom.sun.management.jmxremote.rmi.port=7091"
            + " -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
            + " -Djava.rmi.server.hostname=localhost";

    private static final String AGENT_FLAG = "-javaagent:/app/verbatime-agent.jar";

    private static final Endpoint SEED = new Endpoint("orders", "/orders?sku=widget&qty=3");

    private static final int SEED_REQUESTS = 50;

    private static final int CALIBRATION_REQUESTS = 200;

    private static final int WARMUP_CONNECTIONS = 16;

    private static final int SATURATION_CONNECTIONS = 64;

    private static final int PINNED_CPUS = 8;

    private static final Pattern STARTED = Pattern.compile("Started \\S+ in [0-9.]+ seconds \\(process running for ([0-9.]+)\\)");

    private int rounds = 5;

    private int duration = 30;

    private int warmup = 30;

    private int rate = 200;

    private String root = "org.springframework.web.servlet.DispatcherServlet::doDispatch";

    private List<String> variants = List.of("A", "B", "D");

    private final List<Endpoint> endpoints = new ArrayList<>(List.of(new Endpoint("healthz", "/healthz"), new Endpoint("orders-recent", "/orders/recent")));

    private final Map<String, String> pins = new HashMap<>(Map.of("app", "0-3", "db", "4-5", "load", "6-7"));

    private boolean pinned = true;

    private Path results;

    private volatile Map<String, String> upEnv;

    private final List<Report.Startup> startups = new ArrayList<>();

    private final List<Report.Sample> samples = new ArrayList<>();

    private Bench() {
    }

    public static void main(final String[] args) throws Exception {
        final Bench bench = new Bench();
        bench.parse(args);
        bench.run();
    }

    private void parse(final String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--quick" -> {
                    rounds = 1;
                    duration = 10;
                    warmup = 10;
                }
                case "--rounds" -> rounds = Integer.parseInt(args[++i]);
                case "--duration" -> duration = Integer.parseInt(args[++i]);
                case "--warmup" -> warmup = Integer.parseInt(args[++i]);
                case "--rate" -> rate = Integer.parseInt(args[++i]);
                case "--root" -> root = args[++i];
                case "--variants" -> variants = List.of(args[++i].split(","));
                case "--with-orders" -> endpoints.add(SEED);
                case "--no-pin" -> pinned = false;
                case "--pin" -> {
                    for (final String pin : args[++i].split(",(?=[a-z]+=)")) {
                        final int eq = pin.indexOf('=');
                        if (eq <= 0 || !pins.containsKey(pin.substring(0, eq))) {
                            throw new IllegalArgumentException("--pin takes app=..,db=..,load=.., got '" + pin + "'");
                        }
                        pins.put(pin.substring(0, eq), pin.substring(eq + 1));
                    }
                }
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (!variants.contains(Report.BASELINE) || !List.of("A", "B", "D").containsAll(variants)) {
            throw new IllegalArgumentException("--variants takes A, B and D and must include A, got " + variants);
        }
    }

    private void run() throws Exception {
        if (!Files.exists(Path.of(EXAMPLE_COMPOSE)) || !Files.exists(Path.of(BENCH_COMPOSE))) {
            throw new IllegalStateException("run from the repository root: " + EXAMPLE_COMPOSE + " not found");
        }
        final String docker = exec(Map.of(), "docker", "info", "--format", "{{.NCPU}} CPUs, {{.MemTotal}} bytes, {{.OperatingSystem}}, {{.Architecture}}").strip();
        if (pinned && Integer.parseInt(docker.substring(0, docker.indexOf(' '))) < PINNED_CPUS && pins.equals(Map.of("app", "0-3", "db", "4-5", "load", "6-7"))) {
            throw new IllegalStateException("the default pinning needs " + PINNED_CPUS + " CPUs but Docker has " + docker
                    + ". Pass --pin app=..,db=..,load=.. or --no-pin");
        }
        results = Path.of("bench/results", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(results);
        Runtime.getRuntime().addShutdownHook(new Thread(this::downAfterInterrupt, "bench-down"));

        final List<String> header = List.of(
                "date: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "commit: " + exec(Map.of(), "git", "rev-parse", "--short", "HEAD").strip() + (exec(Map.of(), "git", "status", "--porcelain").isBlank() ? "" : " + uncommitted changes"),
                "docker: " + docker,
                "host: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                "cpuset: " + (pinned ? "app=" + pins.get("app") + " db=" + pins.get("db") + " load=" + pins.get("load") : "not pinned"),
                "rounds=" + rounds + " duration=" + duration + "s warmup=" + warmup + "s rate=" + rate + "/s root=" + root);
        header.forEach(System.out::println);

        System.out.println("building the image ...");
        compose(env("A"), "build", "app");
        compose(env("A"), "down", "-v");
        for (int round = 1; round <= rounds; round++) {
            final List<String> order = new ArrayList<>(variants);
            Collections.rotate(order, -(round - 1));
            for (final String variant : order) {
                runVariant(round, variant);
                Files.writeString(results.resolve("samples.csv"), Report.csv(samples));
            }
        }
        final String summary = Report.render(header, startups, samples);
        Files.writeString(results.resolve("summary.md"), summary);
        System.out.println();
        System.out.println(summary);
        System.out.println("written to " + results);
    }

    private void runVariant(final int round, final String variant) throws Exception {
        final Map<String, String> env = env(variant);
        final boolean recording = variant.equals(Report.RECORDING);
        System.out.println("round " + round + " variant " + variant + ": starting");
        upEnv = env;
        try {
            compose(env, "up", "-d", "--wait");
            final Matcher started = STARTED.matcher(compose(env, "logs", "--no-color", "app"));
            if (!started.find()) {
                throw new IllegalStateException("the app log has no 'Started ... (process running for ...)' line");
            }
            startups.add(new Report.Startup(round, variant, Double.parseDouble(started.group(1))));

            try (Control control = Control.connect(JMX_ADDRESS)) {
                if (control.hasAgent() == variant.equals(Report.BASELINE)) {
                    throw new IllegalStateException("variant " + variant + " has agent=" + control.hasAgent());
                }
                load(env, round + "-" + variant + "-seed", Load.count(url(SEED), SEED_REQUESTS));
                if (recording) {
                    control.replaceRoots(root);
                    final String resolved = control.status().get("root.0");
                    if (resolved == null || !resolved.startsWith("ok ")) {
                        throw new IllegalStateException("root " + root + " is not instrumented: root.0=" + resolved);
                    }
                }
                for (final Endpoint endpoint : endpoints) {
                    samples.add(measure(round, variant, endpoint, env, control));
                }
                if (!variant.equals(Report.BASELINE) && !recording && !"idle".equals(control.status().get("state"))) {
                    throw new IllegalStateException("variant " + variant + " must stay idle, got state=" + control.status().get("state"));
                }
            }
        } finally {
            compose(env, "down", "-v");
            upEnv = null;
        }
    }

    private Report.Sample measure(final int round, final String variant, final Endpoint endpoint, final Map<String, String> env,
            final Control control) throws Exception {
        final boolean recording = variant.equals(Report.RECORDING);
        final String tag = round + "-" + variant + "-" + endpoint.name();
        System.out.println("round " + round + " variant " + variant + ": " + endpoint.path());
        if (recording) {
            control.startRecording(tag);
        }
        final Load.Result warm = load(env, tag + "-warmup", Load.closedLoop(url(endpoint), warmup, WARMUP_CONNECTIONS));

        final long cpu0 = control.processCpuNanos();
        final Control.Gc gc0 = control.gc();
        final Load.Result fixed = load(env, tag + "-fixed", Load.fixedRate(url(endpoint), duration, rate));
        final long cpu1 = control.processCpuNanos();
        final Control.Gc gc1 = control.gc();

        final Load.Result saturation = load(env, tag + "-saturation", Load.closedLoop(url(endpoint), duration, SATURATION_CONNECTIONS));

        double bytesPerRequest = Double.NaN;
        double eventsPerRequest = Double.NaN;
        if (recording) {
            bytesPerRequest = (double) stopRecording(control) / (warm.requests() + fixed.requests() + saturation.requests());
            eventsPerRequest = calibrate(tag, endpoint, env, control);
        }
        final double thousands = fixed.requests() / 1e3;
        return new Report.Sample(round, variant, endpoint.path(), rate, fixed, (cpu1 - cpu0) / 1e6 / fixed.requests(),
                (gc1.count() - gc0.count()) / thousands, (gc1.millis() - gc0.millis()) / thousands, saturation.requestsPerSec(),
                bytesPerRequest, eventsPerRequest);
    }

    private double calibrate(final String tag, final Endpoint endpoint, final Map<String, String> env, final Control control)
            throws Exception {
        final long id = control.startRecording(tag + "-calibration");
        load(env, tag + "-calibration", Load.count(url(endpoint), CALIBRATION_REQUESTS));
        stopRecording(control);
        final long[] events = TraceStats.eventsPerSession(control.download(id));
        if (events.length < CALIBRATION_REQUESTS) {
            throw new IllegalStateException("the calibration recording has " + events.length + " sessions for " + CALIBRATION_REQUESTS + " requests");
        }
        return Report.median(Arrays.stream(events).sorted().asDoubleStream().toArray());
    }

    private static long stopRecording(final Control control) throws Exception {
        if (control.status().containsKey("recording.truncated")) {
            throw new IllegalStateException("the agent stopped writing the recording while it ran");
        }
        control.stopRecording();
        final Map<String, String> status = control.status();
        final long bytes = Long.parseLong(status.getOrDefault("lastRecording.bytes", "0"));
        if (status.containsKey("lastRecording.truncated") || bytes == 0) {
            throw new IllegalStateException("the recording is truncated or empty: " + status);
        }
        return bytes;
    }

    private Load.Result load(final Map<String, String> env, final String tag, final List<String> ohaArgs) throws Exception {
        final List<String> command = new ArrayList<>(List.of("run", "--rm", "--no-deps", "-T", "load"));
        command.addAll(ohaArgs);
        final String json = compose(env, command.toArray(String[]::new));
        Files.writeString(results.resolve(tag + ".json"), json);
        return Load.parse(json);
    }

    private static String url(final Endpoint endpoint) {
        return "http://app:8080" + endpoint.path();
    }

    private Map<String, String> env(final String variant) {
        final Map<String, String> env = new HashMap<>();
        env.put("BENCH_JAVA_TOOL_OPTIONS", variant.equals(Report.BASELINE) ? JMX_FLAGS : AGENT_FLAG + " " + JMX_FLAGS);
        env.put("BENCH_CPUS_APP", pinned ? pins.get("app") : "");
        env.put("BENCH_CPUS_DB", pinned ? pins.get("db") : "");
        env.put("BENCH_CPUS_LOAD", pinned ? pins.get("load") : "");
        return env;
    }

    private void downAfterInterrupt() {
        final Map<String, String> env = upEnv;
        if (env != null) {
            try {
                compose(env, "down", "-v");
            } catch (final Exception e) {
                System.err.println("could not stop the containers: " + e);
            }
        }
    }

    private String compose(final Map<String, String> env, final String... args) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>(List.of("docker", "compose", "-p", "verbatime-bench", "-f", EXAMPLE_COMPOSE, "-f", BENCH_COMPOSE));
        command.addAll(List.of(args));
        return exec(env, command.toArray(String[]::new));
    }

    private String exec(final Map<String, String> env, final String... command) throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(env);
        final File log = results == null ? null : results.resolve("commands.log").toFile();
        builder.redirectError(log == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.appendTo(log));
        final Process process = builder.start();
        final String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " exited with " + process.exitValue()
                    + (log == null ? "" : ", see " + log));
        }
        return stdout;
    }
}
