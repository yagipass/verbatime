package io.github.yagipass.verbatime.agent;

import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.errorprone.annotations.Var;

final class CorpusVerifier {

    static final int BIG_METHOD = 32768;

    interface Sabotage {
        byte[] apply(String internalName, byte[] transformed);
    }

    enum Outcome {
        TRANSFORMED, UNCHANGED, REFUSED, ID_LIMIT
    }

    record Regression(String jar, String className, String method, Throwable error, Throwable originalError) {

        String describe() {
            String masked = originalError == null ? "" : "\n      (the original bytes fail too, but with " + summary(originalError) + ")";
            return className + " in " + jar + ", method " + method + ": " + error.getClass().getName() + ": " + indent(error.getMessage()) + masked;
        }
    }

    static final class JarReport {

        final String jar;

        int classes;

        int linked;

        int transformed;

        int unchanged;

        int refused;

        int idLimit;

        int jsrClasses;

        int bigMethods;

        int maxCode;

        long millis;

        final Map<Integer, Integer> majors = new TreeMap<>();

        final Set<String> originalFailures = new TreeSet<>();

        final Map<String, Integer> originalFailReasons = new HashMap<>();

        final Map<String, String> refusals = new TreeMap<>();

        final List<Regression> regressions = new ArrayList<>();

        final List<String> differs = new ArrayList<>();

        JarReport(String jar) {
            this.jar = jar;
        }
    }

    private CorpusVerifier() {
    }

    @SuppressWarnings("ReturnValueIgnored")
    static Throwable link(ClassLoader loader, String binaryName) {
        try {
            Class<?> c = Class.forName(binaryName, false, loader);
            // Called for its side effect: HotSpot links, and so verifies, a class before it hands out its methods, and does not initialize it.
            c.getDeclaredMethods();
            return null;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            return t;
        }
    }

    static List<JarReport> run(CorpusJars corpus, List<CorpusJars.Jar> subjects, int threads, Sabotage sabotage) throws InterruptedException, ExecutionException {
        PrintStream realErr = System.err;
        RefusalLog log = new RefusalLog(realErr);
        System.setErr(log);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            Map<CorpusJars.Jar, Future<JarReport>> futures = new LinkedHashMap<>();
            List<CorpusJars.Jar> largestFirst = new ArrayList<>(subjects);
            largestFirst.sort(Comparator.comparingLong(CorpusJars.Jar::bytes).reversed());
            for (CorpusJars.Jar j : largestFirst) {
                futures.put(j, pool.submit(() -> verify(corpus, j, sabotage, log)));
            }
            List<JarReport> out = new ArrayList<>();
            for (CorpusJars.Jar j : subjects) {
                out.add(futures.get(j).get());
            }
            return out;
        } finally {
            System.setErr(realErr);
        }
    }

    private static JarReport verify(CorpusJars corpus, CorpusJars.Jar jar, Sabotage sabotage, RefusalLog log) {
        long start = System.nanoTime();
        JarReport r = new JarReport(jar.name());
        Transformer tr = new Transformer(Config.parse(""), new Roots(), null);
        Map<String, Outcome> outcomes = new HashMap<>();
        CorpusLoader original = new CorpusLoader("corpus-original", corpus, jar, List.of(), null);
        CorpusLoader transformed = new CorpusLoader("corpus-transformed", corpus, jar, List.of(), (loader, internalName, bytes) -> {
            int failedBefore = tr.failedClasses();
            int limitedBefore = tr.idLimitSkippedClasses();
            @Var byte[] out = tr.transform(null, loader, internalName, null, null, bytes);
            @Var Outcome o = Outcome.UNCHANGED;
            if (out != null) {
                o = Outcome.TRANSFORMED;
            } else if (tr.failedClasses() > failedBefore) {
                o = Outcome.REFUSED;
            } else if (tr.idLimitSkippedClasses() > limitedBefore) {
                o = Outcome.ID_LIMIT;
            }
            outcomes.put(internalName, o);
            if (out != null && sabotage != null) {
                out = sabotage.apply(internalName, out);
            }
            return out;
        });
        for (String entry : jar.classEntries()) {
            String internal = entry.substring(0, entry.length() - ".class".length());
            String binary = internal.replace('/', '.');
            r.classes++;
            shape(r, jar.read(entry));
            Throwable eo = link(original, binary);
            Throwable ex = link(transformed, binary);
            Outcome o = outcomes.get(internal);
            if (o == Outcome.TRANSFORMED) {
                r.transformed++;
            } else if (o == Outcome.REFUSED) {
                r.refused++;
                r.refusals.put(binary, log.reason(internal));
            } else if (o == Outcome.ID_LIMIT) {
                r.idLimit++;
            } else {
                r.unchanged++;
            }
            if (eo == null && ex == null) {
                r.linked++;
            } else if (eo == null) {
                r.regressions.add(new Regression(jar.name(), binary, method(ex), ex, null));
            } else {
                r.originalFailures.add(binary);
                r.originalFailReasons.merge(summary(eo), 1, Integer::sum);
                if (ex != null && ex.getClass() != eo.getClass()) {
                    r.regressions.add(new Regression(jar.name(), binary, method(ex), ex, eo));
                } else if (ex == null) {
                    r.differs.add(binary + ": links only when transformed; the original fails with " + summary(eo));
                }
            }
        }
        r.millis = (System.nanoTime() - start) / 1_000_000;
        return r;
    }

    private static void shape(JarReport r, byte[] bytes) {
        int major = ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
        r.majors.merge(major, 1, Integer::sum);
        ClassModel cm;
        try {
            cm = ClassFile.of().parse(bytes);
        } catch (IllegalArgumentException e) {
            return;
        }
        @Var boolean jsr = false;
        for (MethodModel mm : cm.methods()) {
            if (!(mm.code().orElse(null) instanceof CodeAttribute ca)) {
                continue;
            }
            r.maxCode = Math.max(r.maxCode, ca.codeLength());
            if (ca.codeLength() >= BIG_METHOD) {
                r.bigMethods++;
            }
            if (major < ClassFile.JAVA_7_VERSION && !jsr) {
                for (CodeElement e : ca) {
                    if (e instanceof DiscontinuedInstruction) {
                        jsr = true;
                        break;
                    }
                }
            }
        }
        if (jsr) {
            r.jsrClasses++;
        }
    }

    private static final Pattern TYPE_CHECKER_LOCATION = Pattern.compile("Location:\\s+(\\S+) @");

    private static final Pattern OLD_VERIFIER_LOCATION = Pattern.compile("\\(class: (\\S+), method: (\\S+) signature: (\\S+)\\)");

    static final String UNNAMED = "(not named by the error)";

    static String method(Throwable t) {
        String msg = String.valueOf(t.getMessage());
        Matcher m = TYPE_CHECKER_LOCATION.matcher(msg);
        if (m.find()) {
            return m.group(1);
        }
        Matcher old = OLD_VERIFIER_LOCATION.matcher(msg);
        if (old.find()) {
            return old.group(1) + "." + old.group(2) + old.group(3);
        }
        return UNNAMED;
    }

    static String methodClass(String method) {
        int paren = method.indexOf('(');
        int dot = paren < 0 ? -1 : method.lastIndexOf('.', paren);
        return dot < 0 ? UNNAMED : method.substring(0, dot);
    }

    static String summary(Throwable t) {
        String msg = String.valueOf(t.getMessage());
        int nl = msg.indexOf('\n');
        String first = (nl < 0 ? msg : msg.substring(0, nl)).replaceAll("'corpus-[a-z]+'( @[0-9a-f]+)?", "<loader>").replaceAll("@[0-9a-f]{6,}", "@<id>");
        return t.getClass().getSimpleName() + ": " + (first.length() > 160 ? first.substring(0, 160) + "..." : first);
    }

    private static String indent(String s) {
        return String.valueOf(s).replace("\n", "\n      ");
    }

    static final class RefusalLog extends PrintStream {

        private static final String PREFIX = "[verbatime] WARN failed to instrument ";

        private static final String SEPARATOR = ", loading it unchanged: ";

        private final Map<String, String> reasons = new ConcurrentHashMap<>();

        RefusalLog(PrintStream out) {
            super(out, true, StandardCharsets.UTF_8);
        }

        @Override
        public void println(String x) {
            if (x != null && x.startsWith(PREFIX)) {
                int sep = x.indexOf(SEPARATOR);
                if (sep > 0) {
                    reasons.put(x.substring(PREFIX.length(), sep), x.substring(sep + SEPARATOR.length()));
                    return;
                }
            }
            super.println(x);
        }

        String reason(String internalName) {
            return reasons.getOrDefault(internalName, "(the Transformer logged no reason; see stderr)");
        }
    }
}
