// Copyright (c) 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class InvertBooleanBindingGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("InvertBooleanBinding");

    private static final String EXCEPTION_MESSAGE_OUTER =
            "Bidirectional binding failed, setting to the previous value.";

    private static final String EXCEPTION_MESSAGE_INNER =
            "Bidirectional binding failed together with an attempt"
            + " to restore the source property to the previous value."
            + " Removing the bidirectional binding from both properties.";

    private CtConstructor constructor;
    private CtMethod getProperty1Method;
    private CtMethod getProperty2Method;
    private CtMethod wasGarbageCollectedMethod;
    private CtMethod changedMethod;
    private CtMethod bindMethod;
    private CtField property1Field;
    private CtField property2Field;
    private CtField updatingField;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return TypeInstance.booleanType();
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.addInterface(ChangeListenerType());
        generatedClass.addInterface(WeakListenerType());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        updatingField = new CtField(CtClass.booleanType, "updating", generatedClass);
        updatingField.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(updatingField);

        property1Field = new CtField(WeakReferenceType(), "property1", generatedClass);
        property1Field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(property1Field);

        property2Field = new CtField(WeakReferenceType(), "property2", generatedClass);
        property2Field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(property2Field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor(new CtClass[] {PropertyType(), PropertyType()}, generatedClass);
        constructor.setModifiers(Modifier.PUBLIC);
        generatedClass.addConstructor(constructor);

        getProperty1Method = new CtMethod(PropertyType(), "getProperty1", new CtClass[0], generatedClass);
        getProperty1Method.setModifiers(Modifier.PRIVATE);
        generatedClass.addMethod(getProperty1Method);

        getProperty2Method = new CtMethod(PropertyType(), "getProperty2", new CtClass[0], generatedClass);
        getProperty2Method.setModifiers(Modifier.PRIVATE);
        generatedClass.addMethod(getProperty2Method);

        wasGarbageCollectedMethod = new CtMethod(
            CtClass.booleanType, "wasGarbageCollected", new CtClass[0], generatedClass);
        wasGarbageCollectedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(wasGarbageCollectedMethod);

        changedMethod = new CtMethod(
            CtClass.voidType, "changed",
            new CtClass[] {ObservableValueType(), ObjectType(), ObjectType()}, generatedClass);
        changedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(changedMethod);

        bindMethod = new CtMethod(
            CtClass.voidType, "bindBidirectional",
            new CtClass[] {PropertyType(), PropertyType()}, generatedClass);
        bindMethod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
        generatedClass.addMethod(bindMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context, constructor);
        emitBindMethod(context, bindMethod);
        emitChangedMethod(context, changedMethod);
        emitWasGarbageCollectedMethod(context, wasGarbageCollectedMethod);
        emitGetPropertyMethod(context, getProperty1Method, property1Field);
        emitGetPropertyMethod(context, getProperty2Method, property2Field);
    }

    private void emitConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 3, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor());

        // this.property1 = new WeakReference($1)
        code.aload(0)
            .anew(WeakReferenceType())
            .dup()
            .aload(1)
            .invokespecial(WeakReferenceType(), MethodInfo.nameInit, constructor(ObjectType()))
            .putfield(generatedClass, property1Field.getName(), WeakReferenceType());

        // this.property2 = new WeakReference($2)
        code.aload(0)
            .anew(WeakReferenceType())
            .dup()
            .aload(2)
            .invokespecial(WeakReferenceType(), MethodInfo.nameInit, constructor(ObjectType()))
            .putfield(generatedClass, property2Field.getName(), WeakReferenceType())
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitBindMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 3, -1);
        Bytecode code = context.getOutput();
        Local bindingLocal = code.acquireLocal(false);

        // var binding = new InvertedBooleanBinding($0, $1)
        code.anew(generatedClass)
            .dup()
            .aload(0)
            .aload(1)
            .invokespecial(generatedClass, MethodInfo.nameInit, constructor(PropertyType(), PropertyType()))
            .astore(bindingLocal);

        // $0.setValue(!$1.getValue())
        code.aload(0)
            .aload(1)
            .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
            .checkcast(BooleanType())
            .ext_unbox(SourceInfo.none(), BooleanType(), CtClass.booleanType)
            .ifne(() -> code.iconst(0), () -> code.iconst(1))
            .ext_box(CtClass.booleanType)
            .invokeinterface(WritableValueType(), "setValue", function(CtClass.voidType, ObjectType()));

        // $0.addListener(binding)
        code.aload(0)
            .aload(bindingLocal)
            .invokeinterface(ObservableValueType(), "addListener", function(CtClass.voidType, ChangeListenerType()));

        // $1.addListener(binding)
        code.aload(1)
            .aload(bindingLocal)
            .invokeinterface(ObservableValueType(), "addListener", function(CtClass.voidType, ChangeListenerType()))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitWasGarbageCollectedMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(generatedClass, "getProperty1", function(PropertyType()))
            .ifnull(() -> code.iconst(1).ireturn())
            .aload(0)
            .invokespecial(generatedClass, "getProperty2", function(PropertyType()))
            .ifnull(() -> code.iconst(1).ireturn())
            .iconst(0)
            .ireturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitGetPropertyMethod(BytecodeEmitContext parentContext,
                                       CtMethod method,
                                       CtField propertyField) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, propertyField.getName(), WeakReferenceType())
            .invokevirtual(WeakReferenceType(), "get", function(ObjectType()))
            .areturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitChangedMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 4, -1);
        Bytecode code = context.getOutput();
        Local property1Local = code.acquireLocal(false);
        Local property2Local = code.acquireLocal(false);
        Local anyNullLocal = code.acquireLocal(false);
        Local flippedNewValueLocal = code.acquireLocal(false);

        // if (updating) return
        code.aload(0)
            .getfield(generatedClass, updatingField.getName(), CtClass.booleanType)
            .ifne(code::vreturn);

        // Property property1 = getProperty1()
        code.aload(0)
            .invokespecial(generatedClass, "getProperty1", function(PropertyType()))
            .astore(property1Local);

        // Property property2 = getProperty2()
        code.aload(0)
            .invokespecial(generatedClass, "getProperty2", function(PropertyType()))
            .astore(property2Local);

        // boolean anyNull = property1 == null || property2 == null
        code.aload(property1Local)
            .ifnull(() -> code.iconst(1), () -> code.iconst(0))
            .ifeq(
                () -> code
                    .aload(property2Local)
                    .ifnull(() -> code.iconst(1), () -> code.iconst(0)),
                () -> code.iconst(1))
            .istore(anyNullLocal);

        Runnable emitRemoveListeners = () -> code
            // if (property1 == null) property1.removeListener(this)
            .aload(property1Local)
            .ifnonnull(() -> code
                .aload(property1Local)
                .aload(0)
                .invokeinterface(ObservableValueType(), "removeListener",
                                 function(CtClass.voidType, ChangeListenerType())))

            // if (property2 == null) property2.removeListener(this)
            .aload(property2Local)
            .ifnonnull(() -> code
                .aload(property2Local)
                .aload(0)
                .invokeinterface(ObservableValueType(), "removeListener",
                                 function(CtClass.voidType, ChangeListenerType())));

        Runnable emitUpdateValue = () -> {
            // this.updating = true
            code.aload(0)
                .iconst(1)
                .putfield(generatedClass, updatingField.getName(), CtClass.booleanType)

                // boolean flippedValue = !newValue
                .aload(3)
                .checkcast(BooleanType())
                .dup()
                .ifnull(
                    () -> code.pop().iconst(0),
                    () -> code.ext_unbox(SourceInfo.none(), BooleanType(), CtClass.booleanType))
                .ifne(
                    () -> code.iconst(0),
                    () -> code.iconst(1))
                .ext_box(CtClass.booleanType)
                .astore(flippedNewValueLocal);

            int start = code.position();

            // if (property1 == sourceProperty)
            code.aload(property1Local)
                .aload(1)
                .if_acmpeq(
                    // property2.setValue(flippedValue)
                    () -> code
                        .aload(property2Local)
                        .aload(flippedNewValueLocal)
                        .invokeinterface(WritableValueType(), "setValue", function(CtClass.voidType, ObjectType())),

                    // property1.setValue(flippedValue)
                    () -> code
                        .aload(property1Local)
                        .aload(flippedNewValueLocal)
                        .invokeinterface(WritableValueType(), "setValue", function(CtClass.voidType, ObjectType())))

                // this.updating = false
                .aload(0)
                .iconst(0)
                .putfield(generatedClass, updatingField.getName(), CtClass.booleanType);

            int end = code.position();
            Label label = code.goto_label();
            int handler = code.position();
            CatchBlock innerBlock = emitOuterCatchBlock(code, property1Local, property2Local);
            code.addExtraStackSize(2);
            label.resume();

            int throwableInfo = code.getConstPool().addClassInfo(ThrowableType());
            code.getExceptionTable().add(start, end, handler, throwableInfo);
            code.getExceptionTable().add(innerBlock.start, innerBlock.end, innerBlock.handler, throwableInfo);
        };

        // if (anyNull)
        code.iload(anyNullLocal)
            .ifeq(emitUpdateValue, emitRemoveListeners)
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private CatchBlock emitOuterCatchBlock(Bytecode code, Local property1Local, Local property2Local) {
        Local flippedOldValueLocal = code.acquireLocal(false);
        Local outerExceptionLocal = code.acquireLocal(false);

        // boolean flippedValue = !oldValue
        code.astore(outerExceptionLocal)
            .aload(2)
            .checkcast(BooleanType())
            .dup()
            .ifnull(
                () -> code.pop().iconst(0),
                () -> code.ext_unbox(SourceInfo.none(), BooleanType(), CtClass.booleanType))
            .ifne(
                () -> code.iconst(0),
                () -> code.iconst(1))
            .ext_box(CtClass.booleanType)
            .astore(flippedOldValueLocal);

        int start = code.position();

        // if (property1 == sourceProperty)
        code.aload(property1Local)
            .aload(1)
            .if_acmpeq(
                // property1.setValue(flippedValue)
                () -> code
                    .aload(property1Local)
                    .aload(flippedOldValueLocal)
                    .invokeinterface(WritableValueType(), "setValue", function(CtClass.voidType, ObjectType())),

                // property2.setValue(flippedValue)
                () -> code
                    .aload(property2Local)
                    .aload(flippedOldValueLocal)
                    .invokeinterface(WritableValueType(), "setValue", function(CtClass.voidType, ObjectType())));

        // this.updating = false
        code.aload(0)
            .iconst(0)
            .putfield(generatedClass, updatingField.getName(), CtClass.booleanType);

        int end = code.position();
        Label label = code.goto_label();
        int handler = code.position();
        emitInnerCatchBlock(code, outerExceptionLocal, property1Local, property2Local);
        label.resume();

        // throw new RuntimeException(EXCEPTION_MESSAGE_OUTER, e1)
        code.anew(RuntimeExceptionType())
            .dup()
            .ldc(EXCEPTION_MESSAGE_OUTER)
            .aload(outerExceptionLocal)
            .invokespecial(RuntimeExceptionType(), MethodInfo.nameInit, constructor(StringType(), ThrowableType()))
            .athrow();

        return new CatchBlock(start, end, handler);
    }

    private void emitInnerCatchBlock(Bytecode code, Local outerExceptionLocal,
                                     Local property1Local, Local property2Local) {
        Local exceptionLocal = code.acquireLocal(false);

        // e1.addSuppressed(e2)
        code.astore(exceptionLocal)
            .aload(exceptionLocal)
            .aload(outerExceptionLocal)
            .invokevirtual(ThrowableType(), "addSuppressed", function(CtClass.voidType, ThrowableType()));

        // property1.removeListener(this)
        code.aload(property1Local)
            .aload(0)
            .invokeinterface(ObservableValueType(), "removeListener", function(CtClass.voidType, ChangeListenerType()));

        // property2.removeListener(this)
        code.aload(property2Local)
            .aload(0)
            .invokeinterface(ObservableValueType(), "removeListener", function(CtClass.voidType, ChangeListenerType()));

        // this.updating = false
        code.aload(0)
            .iconst(0)
            .putfield(generatedClass, updatingField.getName(), CtClass.booleanType);

        // throw new RuntimeException(EXCEPTION_MESSAGE_INNER, e1)
        code.anew(RuntimeExceptionType())
            .dup()
            .ldc(EXCEPTION_MESSAGE_INNER)
            .aload(exceptionLocal)
            .invokespecial(RuntimeExceptionType(), MethodInfo.nameInit, constructor(StringType(), ThrowableType()))
            .athrow();

        code.releaseLocal(exceptionLocal);
    }

    private record CatchBlock(int start, int end, int handler) {}
}
