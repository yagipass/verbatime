package io.github.yagipass.verbatime.agent;

import java.io.ObjectStreamClass;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileVersion;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.agent.probe.ExceptionRegistry;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.TraceFileWriter;
import io.github.yagipass.verbatime.agent.probe.Tracing;
import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.DecodedTrace;

public final class TransformerTest {

    private TransformerTest() {
    }

    public static void run() throws Exception {
        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
        Files.createDirectories(tmp);
        final TraceFileWriter out = TraceFileWriter.open(tmp.resolve("unit.vbtm"));
        Tracing.start(out);

        final Config cfg = Config.parse("include=io.github.yagipass.verbatime.fixtures");
        final List<RootSpec> fxRoots = List.of(RootSpec.parse("io.github.yagipass.verbatime.fixtures.Fixture::root"), RootSpec.parse("io.github.yagipass.verbatime.fixtures.Fixture::rootThrows"));
        final Roots roots = new Roots();
        roots.replaceRoots(fxRoots);
        final Transformer tr = new Transformer(cfg, roots, null);
        final TransformingLoader loader = new TransformingLoader(tr, "io.github.yagipass.verbatime.fixtures.", TransformingLoader::classpathBytes);

        final Class<?> fx = loader.loadClass("io.github.yagipass.verbatime.fixtures.Fixture");
        Check.that(fx.getClassLoader() == loader, "Fixture defined by the child-first loader");
        final Object o = fx.getConstructor().newInstance();
        final String expected = new io.github.yagipass.verbatime.fixtures.Fixture().root();
        Check.eq(expected, fx.getMethod("root").invoke(o), "transformed root() result");
        Check.that(roots.isResolved(fxRoots.get(0)), "root resolved at transform time");
        Check.that(tr.instrumentedClasses() >= 3, "Fixture, FixtureInterface and Fixture$Inner instrumented");

        final Method rootM = fx.getMethod("root");
        Check.eq(1, rootM.getAnnotations().length, "method annotation kept on the wrapper");
        Check.eq("io.github.yagipass.verbatime.fixtures.Marker", rootM.getAnnotations()[0].annotationType().getName(), "annotation type");
        final Method ap = fx.getDeclaredMethod("annotatedParam", String.class);
        Check.eq(1, ap.getParameterAnnotations()[0].length, "parameter annotation kept on the wrapper");
        final Method sync = fx.getDeclaredMethod("sync", int.class);
        Check.that(Modifier.isSynchronized(sync.getModifiers()), "wrapper keeps synchronized so reflection and the default serialVersionUID match the original class");
        final Method syncBody = fx.getDeclaredMethod("sync$trace", int.class);
        Check.that(!Modifier.isSynchronized(syncBody.getModifiers()), "body runs under the wrapper's monitor and needs no lock of its own");
        Check.that(Modifier.isPrivate(syncBody.getModifiers()), "body is private");
        Check.that(syncBody.isSynthetic(), "body is synthetic");
        Check.that(Modifier.isPublic(fx.getDeclaredMethod("stat", int.class, long.class).getModifiers()), "public static wrapper stays public");
        Check.that(Modifier.isStatic(fx.getDeclaredMethod("stat$trace", int.class, long.class).getModifiers()), "static body stays static");
        Check.eq(1, fx.getDeclaredConstructors().length, "constructor untouched");
        for (int i = 0; i < MethodRegistry.size(); i++) {
            if (MethodRegistry.methodName(i).startsWith("<")) {
                Check.fail("constructor registered: " + MethodRegistry.displayName(i));
            }
        }

        final Class<?> serial = loader.loadClass("io.github.yagipass.verbatime.fixtures.SerializableFixture");
        Check.that(serial.getClassLoader() == loader, "SerializableFixture defined by the child-first loader");
        Check.that(Modifier.isPrivate(serial.getDeclaredMethod("increment$trace").getModifiers()), "SerializableFixture.increment instrumented");
        Check.eq(ObjectStreamClass.lookup(io.github.yagipass.verbatime.fixtures.SerializableFixture.class).getSerialVersionUID(), ObjectStreamClass.lookup(serial).getSerialVersionUID(),
                "instrumentation must not change the default serialVersionUID, or sessions and caches written without the agent fail to deserialize");
        final Object so = serial.getConstructor().newInstance();
        Check.eq(true, serial.getMethod("holdsOwnMonitor").invoke(so), "instance body runs while the wrapper holds this");
        Check.eq(true, serial.getMethod("holdsClassMonitor").invoke(null), "static body runs while the wrapper holds the Class monitor");

        final Class<?> iface = loader.loadClass("io.github.yagipass.verbatime.fixtures.FixtureInterface");
        Check.that(iface.getClassLoader() == loader, "FixtureInterface defined by the child-first loader");
        final List<String> ifaceMethods = new ArrayList<>();
        for (final Method m : iface.getDeclaredMethods()) {
            ifaceMethods.add(m.getName());
        }
        Check.that(ifaceMethods.contains("greet$trace"), "default method got a body: " + ifaceMethods);
        Check.that(ifaceMethods.contains("istatic$trace"), "interface static method got a body");
        Check.that(!ifaceMethods.contains("abstractMethod$trace"), "abstract method not instrumented");

        try {
            fx.getMethod("rootThrows").invoke(o);
            Check.fail("rootThrows should throw");
        } catch (final InvocationTargetException e) {
            Check.that(e.getCause() instanceof IllegalStateException, "original exception propagates");
        }

        final DecodedTrace d = DecodedTrace.decode(out.path());
        Check.eq(2, d.sessions.size(), "two sessions so far");
        final DecodedTrace.DecodedSession s1 = d.sessions.get(1);
        Check.eq("io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;", MethodRegistry.displayName(s1.rootId), "session 1 root");
        Check.that(s1.ended, "session 1 carries SESSION_END");
        final List<DecodedTrace.Node> n1 = DecodedTrace.toPreorder(s1);
        Check.that(n1.stream().noneMatch(DecodedTrace.Node::unclosed), "no unclosed frames");
        final List<String> depth1 = new ArrayList<>();
        for (final DecodedTrace.Node n : n1) {
            if (n.depth() == 1) {
                depth1.add(simple(n));
            }
        }
        Check.eq(List.of("stat", "inst", "prims", "sync", "rec", "caught", "caughtOther", "lambda", "greet", "istatic", "inner", "arr", "annotatedParam", "vd"), depth1, "direct children of root in call order");
        Check.eq(4, named(n1, "rec").size(), "recursion depth 3 -> 4 rec frames");
        Check.eq(List.of(1, 2, 3, 4), named(n1, "rec").stream().map(DecodedTrace.Node::depth).toList(), "rec nesting");
        Check.that(single(n1, "thrower").thrown(), "throw flag on thrower");
        Check.that(!single(n1, "caught").thrown(), "no throw flag on caught, because it caught the exception");
        Check.eq("java.lang.IllegalStateException", d.exceptionName(single(n1, "thrower").exceptionId()), "the wrapper hands the thrown object to the probe, so the class is on record");
        Check.eq("io.github.yagipass.verbatime.fixtures.FixtureException", d.exceptionName(single(n1, "otherThrower").exceptionId()), "a second exception class gets its own id");
        Check.that(single(n1, "thrower").exceptionId() != single(n1, "otherThrower").exceptionId(), "different classes, different ids");
        Check.eq(single(n1, "otherThrower").exceptionId(), ExceptionRegistry.id(io.github.yagipass.verbatime.fixtures.FixtureException.class), "the same class name from another loader shares the id, because ids are keyed by name as the file is");
        Check.eq(0, d.danglingExceptionRefs, "every EXCEPTION record is written before the chunk that references it");
        Check.eq(8, countAtDepth(n1, 2, "bool", "by", "ch", "sh", "in", "lo", "fl", "db"), "all primitive-returning callees recorded under prims");

        final List<DecodedTrace.Node> lambdaBodies = new ArrayList<>();
        for (final DecodedTrace.Node n : n1) {
            if (simple(n).startsWith("lambda$")) {
                lambdaBodies.add(n);
            }
        }
        Check.eq(1, lambdaBodies.size(), "lambda body method recorded");
        Check.eq(3, single(n1, "lambdaBody").depth(), "lambda -> lambda$... -> lambdaBody");
        Check.eq("io.github.yagipass.verbatime.fixtures.FixtureInterface.greet(Ljava/lang/String;)Ljava/lang/String;", MethodRegistry.displayName(single(n1, "greet").methodId()), "default method recorded under the interface");
        Check.eq(2, single(n1, "helper").depth(), "helper under greet");
        Check.eq("io.github.yagipass.verbatime.fixtures.Fixture$Inner.inner(I)I", MethodRegistry.displayName(single(n1, "inner").methodId()), "nested class method");
        final DecodedTrace.DecodedSession s2 = d.sessions.get(2);
        Check.eq("io.github.yagipass.verbatime.fixtures.Fixture.rootThrows()V", MethodRegistry.displayName(s2.rootId), "session 2 root");
        final List<DecodedTrace.Node> n2 = DecodedTrace.toPreorder(s2);
        Check.that(n2.get(0).thrown(), "throw flag on the root itself");
        Check.that(single(n2, "thrower").thrown(), "throw flag on thrower in session 2");
        Check.eq(single(n2, "thrower").exceptionId(), n2.get(0).exceptionId(), "an exception unwinding through the root is recorded on every frame it passes, so the viewer can follow it up the tree");
        for (final Map.Entry<Integer, String> e : d.exceptionNames.entrySet()) {
            if (!e.getValue().equals(ExceptionRegistry.name(e.getKey()))) {
                Check.fail("EXCEPTION record disagrees with the registry: " + e);
            }
        }

        Check.that(d.methodNames.containsValue("io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;"), "CLASS records contain the root");
        for (final Map.Entry<Integer, String> e : d.methodNames.entrySet()) {
            if (!e.getValue().equals(MethodRegistry.displayName(e.getKey()))) {
                Check.fail("CLASS record disagrees with the registry: " + e);
            }
        }

        final Class<?> collide = loader.loadClass("io.github.yagipass.verbatime.fixtures.BodyNameCollision");
        final Object co = collide.getConstructor().newInstance();
        Check.eq(3, collide.getMethod("bar").invoke(co), "BodyNameCollision.bar() result");
        int fooTrace = 0;
        boolean barTrace = false;
        for (final Method m : collide.getDeclaredMethods()) {
            if (m.getName().equals("foo$trace")) {
                fooTrace++;
            }
            if (m.getName().equals("bar$trace")) {
                barTrace = true;
            }
        }
        Check.eq(1, fooTrace, "colliding foo not instrumented");
        Check.that(barTrace, "bar instrumented despite the collision");

        final int failedBefore = tr.failedClasses();
        final byte[] garbage = tr.transform(null, loader, "io/github/yagipass/verbatime/fixtures/Garbage", null, null, new byte[] { (byte) 0xCA, 1 });
        Check.that(garbage == null, "garbage class left unchanged");
        Check.eq(failedBefore + 1, tr.failedClasses(), "failure counted");

        final Path fix8 = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.fixtures8"));
        final byte[] oldBytes = Files.readAllBytes(fix8.resolve("io/github/yagipass/verbatime/fixtures8/LegacyClassFile.class"));
        int sessionsSoFar = 2;
        for (final int major : new int[] { 51, 50 }) {
            final byte[] versioned = withVersion(oldBytes, major);
            final Config cfg8 = Config.parse("include=io.github.yagipass.verbatime.fixtures8");
            final Roots roots8 = new Roots();
            roots8.replaceRoots(List.of(RootSpec.parse("io.github.yagipass.verbatime.fixtures8.LegacyClassFile::root")));
            final Transformer tr8 = new Transformer(cfg8, roots8, null);
            final TransformingLoader l8 = new TransformingLoader(tr8, "io.github.yagipass.verbatime.fixtures8.", n -> n.equals("io.github.yagipass.verbatime.fixtures8.LegacyClassFile") ? versioned : null);
            final Class<?> old = l8.loadClass("io.github.yagipass.verbatime.fixtures8.LegacyClassFile");
            final Object oo = old.getConstructor().newInstance();
            Check.eq("a25.01", old.getMethod("root").invoke(oo), "v" + major + " root() result");
            Check.eq(12, old.getMethod("sync", int.class).invoke(oo, 4), "v" + major + " sync() result");
            sessionsSoFar++;

            final byte[] t8 = tr8.transform(null, l8, "io/github/yagipass/verbatime/fixtures8/LegacyClassFile", null, null, versioned);
            final ClassModel cm8 = ClassFile.of().parse(t8);
            Check.eq(major, cm8.majorVersion(), "class-file version preserved");
            boolean wrapperHasStackMap = false;
            for (final MethodModel mm : cm8.methods()) {
                if (mm.methodName().equalsString("root") && mm.code().isPresent()) {
                    wrapperHasStackMap = mm.code().get().findAttribute(Attributes.stackMapTable()).isPresent();
                }
            }
            Check.that(wrapperHasStackMap, "v" + major + " wrapper got a StackMapTable");
        }
        Tracing.stop();
        out.close();
        final DecodedTrace d2 = DecodedTrace.decode(out.path());
        Check.eq(sessionsSoFar, d2.sessions.size(), "old-version roots wrote sessions too");
        Check.eq("io.github.yagipass.verbatime.fixtures8.LegacyClassFile.b(I)I", MethodRegistry.displayName(single(DecodedTrace.toPreorder(d2.sessions.get(3)), "b").methodId()), "old fixture callee recorded");
        Check.that(d2.cleanEnd, "the recording is complete");

        gateInjection();
    }

    private static final String MAIN_ARGS = "([Ljava/lang/String;)V";

    private static final String MAIN_NOARG = "()V";

    private static void gateInjection() {
        final byte[] demoBytes = TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.DemoMain");
        final ClassLoader cl = TransformerTest.class.getClassLoader();

        final Transformer inScope = new Transformer(Config.parse("include=io.github.yagipass.verbatime.fixtures"), new Roots(), "io/github/yagipass/verbatime/fixtures/DemoMain");
        final byte[] full = inScope.transform(null, cl, "io/github/yagipass/verbatime/fixtures/DemoMain", null, null, demoBytes);
        Check.that(full != null, "gate class inside include scope is transformed");
        final ClassModel fullCm = ClassFile.of().parse(full);
        Check.that(hasMethod(fullCm, "main$trace"), "in-scope gate class keeps the wrapper scheme");
        Check.eq(List.of("await", "enter"), agentInvokes(fullCm, MAIN_ARGS, 2), "gate call precedes Probe.enter in the wrapper");

        final Transformer outOfScope = new Transformer(Config.parse("include=com.example"), new Roots(), "io/github/yagipass/verbatime/fixtures/DemoMain");
        final byte[] minimal = outOfScope.transform(null, cl, "io/github/yagipass/verbatime/fixtures/DemoMain", null, null, demoBytes);
        Check.that(minimal != null, "gate class outside include scope still gets the gate");
        final ClassModel minCm = ClassFile.of().parse(minimal);
        Check.that(!hasMethod(minCm, "main$trace"), "out-of-scope gate class is not instrumented");
        Check.eq(List.of("await"), agentInvokes(minCm, MAIN_ARGS, Integer.MAX_VALUE), "only the gate call is injected");
        final byte[] other = outOfScope.transform(null, cl, "io/github/yagipass/verbatime/fixtures/Fixture", null, null, TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.Fixture"));
        Check.that(other == null, "other out-of-scope classes stay untouched");

        gateOnLauncherMain("NoArgMain", MAIN_NOARG);
        gateOnLauncherMain("InstanceMain", MAIN_ARGS);
        gateOnLauncherMain("InstanceNoArgMain", MAIN_NOARG);

        final ClassModel bothFull = transformGateClass("BothMains", true);
        Check.eq(List.of("await", "enter"), agentInvokes(bothFull, MAIN_ARGS, 2), "in-scope BothMains gates the launcher-chosen main(String[])");
        Check.eq(List.of("enter"), agentInvokes(bothFull, MAIN_NOARG, 1), "in-scope BothMains leaves main() ungated so delegation cannot wait twice");
        Check.eq(1, gateCount(bothFull), "in-scope BothMains carries exactly one gate");
        final ClassModel bothMin = transformGateClass("BothMains", false);
        Check.eq(List.of("await"), agentInvokes(bothMin, MAIN_ARGS, Integer.MAX_VALUE), "out-of-scope BothMains gates main(String[])");
        Check.eq(1, gateCount(bothMin), "out-of-scope BothMains carries exactly one gate");

        final ClassModel privFull = transformGateClass("PrivateMain", true);
        Check.eq(0, gateCount(privFull), "in-scope PrivateMain is instrumented but never gated");
        final Transformer privScope = new Transformer(Config.parse("include=com.example"), new Roots(), "io/github/yagipass/verbatime/fixtures/PrivateMain");
        final byte[] privMin = privScope.transform(null, cl, "io/github/yagipass/verbatime/fixtures/PrivateMain", null, null, TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.PrivateMain"));
        Check.that(privMin == null, "out-of-scope PrivateMain has nothing to gate and loads unchanged");

        final ClassModel collide = transformGateClass("MainBodyNameCollision", true);
        Check.eq(List.of("await"), agentInvokes(collide, MAIN_ARGS, Integer.MAX_VALUE), "unwrappable main still receives the gate and nothing else");
        Check.eq(1, gateCount(collide), "MainBodyNameCollision carries exactly one gate");
    }

    private static void gateOnLauncherMain(final String simpleName, final String desc) {
        final ClassModel full = transformGateClass(simpleName, true);
        Check.that(hasMethod(full, "main$trace"), "in-scope " + simpleName + " keeps the wrapper scheme");
        Check.eq(List.of("await", "enter"), agentInvokes(full, desc, 2), "in-scope " + simpleName + " gates its main before Probe.enter");
        Check.eq(1, gateCount(full), "in-scope " + simpleName + " carries exactly one gate");
        final ClassModel minimal = transformGateClass(simpleName, false);
        Check.that(!hasMethod(minimal, "main$trace"), "out-of-scope " + simpleName + " is not instrumented");
        Check.eq(List.of("await"), agentInvokes(minimal, desc, Integer.MAX_VALUE), "out-of-scope " + simpleName + " gets only the gate");
        Check.eq(1, gateCount(minimal), "out-of-scope " + simpleName + " carries exactly one gate");
    }

    private static ClassModel transformGateClass(final String simpleName, final boolean inScope) {
        final String internal = "io/github/yagipass/verbatime/fixtures/" + simpleName;
        final Config config = Config.parse(inScope ? "include=io.github.yagipass.verbatime.fixtures" : "include=com.example");
        final Transformer t = new Transformer(config, new Roots(), internal);
        final byte[] out = t.transform(null, TransformerTest.class.getClassLoader(), internal, null, null, TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures." + simpleName));
        Check.that(out != null, (inScope ? "in-scope " : "out-of-scope ") + simpleName + " is transformed for the gate");
        return ClassFile.of().parse(out);
    }

    private static int gateCount(final ClassModel cm) {
        int count = 0;
        for (final MethodModel mm : cm.methods()) {
            if (mm.code().isEmpty()) {
                continue;
            }
            for (final CodeElement e : mm.code().get()) {
                if (e instanceof final InvokeInstruction ii && ii.owner().name().equalsString("io/github/yagipass/verbatime/agent/probe/StartupGate") && ii.name().equalsString("await")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasMethod(final ClassModel cm, final String name) {
        for (final MethodModel mm : cm.methods()) {
            if (mm.methodName().equalsString(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> agentInvokes(final ClassModel cm, final String desc, final int limit) {
        final List<String> found = new ArrayList<>();
        for (final MethodModel mm : cm.methods()) {
            if (!mm.methodName().equalsString("main") || !mm.methodType().equalsString(desc) || mm.code().isEmpty()) {
                continue;
            }
            for (final CodeElement e : mm.code().get()) {
                if (e instanceof final InvokeInstruction ii && (ii.owner().name().equalsString("io/github/yagipass/verbatime/agent/probe/Probe") || ii.owner().name().equalsString("io/github/yagipass/verbatime/agent/probe/StartupGate"))) {
                    found.add(ii.name().stringValue());
                    if (found.size() >= limit) {
                        return found;
                    }
                }
            }
        }
        return found;
    }

    private static String simple(final DecodedTrace.Node n) {
        return MethodRegistry.methodName(n.methodId());
    }

    private static List<DecodedTrace.Node> named(final List<DecodedTrace.Node> nodes, final String simpleName) {
        final List<DecodedTrace.Node> out = new ArrayList<>();
        for (final DecodedTrace.Node n : nodes) {
            if (simple(n).equals(simpleName)) {
                out.add(n);
            }
        }
        return out;
    }

    private static DecodedTrace.Node single(final List<DecodedTrace.Node> nodes, final String simpleName) {
        final List<DecodedTrace.Node> found = named(nodes, simpleName);
        if (found.size() != 1) {
            throw new AssertionError("expected exactly one '" + simpleName + "' frame, found " + found.size());
        }
        return found.get(0);
    }

    private static int countAtDepth(final List<DecodedTrace.Node> nodes, final int depth, final String... names) {
        int found = 0;
        for (final String name : names) {
            for (final DecodedTrace.Node n : named(nodes, name)) {
                if (n.depth() == depth) {
                    found++;
                }
            }
        }
        return found;
    }

    private static byte[] withVersion(final byte[] bytes, final int major) {
        final ClassFile cf = ClassFile.of();
        return cf.transformClass(cf.parse(bytes), (cb, ce) -> {
            if (ce instanceof ClassFileVersion) {
                cb.with(ClassFileVersion.of(major, 0));
            } else {
                cb.with(ce);
            }
        });
    }
}
