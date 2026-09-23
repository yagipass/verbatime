package io.github.yagipass.verbatime.agent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import io.github.yagipass.verbatime.agent.jmx.VerbatimeControl;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.TraceFileWriter;
import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.DecodedTrace;
import io.github.yagipass.verbatime.agent.test.TestMain;
import io.github.yagipass.verbatime.format.Vbtm;

final class MethodIdLimitTest {

    private static final int MAX = Vbtm.METHOD_ID_LIMIT;

    private MethodIdLimitTest() {
    }

    public static void main(final String[] args) {
        TestMain.run("MethodIdLimitTest", MethodIdLimitTest::run);
        TestMain.report();
    }

    static void run() throws Exception {
        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
        Files.createDirectories(tmp);

        final int filler = MethodRegistry.reserveIds("test.limit.Filler", Collections.nCopies(MAX - 2, "f()V"));
        Check.eq(0, filler, "a fresh JVM hands out ids from zero");

        Check.eq(MethodRegistry.LIMIT_REACHED, MethodRegistry.reserveIds("test.limit.Straddle", List.of("a()V", "b()V", "c()V")), "a class that would cross the limit is refused, since readers reject a block whose baseId + count passes 2^22");
        Check.eq(MAX - 2, MethodRegistry.size(), "a refused class takes no ids, so no partial block can reach a recording");

        final List<String> exactSigs = List.of("x()V", "y()V");
        final int exact = MethodRegistry.reserveIds("test.limit.Exact", exactSigs);
        Check.eq(MAX - 2, exact, "a class ending exactly at the limit is accepted, as readers accept it");
        Check.eq(MAX, MethodRegistry.size(), "the registry is now full");
        MethodRegistry.commitClass(exact, "test.limit.Exact", exactSigs);

        final Config cfg = Config.parse("include=io.github.yagipass.verbatime.fixtures");
        final Roots roots = new Roots();
        roots.replaceRoots(List.of(RootSpec.parse("io.github.yagipass.verbatime.fixtures.Fixture::root")));
        final Transformer tr = new Transformer(cfg, roots, null);
        final TransformingLoader loader = new TransformingLoader(tr, "io.github.yagipass.verbatime.fixtures.", TransformingLoader::classpathBytes);

        final byte[][] gated = new byte[1][];
        final String err = Check.captureStderr(() -> {
            loader.loadClass("io.github.yagipass.verbatime.fixtures.Fixture");
            loader.loadClass("io.github.yagipass.verbatime.fixtures.Fixture$Inner");
            gated[0] = tr.instrument("io/github/yagipass/verbatime/fixtures/GateMain", TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.GateMain"), true);
        });
        final Class<?> fx = loader.loadClass("io.github.yagipass.verbatime.fixtures.Fixture");

        final String expected = new io.github.yagipass.verbatime.fixtures.Fixture().root();
        Check.eq(expected, fx.getMethod("root").invoke(fx.getConstructor().newInstance()), "a skipped class still loads and runs, because the application must not break when tracing gives up");
        Check.that(!hasBodyMethod(fx), "a skipped class is loaded unchanged, so its bytecode never refers to an id past the limit");
        Check.eq(0, tr.instrumentedClasses(), "nothing is instrumented once the registry is full");
        Check.eq(0, tr.failedClasses(), "hitting the limit is not a transform failure");
        Check.that(tr.idLimitSkippedClasses() >= 3, "Fixture, Fixture$Inner and GateMain are counted as skipped, got " + tr.idLimitSkippedClasses());
        Check.eq(1, Check.occurrences(err, "WARN method id limit"), "the limit is warned about once, not once per class load");

        Check.that(gated[0] != null, "the startup gate is still injected into a skipped main class, so waitstart keeps holding the JVM");
        final ClassModel gm = ClassFile.of().parse(gated[0]);
        Check.that(callsGateAwait(gm), "GateMain.main calls StartupGate.await");
        Check.that(gm.methods().stream().noneMatch(m -> m.methodName().stringValue().endsWith(Transformer.BODY_SUFFIX)), "the gated main class carries no instrumented wrappers");

        final VerbatimeControl ctl = new VerbatimeControl(cfg, tr, roots, new Recorder());
        Check.that(List.of(ctl.status()).contains("idLimitSkippedClasses=" + tr.idLimitSkippedClasses()), "status reports skipped classes, so a JMX client can see why methods are missing without reading stderr");

        final TraceFileWriter w = TraceFileWriter.open(tmp.resolve("limit.vbtm"));
        MethodRegistry.attach(w);
        MethodRegistry.detach();
        w.close();
        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.that(d.cleanEnd, "a recording started after the limit was reached is still readable");
        Check.eq("test.limit.Exact.y()V", d.methodNames.get(MAX - 1), "the last valid id is replayed into the recording");
    }

    private static boolean hasBodyMethod(final Class<?> c) {
        for (final Method m : c.getDeclaredMethods()) {
            if (m.getName().endsWith(Transformer.BODY_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean callsGateAwait(final ClassModel cm) {
        for (final MethodModel mm : cm.methods()) {
            if (!mm.methodName().equalsString("main") || mm.code().isEmpty()) {
                continue;
            }
            for (final CodeElement ce : mm.code().get()) {
                if (ce instanceof final InvokeInstruction ii && ii.name().equalsString("await") && ii.owner().asInternalName().equals("io/github/yagipass/verbatime/agent/probe/StartupGate")) {
                    return true;
                }
            }
        }
        return false;
    }
}
