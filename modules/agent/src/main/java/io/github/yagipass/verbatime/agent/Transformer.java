package io.github.yagipass.verbatime.agent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.yagipass.verbatime.agent.probe.Log;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.format.Vbtm;

public final class Transformer implements ClassFileTransformer {

    static final String BODY_SUFFIX = "$trace";

    private static final String[] NEVER_INSTRUMENTED = { "java/", "jdk/", "sun/", "com/sun/", "io/github/yagipass/verbatime/agent/", "io/github/yagipass/verbatime/format/" };

    private final Config config;

    private final Roots roots;

    private final String gateClassInternal;

    private final AtomicInteger instrumentedClasses = new AtomicInteger();

    private final AtomicInteger instrumentedMethods = new AtomicInteger();

    private final AtomicInteger failedClasses = new AtomicInteger();

    private final AtomicInteger idLimitSkippedClasses = new AtomicInteger();

    private final AtomicBoolean idLimitWarned = new AtomicBoolean();

    Transformer(final Config config, final Roots roots, final String gateClassInternal) {
        this.config = config;
        this.roots = roots;
        this.gateClassInternal = gateClassInternal;
    }

    public static boolean isNeverInstrumented(final String internalName) {
        return neverInstrumentedPrefix(internalName) != null;
    }

    static String neverInstrumentedPrefix(final String internalName) {
        for (final String p : NEVER_INSTRUMENTED) {
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
    public byte[] transform(final Module module, final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
        if (loader == null || loader == ClassLoader.getPlatformClassLoader()) {
            return null;
        }
        if (className == null || isNeverInstrumented(className)) {
            return null;
        }
        final boolean isGateClass = className.equals(gateClassInternal);
        final boolean selected = config.selects(className);
        if (!selected && !isGateClass) {
            return null;
        }
        try {
            if (!selected) {
                return injectGateOnly(className, classfileBuffer);
            }
            return instrument(className, classfileBuffer, isGateClass);
        } catch (final Throwable t) {
            failedClasses.incrementAndGet();
            Log.warn("failed to instrument " + className + ", loading it unchanged: " + t);
            return null;
        }
    }

    byte[] instrument(final String internalName, final byte[] bytes, final boolean injectGate) {
        final ClassFile cf = ClassFile.of();
        final ClassModel cm = cf.parse(bytes);
        final String binaryName = internalName.replace('/', '.');
        final String gateSig = injectGate ? StartupGateTransform.launcherMainSig(cm) : null;

        final TracingPlan tracing = TracingPlan.plan(cm);
        if (tracing.isEmpty()) {
            return injectGate ? injectGateOnly(cf, cm, binaryName, gateSig) : null;
        }
        final int baseId = MethodRegistry.reserveIds(binaryName, tracing.sigs());
        if (baseId == MethodRegistry.LIMIT_REACHED) {
            idLimitSkippedClasses.incrementAndGet();
            if (idLimitWarned.compareAndSet(false, true)) {
                Log.warn("method id limit of " + Vbtm.METHOD_ID_LIMIT + " reached at " + binaryName + ". This and every later class load unchanged until the JVM restarts. Narrow include= to instrument fewer classes");
            }
            return injectGate ? injectGateOnly(cf, cm, binaryName, gateSig) : null;
        }

        ClassTransform ct = tracing.transform(baseId);
        if (gateSig != null) {
            ct = ct.andThen(StartupGateTransform.prependAwait(gateSig));
        }
        final byte[] out = cf.transformClass(cm, ct);
        instrumentedClasses.incrementAndGet();
        instrumentedMethods.addAndGet(tracing.sigs().size());

        MethodRegistry.commitClass(baseId, binaryName, tracing.sigs());
        roots.classCommitted(binaryName, baseId, tracing.sigs());
        if (injectGate) {
            StartupGateTransform.logArmed(binaryName, gateSig);
        }
        return out;
    }

    private static byte[] injectGateOnly(final String internalName, final byte[] bytes) {
        final ClassFile cf = ClassFile.of();
        final ClassModel cm = cf.parse(bytes);
        return injectGateOnly(cf, cm, internalName.replace('/', '.'), StartupGateTransform.launcherMainSig(cm));
    }

    private static byte[] injectGateOnly(final ClassFile cf, final ClassModel cm, final String binaryName, final String gateSig) {
        StartupGateTransform.logArmed(binaryName, gateSig);
        if (gateSig == null) {
            return null;
        }
        return cf.transformClass(cm, StartupGateTransform.prependAwait(gateSig));
    }
}
