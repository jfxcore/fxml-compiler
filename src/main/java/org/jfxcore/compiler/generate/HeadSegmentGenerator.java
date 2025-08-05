// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.function.Consumer;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class HeadSegmentGenerator extends PropertySegmentGeneratorBase {

    private final Resolver resolver;
    private CtConstructor constructor;
    private CtMethod changedMethod;

    public HeadSegmentGenerator(SourceInfo sourceInfo, FoldedGroup[] groups) {
        super(sourceInfo, groups, 0);
        this.resolver = new Resolver(sourceInfo);
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        super.emitClass(context);
        generatedClass.addInterface(ChangeListenerType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(groups[segment + 1].getCompiledClass(), NEXT_FIELD, generatedClass);
        field.setModifiers(Modifier.FINAL);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor(new CtClass[] {ObservableValueType()}, generatedClass);
        generatedClass.addConstructor(constructor);

        changedMethod = new CtMethod(
            CtClass.voidType,
            "changed",
            new CtClass[] {ObservableValueType(), ObjectType(), ObjectType()},
                generatedClass);
        changedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(changedMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(constructor);
        emitChangedMethod(changedMethod);
        emitGetValueMethod(getValueMethod);
        emitSetValueMethod(setValueMethod);
        emitAddInvalidationListenerMethod(addInvalidationListenerMethod);
        emitRemoveInvalidationListenerMethod(removeInvalidationListenerMethod);
        emitAddChangeListenerMethod(addChangeListenerMethod);
        emitRemoveChangeListenerMethod(removeChangeListenerMethod);
        emitGetBeanMethod(getBeanMethod);
        emitGetNameMethod(getNameMethod);

        if (valueClass.isPrimitive()) {
            emitGetMethod(getMethod);
            emitSetMethod(setMethod);
        }

        if (isNumeric) {
            emitIntValueMethod(intValueMethod);
            emitLongValueMethod(longValueMethod);
            emitFloatValueMethod(floatValueMethod);
            emitDoubleValueMethod(doubleValueMethod);
        }
    }

    private void emitGetValueMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code -> code
            .ext_invoke(nextClassMethod("getValue", function(ObjectType())))
            .areturn());
    }

    private void emitSetValueMethod(CtMethod method) throws Exception {
        expandPath(method, 2, code -> code
            .aload(1)
            .ext_invoke(nextClassMethod("setValue", function(CtClass.voidType, ObjectType())))
            .vreturn());
    }

    private void emitAddInvalidationListenerMethod(CtMethod method) throws Exception {
        expandPath(method, 2, code -> code
            .aload(1)
            .ext_invoke(nextClassMethod("addListener", function(CtClass.voidType, InvalidationListenerType())))
            .vreturn());
    }

    private void emitRemoveInvalidationListenerMethod(CtMethod method) throws Exception {
        expandPath(method, 2, code -> code
            .aload(1)
            .ext_invoke(nextClassMethod("removeListener", function(CtClass.voidType, InvalidationListenerType())))
            .vreturn());
    }

    private void emitAddChangeListenerMethod(CtMethod method) throws Exception {
        expandPath(method, 2, code -> code
            .aload(1)
            .ext_invoke(nextClassMethod("addListener", function(CtClass.voidType, ChangeListenerType())))
            .vreturn());
    }

    private void emitRemoveChangeListenerMethod(CtMethod method) throws Exception {
        expandPath(method, 2, code -> code
            .aload(1)
            .ext_invoke(nextClassMethod("removeListener", function(CtClass.voidType, ChangeListenerType())))
            .vreturn());
    }

    private void emitGetMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code ->
            code.ext_invoke(nextClassMethod("get", function(valueClass)))
                .ext_return(valueClass));
    }

    private void emitSetMethod(CtMethod method) throws Exception {
        expandPath(method, 1 + TypeHelper.getSlots(valueClass), code ->
            code.ext_load(valueClass, 1)
                .ext_invoke(nextClassMethod("set", function(CtClass.voidType, valueClass)))
                .vreturn());
    }

    private void emitIntValueMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code -> code
            .ext_invoke(nextClassMethod("intValue", function(CtClass.intType)))
            .ireturn());
    }

    private void emitLongValueMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code -> code
            .ext_invoke(nextClassMethod("longValue", function(CtClass.longType)))
            .lreturn());
    }

    private void emitFloatValueMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code -> code
            .ext_invoke(nextClassMethod("floatValue", function(CtClass.floatType)))
            .freturn());
    }

    private void emitDoubleValueMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code -> code
            .ext_invoke(nextClassMethod("doubleValue", function(CtClass.doubleType)))
            .dreturn());
    }

    private void emitGetBeanMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code -> code
            .ext_invoke(nextClassMethod("getBean", function(ObjectType())))
            .areturn());
    }

    private void emitGetNameMethod(CtMethod method) throws Exception {
        expandPath(method, 1, code -> code
            .ext_invoke(nextClassMethod("getName", function(StringType())))
            .areturn());
    }

    private void expandPath(CtMethod method, int locals, Consumer<Bytecode> fixup) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), locals);
        code.aload(0);

        for (int i = 0; i < groups.length - 1; ++i) {
            code.getfield(groups[i].getCompiledClass(), NEXT_FIELD, groups[i + 1].getCompiledClass());
        }

        fixup.accept(code);
        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitConstructor(CtConstructor constructor) throws Exception {
        Bytecode code = new Bytecode(constructor.getDeclaringClass(), 2);
        String className = constructor.getDeclaringClass().getName();
        String nextClassName = groups[segment + 1].getCompiledClass().getName();

        // this.next = new NextClassName();
        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor())
            .aload(0)
            .anew(nextClassName)
            .dup()
            .invokespecial(nextClassName, MethodInfo.nameInit, constructor())
            .putfield(resolver.resolveClass(className), NEXT_FIELD, resolver.resolveClass(nextClassName));

        // $1.addListener(this);
        code.aload(1)
            .aload(0)
            .invokeinterface(
                ObservableValueType(),
                "addListener",
                function(CtClass.voidType, ChangeListenerType()));

        // this.changed(null, null, $1.getValue());
        code.aload(0)
            .aconst_null()
            .aconst_null()
            .aload(1)
            .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
            .invokevirtual(className, "changed",
                           function(CtClass.voidType, ObservableValueType(), ObjectType(), ObjectType()))
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitChangedMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 4);
        String nextClassName = groups[segment + 1].getCompiledClass().getName();
        CtClass nextObservableType = groups[segment + 1].getFirstPathSegment().getTypeInstance().jvmType();
        CtClass firstValueType = groups[segment].getFirstPathSegment().getValueTypeInstance().jvmType();
        Segment[] path = this.groups[segment].getPath();

        if (path.length > 1) {
            // if ($3 != null)
            Label L0 = code
                .aload(3)
                .aconst_null()
                .astore(3)
                .dup()
                .ifnull();

            // (T)$3
            code.dup()
                .checkcast(firstValueType.getName());

            // .foo.bar().baz...
            emitInvariants(firstValueType, path, code);

            code.astore(3);

            // end if
            L0.resume().pop();
        }

        code.aload(0)
            .getfield(generatedClass, NEXT_FIELD, resolver.resolveClass(nextClassName))
            .aload(3)
            .invokevirtual(nextClassName, UPDATE_METHOD, function(CtClass.voidType, nextObservableType))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private CtMethod nextClassMethod(String name, String desc) {
        return ExceptionHelper.unchecked(
            getSourceInfo(), () -> groups[segment + 1].getCompiledClass().getMethod(name, desc));
    }
}
