package io.github.yagipass.verbatime.agent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.errorprone.annotations.Var;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.TestMain;

final class CorpusVerifyTest {

    static final String THREADS_PROPERTY = "io.github.yagipass.verbatime.agent.test.corpus.threads";

    private CorpusVerifyTest() {
    }

    public static void main(String[] args) {
        TestMain.run("CorpusVerifyTest", CorpusVerifyTest::run);
        TestMain.report();
    }

    static int threads() {
        return Integer.getInteger(THREADS_PROPERTY, Math.min(4, Runtime.getRuntime().availableProcessors()));
    }

    static void run() throws Exception {
        try (CorpusJars corpus = CorpusJars.fromProperty()) {
            int threads = threads();
            long start = System.nanoTime();
            List<CorpusVerifier.JarReport> reports = CorpusVerifier.run(corpus, corpus.jars(), threads, null);
            long millis = (System.nanoTime() - start) / 1_000_000;
            print(reports, millis, threads);

            @Var int jsr = 0;
            @Var int big = 0;
            @Var int idLimit = 0;
            Map<Integer, Integer> majors = new TreeMap<>();
            List<CorpusVerifier.Regression> regressions = new ArrayList<>();
            for (CorpusVerifier.JarReport r : reports) {
                jsr += r.jsrClasses;
                big += r.bigMethods;
                idLimit += r.idLimit;
                r.majors.forEach((k, v) -> majors.merge(k, v, Integer::sum));
                regressions.addAll(r.regressions);
                Check.that(r.linked * 2 >= r.classes, r.jar + ": at least half its classes link with their original bytes, or most of it goes unchecked; put the jars it needs into the corpus");
            }
            for (CorpusVerifier.Regression reg : regressions) {
                Check.fail("REGRESSION " + reg.describe());
            }
            Check.eq(0, regressions.size(),
                    "every corpus class that links with its original bytes must link with its transformed bytes, because a VerifyError surfaces at link time, after transform() returned, where the agent cannot catch it and the application breaks");
            Check.eq(0, idLimit, "the corpus must fit under the method id limit, or the classes past it load untransformed and their instrumented bytes are never verified");
            Check.that(jsr > 0, "the corpus contains jsr/ret subroutines, which only pre-Java 7 class files may use and which instrumented methods must keep verifiable");
            Check.that(majors.keySet().stream().anyMatch(m -> m < 50), "the corpus contains class files older than Java 6, which carry no StackMapTable and are checked by the old inference verifier");
            Check.that(majors.containsKey(50), "the corpus contains Java 6 class files, which HotSpot checks against their StackMapTable and re-verifies with the old verifier when that fails");
            Check.that(majors.keySet().stream().anyMatch(m -> m >= 61), "the corpus contains Java 17+ class files, with the nestmates, records and invokedynamic that current frameworks compile to");
            Check.that(big > 0, "the corpus contains methods of 32 KiB or more, where code inserted by instrumentation can push branches out of 16-bit range or the method past 64 KiB");
        }
    }

    private static void print(List<CorpusVerifier.JarReport> reports, long millis, int threads) {
        @Var int classes = 0;
        @Var int linked = 0;
        @Var int originalFails = 0;
        @Var int transformed = 0;
        @Var int unchanged = 0;
        @Var int refused = 0;
        @Var int regressions = 0;
        @Var int jsr = 0;
        @Var int big = 0;
        @Var int maxCode = 0;
        Map<Integer, Integer> majors = new TreeMap<>();
        Map<String, Integer> failReasons = new TreeMap<>();
        Map<String, Integer> refusalReasons = new TreeMap<>();
        List<String> differs = new ArrayList<>();
        System.err.println("[corpus] " + String.format("%-44s %7s %7s %9s %7s %9s %7s %7s %5s %5s %7s  %s", "jar", "classes", "linked", "orig-fail", "instr", "unchanged", "refused", "regress", "jsr", ">=32K", "ms", "majors"));
        for (CorpusVerifier.JarReport r : reports) {
            System.err.println("[corpus] " + String.format("%-44s %7d %7d %9d %7d %9d %7d %7d %5d %5d %7d  %s", r.jar, r.classes, r.linked, r.originalFailures.size(), r.transformed, r.unchanged, r.refused, r.regressions.size(), r.jsrClasses,
                    r.bigMethods, r.millis, majors(r.majors)));
            classes += r.classes;
            linked += r.linked;
            originalFails += r.originalFailures.size();
            transformed += r.transformed;
            unchanged += r.unchanged;
            refused += r.refused;
            regressions += r.regressions.size();
            jsr += r.jsrClasses;
            big += r.bigMethods;
            maxCode = Math.max(maxCode, r.maxCode);
            r.majors.forEach((k, v) -> majors.merge(k, v, Integer::sum));
            r.originalFailReasons.forEach((k, v) -> failReasons.merge(k, v, Integer::sum));
            r.refusals.values().forEach(v -> refusalReasons.merge(v, 1, Integer::sum));
            differs.addAll(r.differs);
        }
        System.err.println("[corpus] " + String.format("%-44s %7d %7d %9d %7d %9d %7d %7d %5d %5d %7d", "total (" + reports.size() + " jars)", classes, linked, originalFails, transformed, unchanged, refused, regressions, jsr, big, millis));
        System.err.println("[corpus] major versions: " + majors(majors));
        System.err.println("[corpus] largest method: " + maxCode + " bytes of code; " + big + " methods of 32 KiB or more");
        System.err.println("[corpus] wall clock " + String.format("%.1f s", millis / 1000.0) + " on " + threads + " threads (mvn -Dcorpus.threads=N), metaspace peak " + metaspacePeakMb() + " MB, "
                + ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount() + " classes unloaded with their per-jar loaders");
        System.err.println("[corpus] " + originalFails + " classes skipped because their original bytes fail to link here; top reasons:");
        failReasons.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(15).forEach(e -> System.err.println("[corpus]   " + String.format("%6d  %s", e.getValue(), e.getKey())));
        System.err.println("[corpus] " + refused + " classes refused by the Transformer (loaded unchanged)" + (refused == 0 ? "" : "; reasons:"));
        refusalReasons.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(15).forEach(e -> System.err.println("[corpus]   " + String.format("%6d  %s", e.getValue(), e.getKey())));
        for (CorpusVerifier.JarReport r : reports) {
            r.refusals.entrySet().stream().limit(10).forEach(e -> System.err.println("[corpus]   refused " + e.getKey() + " (" + r.jar + "): " + e.getValue()));
        }
        if (!differs.isEmpty()) {
            System.err.println("[corpus] " + differs.size() + " classes link only when transformed (not a failure, but unexpected):");
            differs.stream().limit(20).forEach(d -> System.err.println("[corpus]   " + d));
        }
    }

    static String majors(Map<Integer, Integer> majors) {
        StringBuilder sb = new StringBuilder();
        majors.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : " ").append(k).append(':').append(v));
        return sb.toString();
    }

    private static long metaspacePeakMb() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().equals("Metaspace")) {
                return pool.getPeakUsage().getUsed() / (1024 * 1024);
            }
        }
        return -1;
    }
}
