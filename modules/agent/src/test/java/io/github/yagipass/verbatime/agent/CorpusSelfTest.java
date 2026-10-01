package io.github.yagipass.verbatime.agent;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import java.io.PrintStream;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.errorprone.annotations.Var;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.TestMain;

final class CorpusSelfTest {

    private static final List<String> SABOTAGED_JARS = List.of("commons-lang-", "mail-", "httpcore-", "log4j-api-", "spring-expression-");

    private static final String CLINIT_PROPERTY = "io.github.yagipass.verbatime.agent.test.corpus.clinitRan";

    private static final MethodTypeDesc INT_NO_ARGS = MethodTypeDesc.of(CD_int);

    private record Sabotaged(String internalName, int major, String method, String kind) {
    }

    private CorpusSelfTest() {
    }

    public static void main(String[] args) {
        TestMain.run("CorpusSelfTest", CorpusSelfTest::run);
        TestMain.report();
    }

    static void run() throws Exception {
        linkingVerifiesWithoutInitializing();
        refusalReasonsAreCaptured();
        corruptedClassesAreReported();
    }

    private static void refusalReasonsAreCaptured() {
        PrintStream realErr = System.err;
        CorpusVerifier.RefusalLog log = new CorpusVerifier.RefusalLog(realErr);
        Transformer tr = new Transformer(Config.parse(""), new Roots(), null);
        System.setErr(log);
        try {
            Check.eq(null, tr.transform(null, CorpusSelfTest.class.getClassLoader(), "corpusselftest/Garbage", null, null, new byte[] { (byte) 0xCA, (byte) 0xFE, 0, 0 }), "the Transformer refuses a class it cannot parse");
        } finally {
            System.setErr(realErr);
        }
        Check.eq(1, tr.failedClasses(), "the refusal is counted");
        Check.that(!log.reason("corpusselftest/Garbage").startsWith("(the Transformer logged no reason"), "the harness captures why the Transformer refused a class, so the corpus report says what to fix, got " + log.reason("corpusselftest/Garbage"));
    }

    private static void linkingVerifiesWithoutInitializing() {
        Map<String, byte[]> classes = Map.of("corpusselftest.Broken52", typeError("corpusselftest/Broken52", ClassFile.JAVA_8_VERSION), "corpusselftest.Broken49", typeError("corpusselftest/Broken49", ClassFile.JAVA_5_VERSION),
                "corpusselftest.Sound", sound("corpusselftest/Sound"));
        ClassLoader loader = new ClassLoader("corpus-selftest", ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name);
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, b, 0, b.length);
            }
        };
        for (String name : List.of("corpusselftest.Broken52", "corpusselftest.Broken49")) {
            Check.that(loads(loader, name), name + " loads without complaint, since HotSpot verifies at link time, so the harness has to link each class itself");
            Throwable t = CorpusVerifier.link(loader, name);
            Check.that(t instanceof VerifyError, name + ": the harness's link step throws VerifyError for a class with a type error, which proves it runs the verifier, got " + t);
        }
        Check.eq(null, CorpusVerifier.link(loader, "corpusselftest.Sound"), "a sound class links");
        Check.eq(null, System.getProperty(CLINIT_PROPERTY), "linking runs no static initializer, since corpus classes may start threads, open files or exit the JVM when initialized");
        try {
            Class.forName("corpusselftest.Sound", true, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        Check.eq("yes", System.getProperty(CLINIT_PROPERTY), "initializing the class does run its static initializer, so the check above would have seen one run by linking");
    }

    private static void corruptedClassesAreReported() throws Exception {
        try (CorpusJars corpus = CorpusJars.fromProperty()) {
            List<CorpusJars.Jar> jars = corpus.named(SABOTAGED_JARS);
            Map<String, Sabotaged> sabotaged = new ConcurrentHashMap<>();
            List<CorpusVerifier.JarReport> reports = CorpusVerifier.run(corpus, jars, CorpusVerifyTest.threads(), (internalName, bytes) -> sabotage(internalName, bytes, sabotaged));

            Set<String> subjects = new HashSet<>();
            Set<String> originalFailures = new HashSet<>();
            List<CorpusVerifier.Regression> regressions = new ArrayList<>();
            for (CorpusJars.Jar j : jars) {
                j.classEntries().forEach(e -> subjects.add(e.substring(0, e.length() - ".class".length())));
            }
            for (CorpusVerifier.JarReport r : reports) {
                r.originalFailures.forEach(n -> originalFailures.add(n.replace('.', '/')));
                regressions.addAll(r.regressions);
            }

            @Var int oldVerifier = 0;
            @Var int failover = 0;
            @Var int typeChecker = 0;
            List<String> missed = new ArrayList<>();
            for (Sabotaged s : sabotaged.values()) {
                if (!subjects.contains(s.internalName()) || originalFailures.contains(s.internalName())) {
                    continue;
                }
                if (s.major() < ClassFile.JAVA_6_VERSION) {
                    oldVerifier++;
                } else if (s.major() == ClassFile.JAVA_6_VERSION) {
                    failover++;
                } else {
                    typeChecker++;
                }
                CorpusVerifier.Regression reg = regressions.stream().filter(x -> x.className().equals(s.internalName().replace('/', '.'))).findFirst().orElse(null);
                if (reg == null) {
                    missed.add(s.internalName() + " (" + s.kind() + " in " + s.method() + ")");
                } else {
                    Check.that(reg.error() instanceof VerifyError, s.internalName() + ": a corrupted class fails with VerifyError, got " + reg.error());
                    String named = CorpusVerifier.methodClass(reg.method());
                    if (named.equals(s.internalName())) {
                        Check.eq(s.internalName() + "." + s.method(), reg.method(), s.internalName() + ": the report names the corrupted method, so a regression can be found without rerunning");
                    } else {
                        Check.that(sabotaged.containsKey(named), s.internalName() + ": an error naming another class comes from a corrupted supertype, which HotSpot links first, got " + reg.method());
                    }
                }
            }
            List<String> unexplained = new ArrayList<>();
            for (CorpusVerifier.Regression reg : regressions) {
                if (!sabotaged.containsKey(CorpusVerifier.methodClass(reg.method()))) {
                    unexplained.add(reg.describe());
                }
            }
            System.err.println("[selftest] corrupted " + (oldVerifier + failover + typeChecker) + " classes that link unmodified (" + oldVerifier + " checked by the old verifier, " + failover + " Java 6 classes that fall back to it, " + typeChecker
                    + " checked by the StackMapTable type checker); the harness reported " + regressions.size() + " regressions, including subclasses that fail with their corrupted superclass. One per verifier, as the corpus check prints them:");
            for (String verifier : List.of("(class: ", "Location:")) {
                regressions.stream().filter(r -> String.valueOf(r.error().getMessage()).contains(verifier) && CorpusVerifier.methodClass(r.method()).equals(r.className().replace('.', '/'))).findFirst()
                        .ifPresent(r -> System.err.println("[selftest]   REGRESSION " + r.describe()));
            }
            Check.that(oldVerifier > 0, "the self-test corrupts class files older than Java 6, so it proves the old inference verifier's failures are reported");
            Check.that(failover > 0, "the self-test corrupts Java 6 class files, so it proves failures survive HotSpot's fallback to the old verifier");
            Check.that(typeChecker > 0, "the self-test corrupts Java 7+ class files, so it proves StackMapTable failures are reported");
            Check.eq(List.of(), missed, "every class whose transformed bytes were corrupted is reported as a regression, or the safety net lets broken bytecode through");
            Check.eq(List.of(), unexplained, "every reported regression traces back to a corrupted class, so the harness raises no false alarms");
        }
    }

    private static boolean loads(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader) != null;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static byte[] sabotage(String internalName, byte[] transformed, Map<String, Sabotaged> sabotaged) {
        if (Math.floorMod(internalName.hashCode(), 3) != 0) {
            return transformed;
        }
        ClassModel cm = ClassFile.of().parse(transformed);
        MethodModel target = firstInstrumented(cm);
        if (target == null) {
            return transformed;
        }
        String method = target.methodName().stringValue() + target.methodType().stringValue();
        boolean typeChecked = cm.majorVersion() >= ClassFile.JAVA_7_VERSION;
        CodeTransform corruption = typeChecked ? CodeTransform.ACCEPT_ALL : new CodeTransform() {
            @Override
            public void atStart(CodeBuilder cob) {
                cob.iconst_0();
                cob.invokevirtual(CD_Object, "hashCode", INT_NO_ARGS);
                cob.pop();
            }

            @Override
            public void accept(CodeBuilder cob, CodeElement e) {
                cob.with(e);
            }
        };
        byte[] out;
        try {
            out = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).transformClass(cm, ClassTransform.transformingMethodBodies(m -> sig(m).equals(method), corruption));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return transformed;
        }
        sabotaged.put(internalName, new Sabotaged(internalName, cm.majorVersion(), method, typeChecked ? "StackMapTable dropped" : "hashCode() called on an int"));
        return out;
    }

    private static MethodModel firstInstrumented(ClassModel cm) {
        for (MethodModel mm : cm.methods()) {
            if (mm.code().isEmpty()) {
                continue;
            }
            for (CodeElement e : mm.code().get()) {
                if (e instanceof InvokeInstruction ii && ii.name().equalsString("enter") && ii.owner().asInternalName().equals("io/github/yagipass/verbatime/agent/probe/Probe")) {
                    return mm;
                }
            }
        }
        return null;
    }

    private static String sig(MethodModel mm) {
        return mm.methodName().stringValue() + mm.methodType().stringValue();
    }

    private static byte[] typeError(String internalName, int major) {
        return ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).build(ClassDesc.ofInternalName(internalName), cb -> {
            cb.withVersion(major, 0);
            clinitSettingProperty(cb);
            cb.withMethodBody("m", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, cob -> {
                cob.iconst_0();
                cob.invokevirtual(CD_Object, "hashCode", INT_NO_ARGS);
                cob.pop();
                cob.return_();
            });
        });
    }

    private static byte[] sound(String internalName) {
        return ClassFile.of().build(ClassDesc.ofInternalName(internalName), cb -> {
            cb.withVersion(ClassFile.JAVA_8_VERSION, 0);
            clinitSettingProperty(cb);
            cb.withMethodBody("m", INT_NO_ARGS, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, cob -> {
                cob.iconst_1();
                cob.ireturn();
            });
        });
    }

    private static void clinitSettingProperty(ClassBuilder cb) {
        cb.withSuperclass(CD_Object);
        cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
        cb.withMethodBody("<clinit>", MethodTypeDesc.of(CD_void), ClassFile.ACC_STATIC, cob -> {
            cob.ldc(CLINIT_PROPERTY);
            cob.ldc("yes");
            cob.invokestatic(ClassDesc.of("java.lang.System"), "setProperty", MethodTypeDesc.of(CD_String, CD_String, CD_String));
            cob.pop();
            cob.return_();
        });
    }
}
