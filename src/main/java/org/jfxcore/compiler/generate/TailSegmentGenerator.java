// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;
import static org.jfxcore.compiler.util.ExceptionHelper.*;

public class TailSegmentGenerator extends PropertySegmentGeneratorBase {

    private static final String CHANGE_LISTENERS_ERROR =
        "Cannot add multiple change listeners to a compiled binding.";
    
    private static final String INVALIDATION_LISTENERS_ERROR =
        "Cannot add multiple invalidation listeners to a compiled binding.";

    private static final String CANNOT_SET_READONLY_PROPERTY =
        "Cannot change the value of a read-only property.";

    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";
    private static final String OBSERVABLE_FIELD = "observable";
    private static final String VALUE_FIELD = "value";
    private static final String PRIMITIVE_VALUE_FIELD = "pvalue";
    private static final String FLAGS_FIELD = "flags";
    private static final String VALIDATE_METHOD = "validate";

    private CtConstructor constructor;
    private CtMethod updateMethod;
    private CtMethod invalidatedMethod;
    private CtMethod changedMethod;
    private CtMethod validateMethod;
    private CtClass observableClass;
    private CtClass boxedValueClass;
    private boolean hasInvariants;

    public TailSegmentGenerator(SourceInfo sourceInfo, FoldedGroup[] groups) {
        super(sourceInfo, groups, groups.length - 1);
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        super.emitClass(context);
        observableClass = Objects.requireNonNull(groups[segment].getFirstPathSegment().getTypeInstance().jvmType());
        boxedValueClass = TypeHelper.getBoxedType(groups[segment].getLastPathSegment().getValueTypeInstance().jvmType());
        generatedClass.addInterface(InvalidationListenerType());
        generatedClass.addInterface(ChangeListenerType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField invalidationLister = new CtField(InvalidationListenerType(), INVALIDATION_LISTENER_FIELD, generatedClass);
        invalidationLister.setModifiers(Modifier.PRIVATE);

        CtField changeListener = new CtField(ChangeListenerType(), CHANGE_LISTENER_FIELD, generatedClass);
        changeListener.setModifiers(Modifier.PRIVATE);

        CtField observable = new CtField(observableClass, OBSERVABLE_FIELD, generatedClass);
        observable.setModifiers(Modifier.PRIVATE);

        generatedClass.addField(invalidationLister);
        generatedClass.addField(changeListener);
        generatedClass.addField(observable);

        hasInvariants = groups[segment].getPath().length > 1;

        if (hasInvariants) {
            CtField field = new CtField(CtClass.intType, FLAGS_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);

            field = new CtField(boxedValueClass, VALUE_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);

            if (valueClass.isPrimitive()) {
                field = new CtField(valueClass, PRIMITIVE_VALUE_FIELD, generatedClass);
                field.setModifiers(Modifier.PRIVATE);
                generatedClass.addField(field);
            }
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        if (groups.length == 1) {
            constructor = new CtConstructor(new CtClass[] {observableClass}, generatedClass);
            generatedClass.addConstructor(constructor);
        } else {
            constructor = CtNewConstructor.defaultConstructor(generatedClass);
            updateMethod = new CtMethod(CtClass.voidType, UPDATE_METHOD, new CtClass[] {observableClass}, generatedClass);
            updateMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addConstructor(constructor);
            generatedClass.addMethod(updateMethod);
        }

        invalidatedMethod = new CtMethod(
            CtClass.voidType, "invalidated", new CtClass[] {ObservableType()}, generatedClass);
        invalidatedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(invalidatedMethod);

        changedMethod = new CtMethod(
            CtClass.voidType,
            "changed",
            new CtClass[] {ObservableValueType(), ObjectType(), ObjectType()},
            generatedClass);
        changedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(changedMethod);

        if (hasInvariants) {
            validateMethod = new CtMethod(
                CtClass.voidType,
                VALIDATE_METHOD,
                valueClass.isPrimitive() ? new CtClass[] {CtClass.booleanType} : new CtClass[0],
                    generatedClass);
            validateMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            generatedClass.addMethod(validateMethod);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        if (groups.length == 1) {
            emitConstructor(constructor);
        } else {
            emitUpdateMethod(updateMethod);
        }

        emitChangedMethod(changedMethod);
        emitGetValueMethod(getValueMethod);
        emitSetValueMethod(setValueMethod);
        emitInvalidatedMethod(invalidatedMethod);
        emitAddListenerMethod(addChangeListenerMethod, true);
        emitAddListenerMethod(addInvalidationListenerMethod, false);
        emitRemoveListenerMethod(removeChangeListenerMethod, true);
        emitRemoveListenerMethod(removeInvalidationListenerMethod, false);
        emitGetBeanMethod(getBeanMethod);
        emitGetNameMethod(getNameMethod);

        if (hasInvariants) {
            emitValidateMethod(validateMethod);
        }

        if (valueClass.isPrimitive()) {
            emitGetMethod(getMethod);
            emitSetMethod(setMethod);
        }

        if (isNumeric) {
            emitNumberValueMethod(intValueMethod, CtClass.intType);
            emitNumberValueMethod(longValueMethod, CtClass.longType);
            emitNumberValueMethod(floatValueMethod, CtClass.floatType);
            emitNumberValueMethod(doubleValueMethod, CtClass.doubleType);
        }
    }

    private void emitConstructor(CtConstructor constructor) throws Exception {
        Bytecode code = new Bytecode(constructor.getDeclaringClass(), 2);

        if (groups.length == 1) {
            code.aload(0)
                .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor())
                .aload(0)
                .aload(1)
                .putfield(constructor.getDeclaringClass(), OBSERVABLE_FIELD, observableClass);
        } else {
            // $1.addListener(this);
            code.aload(0)
                .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor())
                .aload(1)
                .aload(0)
                .invokeinterface(
                    ObservableValueType(),
                    "addListener",
                    function(CtClass.voidType, ChangeListenerType()));
        }

        // this.value = getValue();
        code.aload(0)
            .aload(0)
            .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
            .checkcast(boxedValueClass.getName())
            .putfield(generatedClass, VALUE_FIELD, boxedValueClass)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitGetValueMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);
        CtClass declaringClass = method.getDeclaringClass();

        if (hasInvariants) {
            code.aload(0)
                .getfield(declaringClass, FLAGS_FIELD, CtClass.intType)
                .ifeq(
                    // if (this.flags == 0)
                    () -> {
                        if (valueClass.isPrimitive()) {
                            // validate(true)
                            code.aload(0)
                                .iconst(1)
                                .invokevirtual(
                                    declaringClass,
                                    VALIDATE_METHOD,
                                    function(CtClass.voidType, CtClass.booleanType));
                        } else {
                            // validate()
                            code.aload(0)
                                .invokevirtual(
                                    declaringClass,
                                    VALIDATE_METHOD,
                                    function(CtClass.voidType));
                        }
                    },

                    // if (this.flags == 2)
                    () -> {
                        if (valueClass.isPrimitive()) {
                            code.aload(0)
                                .getfield(declaringClass, FLAGS_FIELD, CtClass.intType)
                                .iconst(2)
                                .if_icmpeq(() -> code
                                    .aload(0)
                                    .aload(0)
                                    .getfield(declaringClass, PRIMITIVE_VALUE_FIELD, valueClass)
                                    .ext_box(valueClass)
                                    .putfield(declaringClass, VALUE_FIELD, boxedValueClass)
                                    .aload(0)
                                    .iconst(1)
                                    .putfield(declaringClass, FLAGS_FIELD, CtClass.intType));
                        }
                    });

            // return this.value
            code.aload(0)
                .getfield(declaringClass, VALUE_FIELD, boxedValueClass)
                .areturn();
        } else {
            // return this.observable != null ? this.observable.getValue() : null
            code.aload(0)
                .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
                .dup()
                .ifnull(
                    () -> code.pop().aconst_null(),
                    () -> code.ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                          "getValue", function(ObjectType())))))
                .areturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitSetValueMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);

        if (groups[segment].getLastPathSegment().getObservableKind() == ObservableKind.NONE
                || !observableClass.subtypeOf(WritableValueType())) {
            code.anew(RuntimeExceptionType())
                .dup()
                .ldc(CANNOT_SET_READONLY_PROPERTY)
                .invokespecial(RuntimeExceptionType(), MethodInfo.nameInit, constructor(StringType()))
                .athrow();
        } else {
            // if (this.observable != null)
            Label L0 = code
                .aload(0)
                .getfield(method.getDeclaringClass(), OBSERVABLE_FIELD, observableClass)
                .ifnull();

            // observable.setValue($1)
            code.aload(0)
                .getfield(method.getDeclaringClass(), OBSERVABLE_FIELD, observableClass)
                .aload(1)
                .ext_invoke(observableClass.getMethod("setValue", function(CtClass.voidType, ObjectType())));

            // end if
            L0.resume()
                .vreturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitGetMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        if (hasInvariants) {
            // if (this.valid == 0)
            code.aload(0)
                .getfield(method.getDeclaringClass(), FLAGS_FIELD, CtClass.intType)
                .ifeq(() -> code
                    // validate(false)
                    .aload(0)
                    .iconst(0)
                    .invokevirtual(
                        method.getDeclaringClass(),
                        VALIDATE_METHOD,
                        function(CtClass.voidType, CtClass.booleanType)))
                .aload(0)
                .getfield(method.getDeclaringClass(), PRIMITIVE_VALUE_FIELD, valueClass)
                .ext_return(method.getReturnType());
        } else {
            // return this.observable != null ? this.observable.getValue() : null
            code.aload(0)
                .getfield(method.getDeclaringClass(), OBSERVABLE_FIELD, observableClass)
                .dup()
                .ifnull(
                    () -> code.pop().ext_defaultconst(valueClass),
                    () -> {
                        if (valueClass.isPrimitive()) {
                            code.ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                            "get", function(TypeHelper.getWidenedNumericType(valueClass)))));
                        } else {
                            code.ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                            "getValue", function(ObjectType()))));
                        }
                    })
                .ext_return(method.getReturnType());
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitSetMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1 + TypeHelper.getSlots(valueClass));

        if (groups[segment].getLastPathSegment().getObservableKind() == ObservableKind.NONE
                || !observableClass.subtypeOf(WritableValueType())) {
            code.anew(RuntimeExceptionType())
                .dup()
                .ldc(CANNOT_SET_READONLY_PROPERTY)
                .invokespecial(RuntimeExceptionType(), MethodInfo.nameInit, constructor(StringType()))
                .athrow();
        } else {
            // if (this.observable != null)
            Label L0 = code
                .aload(0)
                .getfield(method.getDeclaringClass(), OBSERVABLE_FIELD, observableClass)
                .ifnull();

            // this.observable.set($1)
            code.aload(0)
                .getfield(method.getDeclaringClass(), OBSERVABLE_FIELD, observableClass)
                .ext_load(valueClass, 1)
                .ext_invoke(observableClass.getMethod("set", function(CtClass.voidType, valueClass)));

            // end if
            L0.resume()
                .vreturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitNumberValueMethod(CtMethod method, CtClass primitiveType) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);
        CtClass wideValueClass = TypeHelper.getWidenedNumericType(valueClass);

        code.aload(0)
            .invokevirtual(method.getDeclaringClass(), "get", function(wideValueClass))
            .ext_primitiveconv(wideValueClass, primitiveType)
            .ext_return(primitiveType);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitInvalidatedMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);

        if (hasInvariants) {
            code.aload(0)
                .iconst(0)
                .putfield(method.getDeclaringClass(), FLAGS_FIELD, CtClass.intType);
        }

        code.aload(0)
            .getfield(method.getDeclaringClass(), INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
            .aload(0)
            .invokeinterface(InvalidationListenerType(), "invalidated", function(CtClass.voidType, ObservableType()))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitAddListenerMethod(CtMethod method, boolean changeListenerIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);
        CtClass declaringClass = method.getDeclaringClass();
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        CtClass listenerType = changeListenerIsTrue ? ChangeListenerType() : InvalidationListenerType();

        // if (this.listener != null)
        code.aload(0)
            .getfield(declaringClass, fieldName, listenerType)
            .ifnonnull(
                () ->
                    // throw new RuntimeException()
                    code.anew(RuntimeExceptionType())
                        .dup()
                        .ldc(changeListenerIsTrue ? CHANGE_LISTENERS_ERROR : INVALIDATION_LISTENERS_ERROR)
                        .invokespecial(RuntimeExceptionType(), MethodInfo.nameInit, constructor(StringType()))
                        .athrow(),
                () -> {
                    // if (this.observable != null)
                    code.aload(0)
                        .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
                        .dup()
                        .ifnonnull(() -> code
                            // observable.addListener($1)
                            .dup()
                            .aload(0)
                            .ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                        "addListener", function(CtClass.voidType, listenerType)))))

                        // this.listener = $1
                        .pop()
                        .aload(0)
                        .aload(1)
                        .putfield(declaringClass, fieldName, listenerType);

                    if (hasInvariants && changeListenerIsTrue) {
                        if (valueClass.isPrimitive()) {
                            // this.validate(true)
                            code.aload(0)
                                .iconst(1)
                                .invokevirtual(declaringClass, VALIDATE_METHOD,
                                               function(CtClass.voidType, CtClass.booleanType));
                        } else {
                            // this.validate()
                            code.aload(0)
                                .invokevirtual(declaringClass, VALIDATE_METHOD, function(CtClass.voidType));
                        }
                    }
                });

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitRemoveListenerMethod(CtMethod method, boolean changeListenerIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);
        CtClass declaringClass = method.getDeclaringClass();
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        CtClass listenerType = changeListenerIsTrue ? ChangeListenerType() : InvalidationListenerType();

        // if (listener != null...
        code.aload(0)
            .getfield(declaringClass, fieldName, listenerType)
            .ifnonnull(() -> code
                // ... && listener.equals($1))
                .aload(0)
                .getfield(declaringClass, fieldName, listenerType)
                .aload(1)
                .invokevirtual(ObjectType(), "equals", function(CtClass.booleanType, ObjectType()))
                .ifne(() -> code
                    // if (observable != null)
                    .aload(0)
                    .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
                    .ifnonnull(() -> code
                        // observable.removeListener(this)
                        .aload(0)
                        .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
                        .aload(0)
                        .ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                    "removeListener", function(CtClass.voidType, listenerType)))))

                    // listener = $1
                    .aload(0)
                    .aconst_null()
                    .putfield(declaringClass, fieldName, listenerType)));

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitUpdateMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);
        CtClass declaringClass = method.getDeclaringClass();

        // if (this.observable != null)
        code.aload(0)
            .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
            .ifnonnull(() -> code
                // if (this.invalidationListener != null)
                .aload(0)
                .getfield(declaringClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
                .ifnonnull(() -> code
                    // this.observable.removeListener((InvalidationListener)this)
                    .aload(0)
                    .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
                    .aload(0)
                    .ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                "removeListener", function(CtClass.voidType, InvalidationListenerType())))))

                // if (this.changeListener != null)
                .aload(0)
                .getfield(declaringClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                .ifnonnull(() -> code
                    // this.observable.removeListener((ChangeListener)this)
                    .aload(0)
                    .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
                    .aload(0)
                    .ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                "removeListener", function(CtClass.voidType, ChangeListenerType()))))));

        // if ($1 != null)
        code.aload(1)
            .ifnonnull(() -> code
                // if (this.invalidationListener != null)
                .aload(0)
                .getfield(declaringClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
                .ifnonnull(() -> code
                    // this.observable.addListener((InvalidationListener)this)
                    .aload(1)
                    .aload(0)
                    .invokeinterface(ObservableValueType(), "addListener",
                                     function(CtClass.voidType, InvalidationListenerType())))

                // if (this.changeListener != null)
                .aload(0)
                .getfield(declaringClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                .ifnonnull(() -> code
                    // this.observable.addListener((ChangeListener)this)
                    .aload(1)
                    .aload(0)
                    .invokeinterface(ObservableValueType(), "addListener",
                                     function(CtClass.voidType, ChangeListenerType()))));

        // this.observable = $1
        code.aload(0)
            .aload(1)
            .putfield(declaringClass, OBSERVABLE_FIELD, observableClass);

        // if (this.invalidationListener != null)
        code.aload(0)
            .getfield(declaringClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
            .ifnonnull(() -> code
                // this.invalidated(null)
                .aload(0)
                .aconst_null()
                .invokeinterface(InvalidationListenerType(), "invalidated",
                                 function(CtClass.voidType, ObservableType())));

        // if (this.changeListener != null)
        code.aload(0)
            .getfield(declaringClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
            .ifnonnull(() -> code
                // this.changed(null, null, $1 != null ? $1.getValue() : null)
                .aload(0)
                .aconst_null()
                .aconst_null()
                .aconst_null()
                .aload(1)
                .ifnonnull(() -> code
                    .pop()
                    .aload(1)
                    .invokeinterface(ObservableValueType(), "getValue", function(ObjectType())))
                .invokeinterface(ChangeListenerType(), "changed",
                                 function(CtClass.voidType, ObservableValueType(), ObjectType(), ObjectType())));

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitChangedMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 4);

        if (hasInvariants) {
            Local oldValue = code.acquireLocal(false);

            if (valueClass.isPrimitive()) {
                // if (flags == 2)
                code.aload(0)
                    .getfield(generatedClass, FLAGS_FIELD, CtClass.booleanType)
                    .iconst(2)
                    .if_icmpeq(
                        () ->
                            // oldValue = this.pvalue
                            code.aload(0)
                                .getfield(generatedClass, PRIMITIVE_VALUE_FIELD, valueClass)
                                .ext_box(valueClass),
                        () ->
                            // oldValue = this.value
                            code.aload(0)
                                .getfield(generatedClass, VALUE_FIELD, boxedValueClass))
                    .ext_store(boxedValueClass, oldValue);
            } else {
                code.aload(0)
                    .getfield(generatedClass, VALUE_FIELD, valueClass)
                    .ext_store(valueClass, oldValue);
            }

            if (valueClass.isPrimitive()) {
                // validate(true)
                code.aload(0)
                    .iconst(1)
                    .invokevirtual(generatedClass, VALIDATE_METHOD, function(CtClass.voidType, CtClass.booleanType));
            } else {
                // validate()
                code.aload(0)
                    .invokevirtual(generatedClass, VALIDATE_METHOD, function(CtClass.voidType));
            }

            // this.changeListener.changed(this, oldValue, newValue)
            code.aload(0)
                .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                .aload(0)
                .aload(oldValue)
                .aload(0)
                .getfield(generatedClass, VALUE_FIELD, boxedValueClass)
                .invokeinterface(ChangeListenerType(), "changed",
                                 function(CtClass.voidType, ObservableValueType(), ObjectType(), ObjectType()))
                .vreturn();

            code.releaseLocal(oldValue);
        } else {
            // this.changeListener.changed(this, $2, $3)
            code.aload(0)
                .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                .aload(0)
                .aload(2)
                .aload(3)
                .invokeinterface(ChangeListenerType(), "changed",
                                 function(CtClass.voidType, ObservableValueType(), ObjectType(), ObjectType()))
                .vreturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitValidateMethod(CtMethod method) throws Exception {
        int localIndex = valueClass.isPrimitive() ? 2 : 1;
        Bytecode code = new Bytecode(method.getDeclaringClass(), TypeHelper.getSlots(valueClass) + localIndex);
        CtClass declaringClass = method.getDeclaringClass();

        // if (this.observable != null)
        code.aload(0)
            .getfield(declaringClass, OBSERVABLE_FIELD, observableClass)
            .dup()
            .ifnull(
                () -> code
                    .pop()
                    .ext_defaultconst(valueClass),
                () -> code
                    // observable.getValue().foo().bar().baz()...
                    .ext_invoke(unchecked(getSourceInfo(), () -> observableClass.getMethod(
                                "getValue", function(ObjectType()))))
                    .dup()
                    .ifnull(
                        () -> code
                            .pop()
                            .ext_defaultconst(valueClass),
                        () -> {
                            code.checkcast(groups[segment].getFirstPathSegment().getValueTypeInstance().jvmType());
                            emitInvariants(valueClass, groups[segment].getPath(), code);
                        }))
            .ext_store(valueClass, localIndex);

        if (valueClass.isPrimitive()) {
            // this.pvalue = $localIndex
            code.aload(0)
                .ext_load(valueClass, localIndex)
                .putfield(declaringClass, PRIMITIVE_VALUE_FIELD, valueClass);

            // if (boxValue)
            code.iload(1)
                .ifne(
                    () -> code
                        // this.value = $1
                        .aload(0)
                        .ext_load(valueClass, localIndex)
                        .ext_box(valueClass)
                        .putfield(declaringClass, VALUE_FIELD, boxedValueClass)
                        .aload(0)
                        .iconst(1) // 1 = valid, boxed
                        .putfield(declaringClass, FLAGS_FIELD, CtClass.intType),
                    () -> code
                        .aload(0)
                        .iconst(2) // 2 = valid, unboxed
                        .putfield(declaringClass, FLAGS_FIELD, CtClass.intType));
        } else {
            // this.value = $localIndex
            code.aload(0)
                .aload(localIndex)
                .checkcast(boxedValueClass)
                .putfield(declaringClass, VALUE_FIELD, boxedValueClass);

            code.aload(0)
                .iconst(1) // 1 = valid, boxed
                .putfield(declaringClass, FLAGS_FIELD, CtClass.intType);
        }

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitGetBeanMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        if (boxedValueClass.subtypeOf(ReadOnlyPropertyType())) {
            code.aload(0)
                .getfield(method.getDeclaringClass(), VALUE_FIELD, boxedValueClass)
                .dup()
                .ifnonnull(
                    () -> code.invokevirtual(boxedValueClass, "getBean", function(ObjectType())),
                    () -> code.pop().aconst_null())
                .areturn();
        } else {
            code.aload(0)
                .getfield(method.getDeclaringClass(), VALUE_FIELD, boxedValueClass)
                .dup()
                .isinstanceof(ReadOnlyPropertyType())
                .ifne(
                    () -> code
                        .checkcast(ReadOnlyPropertyType())
                        .invokeinterface(ReadOnlyPropertyType(), "getBean", function(ObjectType())),
                    () -> code
                        .pop()
                        .aconst_null())
                .areturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitGetNameMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        if (boxedValueClass.subtypeOf(ReadOnlyPropertyType())) {
            code.aload(0)
                .getfield(method.getDeclaringClass(), VALUE_FIELD, boxedValueClass)
                .dup()
                .ifnonnull(
                    () -> code.invokevirtual(boxedValueClass, "getName", function(StringType())),
                    () -> code.pop().aconst_null())
                .areturn();
        } else {
            code.aload(0)
                .getfield(method.getDeclaringClass(), VALUE_FIELD, boxedValueClass)
                .dup()
                .isinstanceof(ReadOnlyPropertyType())
                .ifne(
                    () -> code
                        .checkcast(ReadOnlyPropertyType())
                        .invokeinterface(ReadOnlyPropertyType(), "getName", function(StringType())),
                    () -> code
                        .pop()
                        .aconst_null())
                .areturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }
}
