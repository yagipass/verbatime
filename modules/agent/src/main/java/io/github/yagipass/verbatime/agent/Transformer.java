package io.github.yagipass.verbatime.agent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.format.Vbtm;

public final class Transformer implements ClassFileTransformer {

    private static final String[] NEVER_INSTRUMENTED = { "java/", "jdk/", "sun/", "com/sun/", "io/github/yagipass/verbatime/agent/", "io/github/yagipass/verbatime/format/" };

    private final Config config;

    private final Roots roots;

    private final String gateClassInternal;

    private final AtomicInteger instrumentedClasses = new AtomicInteger();

    private final AtomicInteger instrumentedMethods = new AtomicInteger();

    private final AtomicInteger failedClasses = new AtomicInteger();

    private final AtomicInteger idLimitSkippedClasses = new AtomicInteger();

    private final AtomicBoolean idLimitWarned = new AtomicBoolean();

    Transformer(Config config, Roots roots, String gateClassInternal) {
        this.config = config;
        this.roots = roots;
        this.gateClassInternal = gateClassInternal;
    }

    public static boolean isNeverInstrumented(String internalName) {
        return neverInstrumentedPrefix(internalName) != null;
    }

    static String neverInstrumentedPrefix(String internalName) {
        for (String p : NEVER_INSTRUMENTED) {
            if (internalName.startsWith(p)) {
                return p;
            }
        }
        return null;
    }

    public int instrumentedClasses() {
        return instrumentedClasses.get();
    }

    public int instrumentedMethods() {
        return instrumentedMethods.get();
    }

    public int failedClasses() {
        return failedClasses.get();
    }

    public int idLimitSkippedClasses() {
        return idLimitSkippedClasses.get();
    }

    @SuppressWarnings("ReferenceEquality")
    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (loader == null || loader == ClassLoader.getPlatformClassLoader()) {
            return null;
        }
        if (className == null || isNeverInstrumented(className)) {
            return null;
        }
        boolean isGateClass = className.equals(gateClassInternal);
        boolean selected = config.selects(className);
        if (!selected && !isGateClass) {
            return null;
        }
        try {
            if (!selected) {
                return injectGateOnly(className, classfileBuffer);
            }
            return instrument(className, classfileBuffer, isGateClass);
        } catch (Throwable t) {
            failedClasses.incrementAndGet();
            Log.warn("failed to instrument " + className + ", loading it unchanged: " + t);
            return null;
        }
    }

    byte[] instrument(String internalName, byte[] bytes, boolean injectGate) {
        ClassFile cf = ClassFile.of();
        ClassModel cm = cf.parse(bytes);
        String binaryName = internalName.replace('/', '.');
        String gateSig = injectGate ? StartupGateTransform.launcherMainSig(cm) : null;

        TracingPlan tracing = TracingPlan.plan(cm);
        if (tracing.isEmpty()) {
            return injectGate ? injectGateOnly(cf, cm, binaryName, gateSig) : null;
        }
        int baseId = MethodRegistry.reserveIds(binaryName, tracing.sigs());
        if (baseId == MethodRegistry.LIMIT_REACHED) {
            idLimitSkippedClasses.incrementAndGet();
            if (idLimitWarned.compareAndSet(false, true)) {
                Log.warn("method id limit of " + Vbtm.METHOD_ID_LIMIT + " reached at " + binaryName + ". This and every later class load unchanged until the JVM restarts. Narrow include= to instrument fewer classes");
            }
            return injectGate ? injectGateOnly(cf, cm, binaryName, gateSig) : null;
        }

        byte[] out = TracingPlan.INLINING.transformClass(cm, tracing.transform(baseId, gateSig));
        instrumentedClasses.incrementAndGet();
        instrumentedMethods.addAndGet(tracing.sigs().size());

        MethodRegistry.commitClass(baseId, binaryName, tracing.sigs());
        roots.classCommitted(binaryName, baseId, tracing.sigs());
        if (injectGate) {
            StartupGateTransform.logArmed(binaryName, gateSig);
        }
        return out;
    }

    private static byte[] injectGateOnly(String internalName, byte[] bytes) {
        ClassFile cf = ClassFile.of();
        ClassModel cm = cf.parse(bytes);
        return injectGateOnly(cf, cm, internalName.replace('/', '.'), StartupGateTransform.launcherMainSig(cm));
    }

    private static byte[] injectGateOnly(ClassFile cf, ClassModel cm, String binaryName, String gateSig) {
        StartupGateTransform.logArmed(binaryName, gateSig);
        if (gateSig == null) {
            return null;
        }
        return cf.transformClass(cm, StartupGateTransform.prependAwait(gateSig));
    }
}
