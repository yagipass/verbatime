package io.github.yagipass.verbatime.agent;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.errorprone.annotations.Var;

final class TracingPlan {

    static final ClassFile INLINING = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS, ClassFile.ShortJumpsOption.FAIL_ON_SHORT_JUMPS);

    static final int JIT_HUGE_METHOD_LIMIT = 8000;

    private static final String PROBE_INTERNAL = "io/github/yagipass/verbatime/agent/probe/Probe";

    private static final ClassDesc PROBE = ClassDesc.ofInternalName(PROBE_INTERNAL);

    private static final MethodTypeDesc VOID_INT = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int);

    private static final MethodTypeDesc VOID_THROWABLE_INT = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Throwable, ConstantDescs.CD_int);

    private static final List<StackMapFrameInfo.VerificationTypeInfo> THROWABLE_ON_STACK = List.of(StackMapFrameInfo.ObjectVerificationTypeInfo.of(ConstantDescs.CD_Throwable));

    private static final int MAX_PROBE_CALL_BYTES = 6;

    private static final int MAX_HANDLER_BYTES = 13;

    private final List<String> sigs;

    private final Map<String, Integer> planned;

    private final boolean needsStackMaps;

    private TracingPlan(List<String> sigs, Map<String, Integer> planned, boolean needsStackMaps) {
        this.sigs = sigs;
        this.planned = planned;
        this.needsStackMaps = needsStackMaps;
    }

    static TracingPlan plan(ClassModel cm) {
        List<String> sigs = new ArrayList<>();
        Map<String, Integer> planned = new HashMap<>();
        if (!referencesProbe(cm)) {
            for (MethodModel mm : cm.methods()) {
                if (unsupportedReason(mm) != null) {
                    continue;
                }
                String sig = mm.methodName().stringValue() + mm.methodType().stringValue();
                planned.put(sig, sigs.size());
                sigs.add(sig);
            }
        }
        return new TracingPlan(List.copyOf(sigs), planned, cm.majorVersion() >= ClassFile.JAVA_6_VERSION);
    }

    List<String> sigs() {
        return sigs;
    }

    boolean isEmpty() {
        return sigs.isEmpty();
    }

    ClassTransform transform(int baseId, String gateSig) {
        if (gateSig != null && !planned.containsKey(gateSig)) {
            throw new IllegalStateException("launcher main " + gateSig + " is not instrumentable, so the startup gate has nowhere to go");
        }
        return (cb, ce) -> {
            if (!(ce instanceof MethodModel mm)) {
                cb.with(ce);
                return;
            }
            String sig = mm.methodName().stringValue() + mm.methodType().stringValue();
            Integer index = planned.get(sig);
            if (index == null) {
                cb.with(ce);
                return;
            }
            List<StackMapFrameInfo> frames = needsStackMaps ? mm.code().flatMap(code -> code.findAttribute(Attributes.stackMapTable())).map(StackMapTableAttribute::entries).orElse(List.of()) : null;
            int declaredLocals = mm.code().get() instanceof CodeAttribute code ? code.maxLocals() : 0;
            cb.transformMethod(mm, MethodTransform.transformingCode(new ProbeCalls(baseId + index, sig.equals(gateSig), frames, declaredLocals, parameterSlots(mm))));
        };
    }

    private static int parameterSlots(MethodModel mm) {
        @Var int slots = mm.flags().has(AccessFlag.STATIC) ? 0 : 1;
        for (ClassDesc p : mm.methodTypeSymbol().parameterList()) {
            slots += TypeKind.from(p).slotSize();
        }
        return slots;
    }

    private static boolean referencesProbe(ClassModel cm) {
        for (PoolEntry e : cm.constantPool()) {
            if (e instanceof ClassEntry ce && ce.name().equalsString(PROBE_INTERNAL)) {
                return true;
            }
        }
        return false;
    }

    private static String unsupportedReason(MethodModel mm) {
        String name = mm.methodName().stringValue();
        if (name.startsWith("<")) {
            return "constructors/initializers are not instrumented";
        }
        if (mm.flags().has(AccessFlag.ABSTRACT)) {
            return "abstract method";
        }
        if (mm.flags().has(AccessFlag.NATIVE)) {
            return "native method";
        }
        if (mm.flags().has(AccessFlag.BRIDGE)) {
            return "bridge method";
        }
        if (mm.code().isEmpty()) {
            return "no Code attribute";
        }
        if (mm.code().get() instanceof CodeAttribute code && pushesPastJitLimit(code)) {
            return "the probe calls would push it past the " + JIT_HUGE_METHOD_LIMIT + "-byte limit above which the JIT never compiles a method";
        }
        return null;
    }

    private static boolean pushesPastJitLimit(CodeAttribute code) {
        int length = code.codeLength();
        if (length > JIT_HUGE_METHOD_LIMIT || length + MAX_PROBE_CALL_BYTES + MAX_HANDLER_BYTES + MAX_PROBE_CALL_BYTES * length <= JIT_HUGE_METHOD_LIMIT) {
            return false;
        }
        return length + MAX_PROBE_CALL_BYTES + MAX_HANDLER_BYTES + MAX_PROBE_CALL_BYTES * returns(code) > JIT_HUGE_METHOD_LIMIT;
    }

    private static int returns(CodeModel code) {
        @Var int n = 0;
        for (CodeElement e : code) {
            if (e instanceof ReturnInstruction) {
                n++;
            }
        }
        return n;
    }

    private static final class ProbeCalls implements CodeTransform {

        private final int id;

        private final boolean gate;

        private final List<StackMapFrameInfo> frames;

        private final int declaredLocals;

        private int touchedLocals;

        private Label bodyStart;

        ProbeCalls(int id, boolean gate, List<StackMapFrameInfo> frames, int declaredLocals, int parameterSlots) {
            this.id = id;
            this.gate = gate;
            this.frames = frames;
            this.declaredLocals = declaredLocals;
            this.touchedLocals = parameterSlots;
        }

        @Override
        public void atStart(CodeBuilder cob) {
            if (gate) {
                StartupGateTransform.emitAwait(cob);
            }
            cob.loadConstant(id);
            cob.invokestatic(PROBE, "enter", VOID_INT);
            bodyStart = cob.newBoundLabel();
        }

        @Override
        public void accept(CodeBuilder cob, CodeElement ce) {
            if (ce instanceof StackMapTableAttribute) {
                return;
            }
            switch (ce) {
                case ReturnInstruction _ -> {
                    cob.loadConstant(id);
                    cob.invokestatic(PROBE, "exit", VOID_INT);
                }
                case LoadInstruction i -> touch(i.slot() + i.typeKind().slotSize());
                case StoreInstruction i -> touch(i.slot() + i.typeKind().slotSize());
                case IncrementInstruction i -> touch(i.slot() + 1);
                case DiscontinuedInstruction.RetInstruction i -> touch(i.slot() + 1);
                default -> {
                }
            }
            cob.with(ce);
        }

        private void touch(int slots) {
            touchedLocals = Math.max(touchedLocals, slots);
        }

        @Override
        public void atEnd(CodeBuilder cob) {
            Label handler = cob.newBoundLabel();
            if (touchedLocals < declaredLocals) {
                cob.aconst_null();
                cob.astore(declaredLocals - 1);
            }
            cob.dup();
            cob.loadConstant(id);
            cob.invokestatic(PROBE, "exitThrow", VOID_THROWABLE_INT);
            cob.athrow();
            cob.exceptionCatchAll(bodyStart, handler, handler);
            if (frames != null) {
                List<StackMapFrameInfo> all = new ArrayList<>(frames.size() + 1);
                all.addAll(frames);
                all.add(StackMapFrameInfo.of(handler, List.of(), THROWABLE_ON_STACK));
                cob.with(StackMapTableAttribute.of(all));
            }
        }
    }
}
