package io.github.yagipass.verbatime.agent;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TracingPlan {

    private static final ClassDesc PROBE = ClassDesc.of("io.github.yagipass.verbatime.agent.probe.Probe");

    private static final MethodTypeDesc VOID_INT = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int);

    private static final MethodTypeDesc VOID_THROWABLE_INT = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Throwable, ConstantDescs.CD_int);

    private final ClassDesc owner;

    private final boolean isInterface;

    private final List<String> sigs;

    private final Map<String, Integer> planned;

    private TracingPlan(final ClassDesc owner, final boolean isInterface, final List<String> sigs, final Map<String, Integer> planned) {
        this.owner = owner;
        this.isInterface = isInterface;
        this.sigs = sigs;
        this.planned = planned;
    }

    static TracingPlan plan(final ClassModel cm) {
        final Set<String> existing = new HashSet<>();
        for (final MethodModel mm : cm.methods()) {
            existing.add(mm.methodName().stringValue() + mm.methodType().stringValue());
        }
        final List<String> sigs = new ArrayList<>();
        final Map<String, Integer> planned = new HashMap<>();
        for (final MethodModel mm : cm.methods()) {
            if (unsupportedReason(mm, existing) != null) {
                continue;
            }
            final String sig = mm.methodName().stringValue() + mm.methodType().stringValue();
            planned.put(sig, sigs.size());
            sigs.add(sig);
        }
        return new TracingPlan(cm.thisClass().asSymbol(), cm.flags().has(AccessFlag.INTERFACE), List.copyOf(sigs), planned);
    }

    List<String> sigs() {
        return sigs;
    }

    boolean isEmpty() {
        return sigs.isEmpty();
    }

    ClassTransform transform(final int baseId) {
        return (cb, ce) -> {
            if (!(ce instanceof final MethodModel mm)) {
                cb.with(ce);
                return;
            }
            final String name = mm.methodName().stringValue();
            final Integer index = planned.get(name + mm.methodType().stringValue());
            if (index == null) {
                cb.with(ce);
                return;
            }

            final int flags = mm.flags().flagsMask();
            final boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
            final MethodTypeDesc mtd = mm.methodTypeSymbol();
            final String bodyName = name + Transformer.BODY_SUFFIX;
            final int id = baseId + index;

            final int bodyFlags = (flags & ~(ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_SYNCHRONIZED)) | ClassFile.ACC_PRIVATE | ClassFile.ACC_SYNTHETIC;
            cb.withMethod(bodyName, mtd, bodyFlags, mb -> {
                for (final MethodElement me : mm) {
                    if (me instanceof CodeModel) {
                        mb.with(me);
                    }
                }
            });

            cb.withMethod(name, mtd, flags, mb -> {
                for (final MethodElement me : mm) {
                    if (!(me instanceof CodeModel) && !(me instanceof AccessFlags)) {
                        mb.with(me);
                    }
                }
                mb.withCode(cob -> emitWrapper(cob, bodyName, mtd, isStatic, id));
            });
        };
    }

    private static String unsupportedReason(final MethodModel mm, final Set<String> existing) {
        final String name = mm.methodName().stringValue();
        if (name.startsWith("<")) {
            return "constructors/initializers are not instrumented";
        }
        if (name.endsWith(Transformer.BODY_SUFFIX)) {
            return "already instrumented";
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
        if (existing.contains(name + Transformer.BODY_SUFFIX + mm.methodType().stringValue())) {
            return "a method named " + name + Transformer.BODY_SUFFIX + " already exists";
        }
        return null;
    }

    private void emitWrapper(final CodeBuilder cob, final String bodyName, final MethodTypeDesc mtd, final boolean isStatic, final int id) {
        final TypeKind returnKind = TypeKind.from(mtd.returnType());

        cob.loadConstant(id);
        cob.invokestatic(PROBE, "enter", VOID_INT);
        cob.trying(tb -> {
            int slot = 0;
            if (!isStatic) {
                tb.aload(0);
                slot = 1;
            }
            for (int i = 0; i < mtd.parameterCount(); i++) {
                final TypeKind k = TypeKind.from(mtd.parameterType(i));
                tb.loadLocal(k, slot);
                slot += k.slotSize();
            }
            if (isStatic) {
                tb.invokestatic(owner, bodyName, mtd, isInterface);
            } else {
                tb.invokespecial(owner, bodyName, mtd, isInterface);
            }
            tb.loadConstant(id);
            tb.invokestatic(PROBE, "exit", VOID_INT);
            tb.return_(returnKind);
        }, catchBuilder -> catchBuilder.catchingAll(hb -> {
            hb.dup();
            hb.loadConstant(id);
            hb.invokestatic(PROBE, "exitThrow", VOID_THROWABLE_INT);
            hb.athrow();
        }));
    }
}
