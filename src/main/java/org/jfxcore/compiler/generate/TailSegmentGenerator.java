// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Objects;

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

    private final Resolver resolver;
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
        this.resolver = new Resolver(sourceInfo);
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        super.emitClass(context);
        observableClass = Objects.requireNonNull(groups[segment].getFirstPathSegment().getTypeInstance().jvmType());
        boxedValueClass = TypeHelper.getBoxedType(groups[segment].getLastPathSegment().getValueTypeInstance().jvmType());
        clazz.addInterface(Classes.InvalidationListenerType());
        clazz.addInterface(Classes.ChangeListenerType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField invalidationLister = new CtField(Classes.InvalidationListenerType(), mangle(INVALIDATION_LISTENER_FIELD), clazz);
        invalidationLister.setModifiers(Modifier.PRIVATE);

        CtField changeListener = new CtField(Classes.ChangeListenerType(), mangle(CHANGE_LISTENER_FIELD), clazz);
        changeListener.setModifiers(Modifier.PRIVATE);

        CtField observable = new CtField(observableClass, mangle(OBSERVABLE_FIELD), clazz);
        observable.setModifiers(Modifier.PRIVATE);

        clazz.addField(invalidationLister);
        clazz.addField(changeListener);
        clazz.addField(observable);

        if (hasInvariants = groups[segment].getPath().length > 1) {
            CtField field = new CtField(CtClass.intType, mangle(FLAGS_FIELD), clazz);
            field.setModifiers(Modifier.PRIVATE);
            clazz.addField(field);

            field = new CtField(boxedValueClass, mangle(VALUE_FIELD), clazz);
            field.setModifiers(Modifier.PRIVATE);
            clazz.addField(field);

            if (valueClass.isPrimitive()) {
                field = new CtField(valueClass, mangle(PRIMITIVE_VALUE_FIELD), clazz);
                field.setModifiers(Modifier.PRIVATE);
                clazz.addField(field);
            }
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        if (groups.length == 1) {
            constructor = new CtConstructor(new CtClass[] {observableClass}, clazz);
            clazz.addConstructor(constructor);
        } else {
            constructor = CtNewConstructor.defaultConstructor(clazz);
            updateMethod = new CtMethod(CtClass.voidType, UPDATE_METHOD, new CtClass[] {observableClass}, clazz);
            updateMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addConstructor(constructor);
            clazz.addMethod(updateMethod);
        }

        invalidatedMethod = new CtMethod(
            CtClass.voidType, "invalidated", new CtClass[] {Classes.ObservableType()}, clazz);
        invalidatedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(invalidatedMethod);

        changedMethod = new CtMethod(
            CtClass.voidType,
            "changed",
            new CtClass[] {Classes.ObservableValueType(), Classes.ObjectType(), Classes.ObjectType()},
            clazz);
        changedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(changedMethod);

        if (hasInvariants) {
            validateMethod = new CtMethod(
                CtClass.voidType,
                VALIDATE_METHOD,
                valueClass.isPrimitive() ? new CtClass[] {CtClass.booleanType} : new CtClass[0],
                clazz);
            validateMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            clazz.addMethod(validateMethod);
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
                .invokespecial(Classes.ObjectType(), MethodInfo.nameInit, Descriptors.constructor())
                .aload(0)
                .aload(1)
                .putfield(constructor.getDeclaringClass(), mangle(OBSERVABLE_FIELD), observableClass);
        } else {
            // $1.addListener(this);
            code.aload(0)
                .invokespecial(Classes.ObjectType(), MethodInfo.nameInit, Descriptors.constructor())
                .aload(1)
                .aload(0)
                .invokeinterface(
                    Classes.ObservableValueType(),
                    "addListener",
                    Descriptors.function(CtClass.voidType, Classes.ChangeListenerType()));
        }

        // this.value = getValue();
        code.aload(0)
            .aload(0)
            .invokeinterface(Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType()))
            .checkcast(boxedValueClass.getName())
            .putfield(clazz, mangle(VALUE_FIELD), boxedValueClass)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitGetValueMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);
        CtClass declaringClass = method.getDeclaringClass();

        if (hasInvariants) {
            code.aload(0)
                .getfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType)
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
                                    Descriptors.function(CtClass.voidType, CtClass.booleanType));
                        } else {
                            // validate()
                            code.aload(0)
                                .invokevirtual(
                                    declaringClass,
                                    VALIDATE_METHOD,
                                    Descriptors.function(CtClass.voidType));
                        }
                    },

                    // if (this.flags == 2)
                    () -> {
                        if (valueClass.isPrimitive()) {
                            code.aload(0)
                                .getfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType)
                                .iconst(2)
                                .if_icmpeq(() -> code
                                    .aload(0)
                                    .aload(0)
                                    .getfield(declaringClass, mangle(PRIMITIVE_VALUE_FIELD), valueClass)
                                    .ext_box(valueClass)
                                    .putfield(declaringClass, mangle(VALUE_FIELD), boxedValueClass)
                                    .aload(0)
                                    .iconst(1)
                                    .putfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType));
                        }
                    });

            // return this.value
            code.aload(0)
                .getfield(declaringClass, mangle(VALUE_FIELD), boxedValueClass)
                .areturn();
        } else {
            // return this.observable != null ? this.observable.getValue() : null
            code.aload(0)
                .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
                .dup()
                .ifnull(
                    () -> code.pop().aconst_null(),
                    () -> code.invokeinterface(
                        Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType())))
                .areturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitSetValueMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);

        if (groups[segment].getLastPathSegment().getObservableKind() == ObservableKind.NONE) {
            code.anew(Classes.RuntimeExceptionType())
                .dup()
                .ldc(CANNOT_SET_READONLY_PROPERTY)
                .invokespecial(Classes.RuntimeExceptionType(), MethodInfo.nameInit, Descriptors.constructor(Classes.StringType()))
                .athrow();
        } else {
            // if (this.observable != null)
            Label L0 = code
                .aload(0)
                .getfield(method.getDeclaringClass(), mangle(OBSERVABLE_FIELD), observableClass)
                .ifnull();

            // observable.setValue($1)
            code.aload(0)
                .getfield(method.getDeclaringClass(), mangle(OBSERVABLE_FIELD), observableClass)
                .aload(1)
                .invokeinterface(
                    Classes.WritableValueType(),
                    "setValue",
                    Descriptors.function(CtClass.voidType, Classes.ObjectType()));

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
                .getfield(method.getDeclaringClass(), mangle(FLAGS_FIELD), CtClass.intType)
                .ifeq(() -> code
                    // validate(false)
                    .aload(0)
                    .iconst(0)
                    .invokevirtual(
                        method.getDeclaringClass(),
                        VALIDATE_METHOD,
                        Descriptors.function(CtClass.voidType, CtClass.booleanType)))
                .aload(0)
                .getfield(method.getDeclaringClass(), mangle(PRIMITIVE_VALUE_FIELD), valueClass)
                .ext_return(method.getReturnType());
        } else {
            // return this.observable != null ? this.observable.getValue() : null
            code.aload(0)
                .getfield(method.getDeclaringClass(), mangle(OBSERVABLE_FIELD), observableClass)
                .dup()
                .ifnull(
                    () -> code.pop().ext_defaultconst(valueClass),
                    () -> {
                        if (valueClass.isPrimitive()) {
                            code.invokeinterface(
                                resolver.getObservableClass(observableClass, false),
                                "get",
                                Descriptors.function(TypeHelper.getWidenedNumericType(valueClass)));
                        } else {
                            code.invokeinterface(
                                Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType()));
                        }
                    })
                .ext_return(method.getReturnType());
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitSetMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1 + TypeHelper.getSlots(valueClass));

        if (groups[segment].getLastPathSegment().getObservableKind() == ObservableKind.NONE) {
            code.anew(Classes.RuntimeExceptionType())
                .dup()
                .ldc(CANNOT_SET_READONLY_PROPERTY)
                .invokespecial(Classes.RuntimeExceptionType(), MethodInfo.nameInit, Descriptors.constructor(Classes.StringType()))
                .athrow();
        } else {
            // if (this.observable != null)
            Label L0 = code
                .aload(0)
                .getfield(method.getDeclaringClass(), mangle(OBSERVABLE_FIELD), observableClass)
                .ifnull();

            // this.observable.set($1)
            code.aload(0)
                .getfield(method.getDeclaringClass(), mangle(OBSERVABLE_FIELD), observableClass)
                .ext_load(valueClass, 1)
                .invokeinterface(
                    resolver.getObservableClass(observableClass, false),
                    "set",
                    Descriptors.function(CtClass.voidType, valueClass));

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
            .invokevirtual(method.getDeclaringClass(), "get", Descriptors.function(wideValueClass))
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
                .putfield(method.getDeclaringClass(), mangle(FLAGS_FIELD), CtClass.intType);
        }

        code.aload(0)
            .getfield(method.getDeclaringClass(), mangle(INVALIDATION_LISTENER_FIELD), Classes.InvalidationListenerType())
            .aload(0)
            .invokeinterface(
                Classes.InvalidationListenerType(),
                "invalidated",
                Descriptors.function(CtClass.voidType, Classes.ObservableType()))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitAddListenerMethod(CtMethod method, boolean changeListenerIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);
        CtClass declaringClass = method.getDeclaringClass();
        String fieldName = mangle(changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD);
        CtClass listenerType = changeListenerIsTrue ? Classes.ChangeListenerType() : Classes.InvalidationListenerType();

        // if (this.listener != null)
        code.aload(0)
            .getfield(declaringClass, fieldName, listenerType)
            .ifnonnull(
                () ->
                    // throw new RuntimeException()
                    code.anew(Classes.RuntimeExceptionType())
                        .dup()
                        .ldc(changeListenerIsTrue ? CHANGE_LISTENERS_ERROR : INVALIDATION_LISTENERS_ERROR)
                        .invokespecial(Classes.RuntimeExceptionType(), MethodInfo.nameInit, Descriptors.constructor(Classes.StringType()))
                        .athrow(),
                () -> {
                    // if (this.observable != null)
                    code.aload(0)
                        .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
                        .dup()
                        .ifnonnull(() -> code
                            // observable.addListener($1)
                            .dup()
                            .aload(0)
                            .invokeinterface(
                                Classes.ObservableValueType(),
                                "addListener",
                                Descriptors.function(CtClass.voidType, listenerType)))

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
                                .invokevirtual(
                                    declaringClass,
                                    VALIDATE_METHOD,
                                    Descriptors.function(CtClass.voidType, CtClass.booleanType));
                        } else {
                            // this.validate()
                            code.aload(0)
                                .invokevirtual(
                                    declaringClass,
                                    VALIDATE_METHOD,
                                    Descriptors.function(CtClass.voidType));
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
        String fieldName = mangle(changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD);
        CtClass listenerType = changeListenerIsTrue ? Classes.ChangeListenerType() : Classes.InvalidationListenerType();

        // if (listener != null...
        code.aload(0)
            .getfield(declaringClass, fieldName, listenerType)
            .ifnonnull(() -> code
                // ... && listener.equals($1))
                .aload(0)
                .getfield(declaringClass, fieldName, listenerType)
                .aload(1)
                .invokevirtual(
                    Classes.ObjectType(), "equals", Descriptors.function(CtClass.booleanType, Classes.ObjectType()))
                .ifne(() -> code
                    // if (observable != null)
                    .aload(0)
                    .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
                    .ifnonnull(() -> code
                        // observable.removeListener(this)
                        .aload(0)
                        .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
                        .aload(0)
                        .invokeinterface(
                            changeListenerIsTrue ? Classes.ObservableValueType() : Classes.ObservableType(),
                            "removeListener",
                            Descriptors.function(CtClass.voidType, listenerType)))

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
            .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
            .ifnonnull(() -> code
                // if (this.invalidationListener != null)
                .aload(0)
                .getfield(declaringClass, mangle(INVALIDATION_LISTENER_FIELD), Classes.InvalidationListenerType())
                .ifnonnull(() -> code
                    // this.observable.removeListener((InvalidationListener)this)
                    .aload(0)
                    .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
                    .aload(0)
                    .invokeinterface(
                        Classes.ObservableValueType(),
                        "removeListener",
                        Descriptors.function(CtClass.voidType, Classes.InvalidationListenerType())))

                // if (this.changeListener != null)
                .aload(0)
                .getfield(declaringClass, mangle(CHANGE_LISTENER_FIELD), Classes.ChangeListenerType())
                .ifnonnull(() -> code
                    // this.observable.removeListener((ChangeListener)this)
                    .aload(0)
                    .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
                    .aload(0)
                    .invokeinterface(
                        Classes.ObservableValueType(),
                        "removeListener",
                        Descriptors.function(CtClass.voidType, Classes.ChangeListenerType()))));

        // if ($1 != null)
        code.aload(1)
            .ifnonnull(() -> code
                // if (this.invalidationListener != null)
                .aload(0)
                .getfield(declaringClass, mangle(INVALIDATION_LISTENER_FIELD), Classes.InvalidationListenerType())
                .ifnonnull(() -> code
                    // this.observable.addListener((InvalidationListener)this)
                    .aload(1)
                    .aload(0)
                    .invokeinterface(
                        Classes.ObservableValueType(),
                        "addListener",
                        Descriptors.function(CtClass.voidType, Classes.InvalidationListenerType())))

                // if (this.changeListener != null)
                .aload(0)
                .getfield(declaringClass, mangle(CHANGE_LISTENER_FIELD), Classes.ChangeListenerType())
                .ifnonnull(() -> code
                    // this.observable.addListener((ChangeListener)this)
                    .aload(1)
                    .aload(0)
                    .invokeinterface(
                        Classes.ObservableValueType(),
                        "addListener",
                        Descriptors.function(CtClass.voidType, Classes.ChangeListenerType()))));

        // this.observable = $1
        code.aload(0)
            .aload(1)
            .putfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass);

        // if (this.invalidationListener != null)
        code.aload(0)
            .getfield(declaringClass, mangle(INVALIDATION_LISTENER_FIELD), Classes.InvalidationListenerType())
            .ifnonnull(() -> code
                // this.invalidated(null)
                .aload(0)
                .aconst_null()
                .invokeinterface(
                    Classes.InvalidationListenerType(),
                    "invalidated",
                    Descriptors.function(CtClass.voidType, Classes.ObservableType())));

        // if (this.changeListener != null)
        code.aload(0)
            .getfield(declaringClass, mangle(CHANGE_LISTENER_FIELD), Classes.ChangeListenerType())
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
                    .invokeinterface(
                        Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType())))
                .invokeinterface(
                    Classes.ChangeListenerType(),
                    "changed",
                    Descriptors.function(CtClass.voidType, Classes.ObservableValueType(), Classes.ObjectType(), Classes.ObjectType())));

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
                    .getfield(clazz, mangle(FLAGS_FIELD), CtClass.booleanType)
                    .iconst(2)
                    .if_icmpeq(
                        () ->
                            // oldValue = this.pvalue
                            code.aload(0)
                                .getfield(clazz, mangle(PRIMITIVE_VALUE_FIELD), valueClass)
                                .ext_box(valueClass),
                        () ->
                            // oldValue = this.value
                            code.aload(0)
                                .getfield(clazz, mangle(VALUE_FIELD), boxedValueClass))
                    .ext_store(boxedValueClass, oldValue);
            } else {
                code.aload(0)
                    .getfield(clazz, mangle(VALUE_FIELD), valueClass)
                    .ext_store(valueClass, oldValue);
            }

            if (valueClass.isPrimitive()) {
                // validate(true)
                code.aload(0)
                    .iconst(1)
                    .invokevirtual(clazz, VALIDATE_METHOD, Descriptors.function(CtClass.voidType, CtClass.booleanType));
            } else {
                // validate()
                code.aload(0)
                    .invokevirtual(clazz, VALIDATE_METHOD, Descriptors.function(CtClass.voidType));
            }

            // this.changeListener.changed(this, oldValue, newValue)
            code.aload(0)
                .getfield(clazz, mangle(CHANGE_LISTENER_FIELD), Classes.ChangeListenerType())
                .aload(0)
                .aload(oldValue)
                .aload(0)
                .getfield(clazz, mangle(VALUE_FIELD), boxedValueClass)
                .invokeinterface(
                    Classes.ChangeListenerType(),
                    "changed",
                    Descriptors.function(CtClass.voidType, Classes.ObservableValueType(), Classes.ObjectType(), Classes.ObjectType()))
                .vreturn();

            code.releaseLocal(oldValue);
        } else {
            // this.changeListener.changed(this, $2, $3)
            code.aload(0)
                .getfield(clazz, mangle(CHANGE_LISTENER_FIELD), Classes.ChangeListenerType())
                .aload(0)
                .aload(2)
                .aload(3)
                .invokeinterface(
                    Classes.ChangeListenerType(),
                    "changed",
                    Descriptors.function(CtClass.voidType, Classes.ObservableValueType(), Classes.ObjectType(), Classes.ObjectType()))
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
            .getfield(declaringClass, mangle(OBSERVABLE_FIELD), observableClass)
            .dup()
            .ifnull(
                () -> code
                    .pop()
                    .ext_defaultconst(valueClass),
                () -> code
                    // observable.getValue().foo().bar().baz()...
                    .invokeinterface(Classes.ObservableValueType(), "getValue", Descriptors.function(Classes.ObjectType()))
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
                .putfield(declaringClass, mangle(PRIMITIVE_VALUE_FIELD), valueClass);

            // if (boxValue)
            code.iload(1)
                .ifne(
                    () -> code
                        // this.value = $1
                        .aload(0)
                        .ext_load(valueClass, localIndex)
                        .ext_box(valueClass)
                        .putfield(declaringClass, mangle(VALUE_FIELD), boxedValueClass)
                        .aload(0)
                        .iconst(1) // 1 = valid, boxed
                        .putfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType),
                    () -> code
                        .aload(0)
                        .iconst(2) // 2 = valid, unboxed
                        .putfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType));
        } else {
            // this.value = $localIndex
            code.aload(0)
                .aload(localIndex)
                .checkcast(boxedValueClass)
                .putfield(declaringClass, mangle(VALUE_FIELD), boxedValueClass);

            code.aload(0)
                .iconst(1) // 1 = valid, boxed
                .putfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType);
        }

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitGetBeanMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        // TODO: Figure out how to deal with getBean()
        code.aconst_null()
            .areturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitGetNameMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        // TODO: Figure out how to deal with getName()
        code.aconst_null()
            .areturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }
    
}
