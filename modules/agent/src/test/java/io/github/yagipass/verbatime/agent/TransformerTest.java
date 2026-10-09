package io.github.yagipass.verbatime.agent;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.agent.probe.ExceptionRegistry;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.TraceFileWriter;
import io.github.yagipass.verbatime.agent.probe.Tracing;
import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.DecodedTrace;
import java.io.ObjectStreamClass;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileVersion;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TransformerTest {

  private static final String MAIN_ARGS = "([Ljava/lang/String;)V";

  private static final String MAIN_NOARG = "()V";

  private TransformerTest() {}

  public static void run() throws Exception {
    Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
    Files.createDirectories(tmp);
    TraceFileWriter out = TraceFileWriter.open(tmp.resolve("unit.vbtm"));
    Tracing.start(out);

    Config cfg = Config.parse("include=io.github.yagipass.verbatime.fixtures");
    List<RootSpec> fxRoots =
        List.of(
            RootSpec.parse("io.github.yagipass.verbatime.fixtures.Fixture::root"),
            RootSpec.parse("io.github.yagipass.verbatime.fixtures.Fixture::rootThrows"));
    Roots roots = new Roots();
    roots.replaceRoots(fxRoots);
    Transformer tr = new Transformer(cfg, roots, null);
    TransformingLoader loader =
        new TransformingLoader(
            tr, "io.github.yagipass.verbatime.fixtures.", TransformingLoader::classpathBytes);

    Class<?> fx = loader.loadClass("io.github.yagipass.verbatime.fixtures.Fixture");
    Check.that(fx.getClassLoader() == loader, "Fixture defined by the child-first loader");
    Object o = fx.getConstructor().newInstance();
    String expected = new io.github.yagipass.verbatime.fixtures.Fixture().root();
    Check.eq(expected, fx.getMethod("root").invoke(o), "transformed root() result");
    Check.that(roots.isResolved(fxRoots.get(0)), "root resolved at transform time");
    Check.that(
        tr.instrumentedClasses() >= 3, "Fixture, FixtureInterface and Fixture$Inner instrumented");

    Method rootM = fx.getMethod("root");
    Check.eq(
        1,
        rootM.getAnnotations().length,
        "method annotation kept, because frameworks read it by reflection");
    Check.eq(
        "io.github.yagipass.verbatime.fixtures.Marker",
        rootM.getAnnotations()[0].annotationType().getName(),
        "annotation type");
    Method ap = fx.getDeclaredMethod("annotatedParam", String.class);
    Check.eq(
        1,
        ap.getParameterAnnotations()[0].length,
        "parameter annotation kept, because frameworks read it by reflection");
    Check.that(
        Modifier.isSynchronized(fx.getDeclaredMethod("sync", int.class).getModifiers()),
        "synchronized kept, so the method still locks and reflection and the default serialVersionUID match the original class");
    Check.eq(1, fx.getDeclaredConstructors().length, "constructor untouched");
    for (int i = 0; i < MethodRegistry.size(); i++) {
      if (MethodRegistry.methodName(i).startsWith("<")) {
        Check.fail("constructor registered: " + MethodRegistry.displayName(i));
      }
    }

    Class<?> serial = loader.loadClass("io.github.yagipass.verbatime.fixtures.SerializableFixture");
    Check.that(
        serial.getClassLoader() == loader, "SerializableFixture defined by the child-first loader");
    Check.eq(
        ObjectStreamClass.lookup(io.github.yagipass.verbatime.fixtures.SerializableFixture.class)
            .getSerialVersionUID(),
        ObjectStreamClass.lookup(serial).getSerialVersionUID(),
        "instrumentation must not change the default serialVersionUID, or sessions and caches written without the agent fail to deserialize");
    Object so = serial.getConstructor().newInstance();
    Check.eq(
        true,
        serial.getMethod("holdsOwnMonitor").invoke(so),
        "an instrumented synchronized method still runs while holding this");
    Check.eq(
        true,
        serial.getMethod("holdsClassMonitor").invoke(null),
        "an instrumented static synchronized method still runs while holding the Class monitor");

    Class<?> iface = loader.loadClass("io.github.yagipass.verbatime.fixtures.FixtureInterface");
    Check.that(
        iface.getClassLoader() == loader, "FixtureInterface defined by the child-first loader");

    for (Class<?> c :
        new Class<?>[] {
          fx, serial, iface, loader.loadClass("io.github.yagipass.verbatime.fixtures.Fixture$Inner")
        }) {
      Check.eq(
          declaredMethods(Class.forName(c.getName())),
          declaredMethods(c),
          c.getSimpleName()
              + " declares the same methods, with the same modifiers, as without the agent, because frameworks scan declared methods and Gradle's worker rejected the extra name$trace methods of the old wrapper scheme");
    }

    Transformer inspector = new Transformer(cfg, new Roots(), null);
    byte[] fxBytes = inspect(inspector, "Fixture");
    List<String> probeLifecycle = List.of("enter", "exit", "exitThrow");
    Check.eq(
        probeLifecycle,
        probeCalls(fxBytes, "root"),
        "root() enters, exits on its one return, and reports a throw from inside itself, so its time and exceptions are on record");
    Check.eq(
        List.of("enter", "exit", "exit", "exitThrow"),
        probeCalls(fxBytes, "caught"),
        "every return gets its own exit, or a call that leaves through the second return stays open in the tree");
    Check.eq(
        probeLifecycle,
        probeCalls(inspect(inspector, "SerializableFixture"), "increment"),
        "synchronized SerializableFixture.increment is instrumented");
    byte[] ifaceBytes = inspect(inspector, "FixtureInterface");
    Check.eq(
        probeLifecycle,
        probeCalls(ifaceBytes, "greet"),
        "interface default method is instrumented");
    Check.eq(
        probeLifecycle,
        probeCalls(ifaceBytes, "istatic"),
        "interface static method is instrumented");
    Check.eq(
        List.of(),
        probeCalls(ifaceBytes, "abstractMethod"),
        "abstract method has no code to instrument");
    int failedBeforeSecondPass = inspector.failedClasses();
    Check.that(
        inspector.transform(
                null,
                TransformerTest.class.getClassLoader(),
                "io/github/yagipass/verbatime/fixtures/Fixture",
                null,
                null,
                fxBytes)
            == null,
        "a class that already calls the probe is left alone, so a second copy of the agent cannot record every call twice");
    Check.eq(
        failedBeforeSecondPass,
        inspector.failedClasses(),
        "leaving an instrumented class alone is not a failure");

    try {
      fx.getMethod("rootThrows").invoke(o);
      Check.fail("rootThrows should throw");
    } catch (InvocationTargetException e) {
      Check.that(e.getCause() instanceof IllegalStateException, "original exception propagates");
      try {
        new io.github.yagipass.verbatime.fixtures.Fixture().rootThrows();
        Check.fail("uninstrumented rootThrows should throw");
      } catch (IllegalStateException plain) {
        Check.eq(
            fixtureFrames(plain),
            fixtureFrames(e.getCause()),
            "stack traces through instrumented methods show the same methods and lines as without the agent, because users paste them and error trackers group by them");
      }
    }

    DecodedTrace d = DecodedTrace.decode(out.path());
    Check.eq(2, d.sessions.size(), "two sessions so far");
    DecodedTrace.DecodedSession s1 = d.sessions.get(1);
    Check.eq(
        "io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;",
        MethodRegistry.displayName(s1.rootId),
        "session 1 root");
    Check.that(s1.ended, "session 1 carries SESSION_END");
    List<DecodedTrace.Node> n1 = DecodedTrace.toPreorder(s1);
    Check.that(n1.stream().noneMatch(DecodedTrace.Node::unclosed), "no unclosed frames");
    List<String> depth1 = new ArrayList<>();
    for (DecodedTrace.Node n : n1) {
      if (n.depth() == 1) {
        depth1.add(simple(n));
      }
    }
    Check.eq(
        List.of(
            "stat",
            "inst",
            "prims",
            "sync",
            "rec",
            "caught",
            "caughtOther",
            "lambda",
            "greet",
            "istatic",
            "inner",
            "arr",
            "annotatedParam",
            "vd"),
        depth1,
        "direct children of root in call order");
    Check.eq(4, named(n1, "rec").size(), "recursion depth 3 -> 4 rec frames");
    Check.eq(
        List.of(1, 2, 3, 4),
        named(n1, "rec").stream().map(DecodedTrace.Node::depth).toList(),
        "rec nesting");
    Check.that(single(n1, "thrower").thrown(), "throw flag on thrower");
    Check.that(
        !single(n1, "caught").thrown(), "no throw flag on caught, because it caught the exception");
    Check.eq(
        "java.lang.IllegalStateException",
        d.exceptionName(single(n1, "thrower").exceptionId()),
        "the wrapper hands the thrown object to the probe, so the class is on record");
    Check.eq(
        "io.github.yagipass.verbatime.fixtures.FixtureException",
        d.exceptionName(single(n1, "otherThrower").exceptionId()),
        "a second exception class gets its own id");
    Check.that(
        single(n1, "thrower").exceptionId() != single(n1, "otherThrower").exceptionId(),
        "different classes, different ids");
    Check.eq(
        single(n1, "otherThrower").exceptionId(),
        ExceptionRegistry.id(io.github.yagipass.verbatime.fixtures.FixtureException.class),
        "the same class name from another loader shares the id, because ids are keyed by name as the file is");
    Check.eq(
        0,
        d.danglingExceptionRefs,
        "every EXCEPTION record is written before the chunk that references it");
    Check.eq(
        8,
        countAtDepth(n1, 2, "bool", "by", "ch", "sh", "in", "lo", "fl", "db"),
        "all primitive-returning callees recorded under prims");

    List<DecodedTrace.Node> lambdaBodies = new ArrayList<>();
    for (DecodedTrace.Node n : n1) {
      if (simple(n).startsWith("lambda$")) {
        lambdaBodies.add(n);
      }
    }
    Check.eq(1, lambdaBodies.size(), "lambda body method recorded");
    Check.eq(3, single(n1, "lambdaBody").depth(), "lambda -> lambda$... -> lambdaBody");
    Check.eq(
        "io.github.yagipass.verbatime.fixtures.FixtureInterface.greet(Ljava/lang/String;)Ljava/lang/String;",
        MethodRegistry.displayName(single(n1, "greet").methodId()),
        "default method recorded under the interface");
    Check.eq(2, single(n1, "helper").depth(), "helper under greet");
    Check.eq(
        "io.github.yagipass.verbatime.fixtures.Fixture$Inner.inner(I)I",
        MethodRegistry.displayName(single(n1, "inner").methodId()),
        "nested class method");
    DecodedTrace.DecodedSession s2 = d.sessions.get(2);
    Check.eq(
        "io.github.yagipass.verbatime.fixtures.Fixture.rootThrows()V",
        MethodRegistry.displayName(s2.rootId),
        "session 2 root");
    List<DecodedTrace.Node> n2 = DecodedTrace.toPreorder(s2);
    Check.that(n2.get(0).thrown(), "throw flag on the root itself");
    Check.that(single(n2, "thrower").thrown(), "throw flag on thrower in session 2");
    Check.eq(
        single(n2, "thrower").exceptionId(),
        n2.get(0).exceptionId(),
        "an exception unwinding through the root is recorded on every frame it passes, so the viewer can follow it up the tree");
    for (Map.Entry<Integer, String> e : d.exceptionNames.entrySet()) {
      if (!e.getValue().equals(ExceptionRegistry.name(e.getKey()))) {
        Check.fail("EXCEPTION record disagrees with the registry: " + e);
      }
    }

    Check.that(
        d.methodNames.containsValue(
            "io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;"),
        "CLASS records contain the root");
    for (Map.Entry<Integer, String> e : d.methodNames.entrySet()) {
      if (!e.getValue().equals(MethodRegistry.displayName(e.getKey()))) {
        Check.fail("CLASS record disagrees with the registry: " + e);
      }
    }

    jitLimit(cfg);
    spareLocals(cfg);

    int failedBefore = tr.failedClasses();
    byte[] garbage =
        tr.transform(
            null,
            loader,
            "io/github/yagipass/verbatime/fixtures/Garbage",
            null,
            null,
            new byte[] {(byte) 0xCA, 1});
    Check.that(garbage == null, "garbage class left unchanged");
    Check.eq(failedBefore + 1, tr.failedClasses(), "failure counted");

    Path fix8 = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.fixtures8"));
    byte[] oldBytes =
        Files.readAllBytes(
            fix8.resolve("io/github/yagipass/verbatime/fixtures8/LegacyClassFile.class"));
    @Var int sessionsSoFar = 2;
    for (int major : new int[] {51, 50, 49}) {
      byte[] versioned = withVersion(oldBytes, major);
      Config cfg8 = Config.parse("include=io.github.yagipass.verbatime.fixtures8");
      Roots roots8 = new Roots();
      roots8.replaceRoots(
          List.of(RootSpec.parse("io.github.yagipass.verbatime.fixtures8.LegacyClassFile::root")));
      Transformer tr8 = new Transformer(cfg8, roots8, null);
      TransformingLoader l8 =
          new TransformingLoader(
              tr8,
              "io.github.yagipass.verbatime.fixtures8.",
              n ->
                  n.equals("io.github.yagipass.verbatime.fixtures8.LegacyClassFile")
                      ? versioned
                      : null);
      Class<?> old = l8.loadClass("io.github.yagipass.verbatime.fixtures8.LegacyClassFile");
      Object oo = old.getConstructor().newInstance();
      Check.eq("a25.01", old.getMethod("root").invoke(oo), "v" + major + " root() result");
      Check.eq(12, old.getMethod("sync", int.class).invoke(oo, 4), "v" + major + " sync() result");
      sessionsSoFar++;

      byte[] t8 =
          tr8.transform(
              null,
              l8,
              "io/github/yagipass/verbatime/fixtures8/LegacyClassFile",
              null,
              null,
              versioned);
      ClassModel cm8 = ClassFile.of().parse(t8);
      Check.eq(major, cm8.majorVersion(), "class-file version preserved");
      if (major >= ClassFile.JAVA_6_VERSION) {
        Check.eq(
            1,
            stackMapFrames(cm8, "root"),
            "v"
                + major
                + " root() has no branches of its own, so its StackMapTable holds just the frame of the probe's catch-all");
        Check.eq(
            2,
            stackMapFrames(cm8, "caught"),
            "v"
                + major
                + " caught() keeps the frame of its own catch and adds the probe's, so the verifier accepts it without the agent computing frames");
      } else {
        Check.eq(
            -1,
            stackMapFrames(cm8, "root"),
            "v"
                + major
                + " classes get no StackMapTable, because the JVM verifies them by type inference");
        Check.eq(
            -1,
            stackMapFrames(cm8, "caught"),
            "v"
                + major
                + " caught() carries no StackMapTable either, even though the source class had one");
      }
    }
    Tracing.stop();
    out.close();
    DecodedTrace d2 = DecodedTrace.decode(out.path());
    Check.eq(sessionsSoFar, d2.sessions.size(), "old-version roots wrote sessions too");
    Check.eq(
        "io.github.yagipass.verbatime.fixtures8.LegacyClassFile.b(I)I",
        MethodRegistry.displayName(
            single(DecodedTrace.toPreorder(d2.sessions.get(3)), "b").methodId()),
        "old fixture callee recorded");
    Check.that(d2.cleanEnd, "the recording is complete");

    gateInjection();
  }

  private static void gateInjection() {
    byte[] demoBytes =
        TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.DemoMain");
    ClassLoader cl = TransformerTest.class.getClassLoader();

    Transformer inScope =
        new Transformer(
            Config.parse("include=io.github.yagipass.verbatime.fixtures"),
            new Roots(),
            "io/github/yagipass/verbatime/fixtures/DemoMain");
    byte[] full =
        inScope.transform(
            null, cl, "io/github/yagipass/verbatime/fixtures/DemoMain", null, null, demoBytes);
    Check.that(full != null, "gate class inside include scope is transformed");
    ClassModel fullCm = ClassFile.of().parse(full);
    Check.eq(
        List.of("await", "enter", "exit", "exitThrow"),
        agentInvokes(fullCm, MAIN_ARGS, Integer.MAX_VALUE),
        "in-scope main is instrumented in place with the gate before Probe.enter, or a recording started during the wait would miss main's own session");

    Transformer outOfScope =
        new Transformer(
            Config.parse("include=com.example"),
            new Roots(),
            "io/github/yagipass/verbatime/fixtures/DemoMain");
    byte[] minimal =
        outOfScope.transform(
            null, cl, "io/github/yagipass/verbatime/fixtures/DemoMain", null, null, demoBytes);
    Check.that(minimal != null, "gate class outside include scope still gets the gate");
    ClassModel minCm = ClassFile.of().parse(minimal);
    Check.eq(
        List.of("await"),
        agentInvokes(minCm, MAIN_ARGS, Integer.MAX_VALUE),
        "out-of-scope gate class gets only the gate call and no probe calls");
    byte[] other =
        outOfScope.transform(
            null,
            cl,
            "io/github/yagipass/verbatime/fixtures/Fixture",
            null,
            null,
            TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.Fixture"));
    Check.that(other == null, "other out-of-scope classes stay untouched");

    gateOnLauncherMain("NoArgMain", MAIN_NOARG);
    gateOnLauncherMain("InstanceMain", MAIN_ARGS);
    gateOnLauncherMain("InstanceNoArgMain", MAIN_NOARG);

    ClassModel bothFull = transformGateClass("BothMains", true);
    Check.eq(
        List.of("await", "enter"),
        agentInvokes(bothFull, MAIN_ARGS, 2),
        "in-scope BothMains gates the launcher-chosen main(String[])");
    Check.eq(
        List.of("enter"),
        agentInvokes(bothFull, MAIN_NOARG, 1),
        "in-scope BothMains leaves main() ungated so delegation cannot wait twice");
    Check.eq(1, gateCount(bothFull), "in-scope BothMains carries exactly one gate");
    ClassModel bothMin = transformGateClass("BothMains", false);
    Check.eq(
        List.of("await"),
        agentInvokes(bothMin, MAIN_ARGS, Integer.MAX_VALUE),
        "out-of-scope BothMains gates main(String[])");
    Check.eq(1, gateCount(bothMin), "out-of-scope BothMains carries exactly one gate");

    ClassModel privFull = transformGateClass("PrivateMain", true);
    Check.eq(0, gateCount(privFull), "in-scope PrivateMain is instrumented but never gated");
    Transformer privScope =
        new Transformer(
            Config.parse("include=com.example"),
            new Roots(),
            "io/github/yagipass/verbatime/fixtures/PrivateMain");
    byte[] privMin =
        privScope.transform(
            null,
            cl,
            "io/github/yagipass/verbatime/fixtures/PrivateMain",
            null,
            null,
            TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.PrivateMain"));
    Check.that(privMin == null, "out-of-scope PrivateMain has nothing to gate and loads unchanged");
  }

  private static void gateOnLauncherMain(String simpleName, String desc) {
    ClassModel full = transformGateClass(simpleName, true);
    Check.eq(
        List.of("await", "enter"),
        agentInvokes(full, desc, 2),
        "in-scope " + simpleName + " gates its main before Probe.enter");
    Check.eq(1, gateCount(full), "in-scope " + simpleName + " carries exactly one gate");
    ClassModel minimal = transformGateClass(simpleName, false);
    Check.eq(
        List.of("await"),
        agentInvokes(minimal, desc, Integer.MAX_VALUE),
        "out-of-scope " + simpleName + " gets only the gate and no probe calls");
    Check.eq(1, gateCount(minimal), "out-of-scope " + simpleName + " carries exactly one gate");
  }

  private static ClassModel transformGateClass(String simpleName, boolean inScope) {
    String internal = "io/github/yagipass/verbatime/fixtures/" + simpleName;
    Config config =
        Config.parse(
            inScope ? "include=io.github.yagipass.verbatime.fixtures" : "include=com.example");
    Transformer t = new Transformer(config, new Roots(), internal);
    byte[] out =
        t.transform(
            null,
            TransformerTest.class.getClassLoader(),
            internal,
            null,
            null,
            TransformingLoader.classpathBytes(
                "io.github.yagipass.verbatime.fixtures." + simpleName));
    Check.that(
        out != null,
        (inScope ? "in-scope " : "out-of-scope ") + simpleName + " is transformed for the gate");
    return ClassFile.of().parse(out);
  }

  private static int gateCount(ClassModel cm) {
    @Var int count = 0;
    for (MethodModel mm : cm.methods()) {
      if (mm.code().isEmpty()) {
        continue;
      }
      for (CodeElement e : mm.code().get()) {
        if (e instanceof InvokeInstruction ii
            && ii.owner()
                .name()
                .equalsString("io/github/yagipass/verbatime/agent/probe/StartupGate")
            && ii.name().equalsString("await")) {
          count++;
        }
      }
    }
    return count;
  }

  private static void jitLimit(Config cfg) {
    String internal = "io/github/yagipass/verbatime/fixtures/HugeMethods";
    int limit = TracingPlan.JIT_HUGE_METHOD_LIMIT;
    byte[] bytes =
        ClassFile.of()
            .build(
                ClassDesc.ofInternalName(internal),
                clb -> {
                  clb.withFlags(ClassFile.ACC_PUBLIC);
                  nopMethod(clb, "small", 1);
                  nopMethod(clb, "roomForProbes", limit - 100);
                  nopMethod(clb, "nearLimit", limit - 10);
                  nopMethod(clb, "alreadyHuge", limit + 1000);
                });
    byte[] out =
        new Transformer(cfg, new Roots(), null)
            .transform(null, TransformerTest.class.getClassLoader(), internal, null, null, bytes);
    List<String> probeLifecycle = List.of("enter", "exit", "exitThrow");
    Check.eq(probeLifecycle, probeCalls(out, "small"), "a small method is instrumented");
    Check.eq(
        probeLifecycle,
        probeCalls(out, "roomForProbes"),
        "a method with room for the probe calls under the JIT's size limit is instrumented");
    Check.that(
        codeLength(out, "roomForProbes") <= limit,
        "the probe calls keep that method within the limit, so the JIT still compiles it: "
            + codeLength(out, "roomForProbes")
            + " bytes");
    Check.eq(
        List.of(),
        probeCalls(out, "nearLimit"),
        "a method the probe calls would push past the JIT's size limit is left alone, because the JIT never compiles it after that and it would run far slower than the probes cost");
    Check.eq(
        probeLifecycle,
        probeCalls(out, "alreadyHuge"),
        "a method already past the limit is never compiled anyway, so it is instrumented");
  }

  private static void spareLocals(Config cfg) throws Exception {
    String name = "io.github.yagipass.verbatime.fixtures.SpareLocals";
    byte[] built =
        ClassFile.of()
            .build(
                ClassDesc.of(name),
                clb -> {
                  clb.withFlags(ClassFile.ACC_PUBLIC);
                  clb.withMethodBody(
                      "spare",
                      MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int),
                      ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                      cob -> {
                        Label start = cob.newBoundLabel();
                        cob.iload(0);
                        cob.ireturn();
                        Label end = cob.newBoundLabel();
                        cob.localVariable(0, "x", ConstantDescs.CD_int, start, end);
                        cob.localVariable(2, "unused", ConstantDescs.CD_int, start, end);
                      });
                });
    byte[] original = withMaxLocals(built, "spare", 3);
    Class<?> plain =
        new TransformingLoader(
                new Transformer(Config.parse("include=com.example"), new Roots(), null),
                name,
                n -> original)
            .loadClass(name);
    Check.eq(
        7,
        plain.getMethod("spare", int.class).invoke(null, 7),
        "SpareLocals loads unchanged, like the Clojure classes that declare local slots their code never touches");

    byte[] instrumented = inspectBytes(new Transformer(cfg, new Roots(), null), name, original);
    Check.eq(
        3,
        ((CodeAttribute) method(instrumented, "spare").code().orElseThrow()).maxLocals(),
        "an instrumented method keeps the max_locals it declared, or HotSpot rejects a LocalVariableTable entry past it with ClassFormatError and the class fails to load");
    Class<?> traced =
        new TransformingLoader(new Transformer(cfg, new Roots(), null), name, n -> original)
            .loadClass(name);
    Check.eq(
        7,
        traced.getMethod("spare", int.class).invoke(null, 7),
        "instrumented SpareLocals loads and runs");

    byte[] fxOriginal =
        TransformingLoader.classpathBytes("io.github.yagipass.verbatime.fixtures.Fixture");
    byte[] fxInstrumented =
        inspectBytes(
            new Transformer(cfg, new Roots(), null),
            "io.github.yagipass.verbatime.fixtures.Fixture",
            fxOriginal);
    Check.eq(
        stores(fxOriginal, "root"),
        stores(fxInstrumented, "root"),
        "javac code touches every slot it declares, so it gets no extra store and grows only by the probe calls");
  }

  private static int stores(byte[] classBytes, String name) {
    @Var int n = 0;
    for (CodeElement e : method(classBytes, name).code().orElseThrow()) {
      if (e instanceof StoreInstruction) {
        n++;
      }
    }
    return n;
  }

  private static byte[] inspectBytes(Transformer t, String binaryName, byte[] bytes) {
    byte[] out =
        t.transform(
            null,
            TransformerTest.class.getClassLoader(),
            binaryName.replace('.', '/'),
            null,
            null,
            bytes);
    if (out == null) {
      throw new AssertionError(binaryName + " was not transformed");
    }
    return out;
  }

  private static byte[] withMaxLocals(byte[] bytes, String name, int maxLocals) {
    CodeAttribute code = (CodeAttribute) method(bytes, name).code().orElseThrow();
    byte[] codeBytes = code.codeArray();
    byte[] needle =
        ByteBuffer.allocate(8 + codeBytes.length)
            .putShort((short) code.maxStack())
            .putShort((short) code.maxLocals())
            .putInt(codeBytes.length)
            .put(codeBytes)
            .array();
    @Var int at = -1;
    for (int i = 0; i + needle.length <= bytes.length; i++) {
      if (Arrays.equals(bytes, i, i + needle.length, needle, 0, needle.length)) {
        if (at >= 0) {
          throw new AssertionError(
              "Code attribute of " + name + " is not unique in the class bytes");
        }
        at = i;
      }
    }
    if (at < 0) {
      throw new AssertionError("Code attribute of " + name + " not found");
    }
    byte[] patched = bytes.clone();
    patched[at + 2] = (byte) (maxLocals >>> 8);
    patched[at + 3] = (byte) maxLocals;
    return patched;
  }

  private static void nopMethod(ClassBuilder clb, String name, int codeLength) {
    clb.withMethodBody(
        name,
        MethodTypeDesc.of(ConstantDescs.CD_void),
        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
        cob -> {
          for (int i = 1; i < codeLength; i++) {
            cob.nop();
          }
          cob.return_();
        });
  }

  private static byte[] inspect(Transformer t, String simpleName) {
    String name = "io.github.yagipass.verbatime.fixtures." + simpleName;
    return inspectBytes(t, name, TransformingLoader.classpathBytes(name));
  }

  private static MethodModel method(byte[] classBytes, String name) {
    for (MethodModel mm : ClassFile.of().parse(classBytes).methods()) {
      if (mm.methodName().equalsString(name)) {
        return mm;
      }
    }
    throw new AssertionError("no method " + name);
  }

  private static List<String> probeCalls(byte[] classBytes, String name) {
    List<String> found = new ArrayList<>();
    MethodModel mm = method(classBytes, name);
    if (mm.code().isPresent()) {
      for (CodeElement e : mm.code().get()) {
        if (e instanceof InvokeInstruction ii
            && ii.owner().name().equalsString("io/github/yagipass/verbatime/agent/probe/Probe")) {
          found.add(ii.name().stringValue());
        }
      }
    }
    return found;
  }

  private static int codeLength(byte[] classBytes, String name) {
    return ((CodeAttribute) method(classBytes, name).code().orElseThrow()).codeLength();
  }

  private static int stackMapFrames(ClassModel cm, String name) {
    for (MethodModel mm : cm.methods()) {
      if (mm.methodName().equalsString(name) && mm.code().isPresent()) {
        return mm.code()
            .get()
            .findAttribute(Attributes.stackMapTable())
            .map(smt -> smt.entries().size())
            .orElse(-1);
      }
    }
    throw new AssertionError("no method " + name);
  }

  private static List<String> declaredMethods(Class<?> c) {
    List<String> out = new ArrayList<>();
    for (Method m : c.getDeclaredMethods()) {
      out.add(
          Modifier.toString(m.getModifiers())
              + (m.isSynthetic() ? " synthetic " : " ")
              + m.getReturnType().getName()
              + " "
              + m.getName()
              + Arrays.toString(m.getParameterTypes()));
    }
    Collections.sort(out);
    return out;
  }

  private static List<String> fixtureFrames(Throwable t) {
    List<String> out = new ArrayList<>();
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().startsWith("io.github.yagipass.verbatime.fixtures.")) {
        out.add(e.getClassName() + "." + e.getMethodName() + ":" + e.getLineNumber());
      }
    }
    return out;
  }

  private static List<String> agentInvokes(ClassModel cm, String desc, int limit) {
    List<String> found = new ArrayList<>();
    for (MethodModel mm : cm.methods()) {
      if (!mm.methodName().equalsString("main")
          || !mm.methodType().equalsString(desc)
          || mm.code().isEmpty()) {
        continue;
      }
      for (CodeElement e : mm.code().get()) {
        if (e instanceof InvokeInstruction ii
            && (ii.owner().name().equalsString("io/github/yagipass/verbatime/agent/probe/Probe")
                || ii.owner()
                    .name()
                    .equalsString("io/github/yagipass/verbatime/agent/probe/StartupGate"))) {
          found.add(ii.name().stringValue());
          if (found.size() >= limit) {
            return found;
          }
        }
      }
    }
    return found;
  }

  private static String simple(DecodedTrace.Node n) {
    return MethodRegistry.methodName(n.methodId());
  }

  private static List<DecodedTrace.Node> named(List<DecodedTrace.Node> nodes, String simpleName) {
    List<DecodedTrace.Node> out = new ArrayList<>();
    for (DecodedTrace.Node n : nodes) {
      if (simple(n).equals(simpleName)) {
        out.add(n);
      }
    }
    return out;
  }

  private static DecodedTrace.Node single(List<DecodedTrace.Node> nodes, String simpleName) {
    List<DecodedTrace.Node> found = named(nodes, simpleName);
    if (found.size() != 1) {
      throw new AssertionError(
          "expected exactly one '" + simpleName + "' frame, found " + found.size());
    }
    return found.get(0);
  }

  private static int countAtDepth(List<DecodedTrace.Node> nodes, int depth, String... names) {
    @Var int found = 0;
    for (String name : names) {
      for (DecodedTrace.Node n : named(nodes, name)) {
        if (n.depth() == depth) {
          found++;
        }
      }
    }
    return found;
  }

  private static byte[] withVersion(byte[] bytes, int major) {
    ClassFile cf = ClassFile.of();
    return cf.transformClass(
        cf.parse(bytes),
        (cb, ce) -> {
          if (ce instanceof ClassFileVersion) {
            cb.with(ClassFileVersion.of(major, 0));
          } else {
            cb.with(ce);
          }
        });
  }
}
