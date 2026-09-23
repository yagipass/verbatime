package io.github.yagipass.verbatime.agent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import io.github.yagipass.verbatime.agent.probe.Log;

final class StartupGateTransform {

    private static final ClassDesc GATE = ClassDesc.of("io.github.yagipass.verbatime.agent.probe.StartupGate");

    private static final MethodTypeDesc VOID_NOARG = MethodTypeDesc.of(ConstantDescs.CD_void);

    private static final String MAIN_NAME = "main";

    private static final String MAIN_DESC = "([Ljava/lang/String;)V";

    private static final String MAIN_NOARG_DESC = "()V";

    private static final CodeTransform PREPEND_AWAIT = new CodeTransform() {
        @Override
        public void atStart(final CodeBuilder cob) {
            cob.invokestatic(GATE, "await", VOID_NOARG);
        }

        @Override
        public void accept(final CodeBuilder cob, final CodeElement ce) {
            cob.with(ce);
        }
    };

    private StartupGateTransform() {
    }

    static String launcherMainSig(final ClassModel cm) {
        String noArg = null;
        for (final MethodModel mm : cm.methods()) {
            if (!mm.methodName().equalsString(MAIN_NAME) || mm.code().isEmpty() || (mm.flags().flagsMask() & ClassFile.ACC_PRIVATE) != 0) {
                continue;
            }
            if (mm.methodType().equalsString(MAIN_DESC)) {
                return MAIN_NAME + MAIN_DESC;
            }
            if (mm.methodType().equalsString(MAIN_NOARG_DESC)) {
                noArg = MAIN_NAME + MAIN_NOARG_DESC;
            }
        }
        return noArg;
    }

    static ClassTransform prependAwait(final String sig) {
        return ClassTransform.transformingMethods(mm -> sig.equals(mm.methodName().stringValue() + mm.methodType().stringValue()), MethodTransform.transformingCode(PREPEND_AWAIT));
    }

    static void logArmed(final String binaryName, final String sig) {
        if (sig != null) {
            Log.info("startup gate armed on " + binaryName + "::" + (sig.endsWith(MAIN_NOARG_DESC) ? "main()" : "main(String[])"));
        } else {
            Log.warn("startup gate not armed because " + binaryName + " declares no main(String[]) or main() the java launcher could run. A private main or one inherited from a superclass does not count. waitstart cannot pause this JVM");
        }
    }
}
