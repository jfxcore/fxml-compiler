// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class TailSegmentGenerator extends PropertySegmentGeneratorBase {

    private static final String CHANGE_LISTENERS_ERROR =
        "Cannot add multiple change listeners to a compiled binding.";

    private static final String INVALIDATION_LISTENERS_ERROR =
        "Cannot add multiple invalidation listeners to a compiled binding.";

    private static final String CANNOT_SET_READONLY_PROPERTY =
        "Cannot change the value of a read-only property.";

    private static final String SOURCE_WEAK_INVALIDATION_LISTENER_FIELD = "sourceWeakInvalidationListener";
    private static final String SOURCE_WEAK_CHANGE_LISTENER_FIELD = "sourceWeakChangeListener";
    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";
    private static final String OBSERVABLE_FIELD = "observable";
    private static final String VALUE_FIELD = "value";
    private static final String PRIMITIVE_VALUE_FIELD = "pvalue";
    private static final String FLAGS_FIELD = "flags";
    private static final String VALIDATE_METHOD = "validate";

    private ConstructorDeclaration constructor;
    private MethodDeclaration updateMethod;
    private MethodDeclaration invalidatedMethod;
    private MethodDeclaration changedMethod;
    private MethodDeclaration validateMethod;
    private TypeDeclaration observableClass;
    private TypeDeclaration boxedValueClass;
    private ObservableDependencyKind dependencyKind;
    private boolean hasInvariants;
    private boolean rootSourceIsNonNull;

    public TailSegmentGenerator(SourceInfo sourceInfo, FoldedGroup[] groups) {
        super(sourceInfo, groups, groups.length - 1);
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        observableClass = groups[segment].getObservableDependencyType();
        boxedValueClass = groups[segment].getLastPathSegment().getValueTypeInstance().declaration().boxed();
        dependencyKind = groups[segment].getObservableDependencyKind();
        rootSourceIsNonNull = groups.length == 1 && !groups[segment].getFirstPathSegment().hasValueSource();

        return super.emitClass(context)
            .addInterface(InvalidationListenerDecl())
            .addInterface(ChangeListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(CHANGE_LISTENER_FIELD, ChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(OBSERVABLE_FIELD, observableClass).setModifiers(Modifier.PRIVATE);
        createField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD, WeakInvalidationListenerDecl())
            .setModifiers(Modifier.PRIVATE);

        if (dependencyKind != ObservableDependencyKind.CONTENT) {
            createField(SOURCE_WEAK_CHANGE_LISTENER_FIELD, WeakChangeListenerDecl())
                .setModifiers(Modifier.PRIVATE);
        }

        hasInvariants = groups[segment].getPath().length > 1 || dependencyKind == ObservableDependencyKind.CONTENT;

        if (hasInvariants) {
            createField(FLAGS_FIELD, intDecl()).setModifiers(Modifier.PRIVATE);
            createField(VALUE_FIELD, boxedValueClass).setModifiers(Modifier.PRIVATE);

            if (valueClass.isPrimitive()) {
                createField(PRIMITIVE_VALUE_FIELD, valueClass).setModifiers(Modifier.PRIVATE);
            }
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        if (groups.length == 1) {
            constructor = createConstructor(observableClass);
        } else {
            constructor = createDefaultConstructor();
            updateMethod = createMethod(UPDATE_METHOD, voidDecl(), observableClass);
            updateMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }

        invalidatedMethod = createMethod("invalidated", voidDecl(), ObservableDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        changedMethod = createMethod("changed", voidDecl(), ObservableValueDecl(), ObjectDecl(), ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        if (hasInvariants) {
            validateMethod = createMethod(
                    VALIDATE_METHOD, voidDecl(),
                    valueClass.isPrimitive() ? new TypeDeclaration[] { booleanDecl() } : new TypeDeclaration[0])
                .setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
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
            emitNumberValueMethod(intValueMethod, intDecl());
            emitNumberValueMethod(longValueMethod, longDecl());
            emitNumberValueMethod(floatValueMethod, floatDecl());
            emitNumberValueMethod(doubleValueMethod, doubleDecl());
        }
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        Bytecode code = new Bytecode(constructor);

        if (groups.length == 1) {
            code.aload(0)
                .invoke(requireSuperClass().requireDeclaredConstructor())
                .aload(0)
                .aload(1)
                .putfield(requireDeclaredField(OBSERVABLE_FIELD));
        }

        if (hasInvariants) {
            // this.value = getValue();
            code.aload(0)
                .aload(0)
                .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                .checkcast(boxedValueClass)
                .putfield(requireDeclaredField(VALUE_FIELD));
        }

        code.vreturn();

        constructor.setCode(code);
    }

    private void emitGetValueMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (hasInvariants) {
            code.aload(0)
                .getfield(requireDeclaredField(FLAGS_FIELD))
                .ifeq(
                    // if (this.flags == 0)
                    () -> {
                        if (valueClass.isPrimitive()) {
                            // validate(true)
                            code.aload(0)
                                .iconst(1)
                                .invoke(requireDeclaredMethod(VALIDATE_METHOD, booleanDecl()));
                        } else {
                            // validate()
                            code.aload(0)
                                .invoke(requireDeclaredMethod(VALIDATE_METHOD));
                        }
                    },

                    // if (this.flags == 2)
                    () -> {
                        if (valueClass.isPrimitive()) {
                            code.aload(0)
                                .getfield(requireDeclaredField(FLAGS_FIELD))
                                .iconst(2)
                                .if_icmpeq(() -> code
                                    .aload(0)
                                    .aload(0)
                                    .getfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD))
                                    .box(valueClass)
                                    .putfield(requireDeclaredField(VALUE_FIELD))
                                    .aload(0)
                                    .iconst(1)
                                    .putfield(requireDeclaredField(FLAGS_FIELD)));
                        }
                    });

            // return this.value
            code.aload(0)
                .getfield(requireDeclaredField(VALUE_FIELD))
                .areturn();
        } else {
            // return this.observable != null ? this.observable.getValue() : null
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .dup()
                .ifnull(
                    () -> code.pop().aconst_null(),
                    () -> code.invoke(ObservableValueDecl().requireDeclaredMethod("getValue")))
                .areturn();
        }

        method.setCode(code);
    }

    private void emitSetValueMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (groups[segment].getLastPathSegment().getValueSourceKind() == ValueSourceKind.NONE
                || !observableClass.subtypeOf(WritableValueDecl())) {
            code.anew(RuntimeExceptionDecl())
                .dup()
                .ldc(CANNOT_SET_READONLY_PROPERTY)
                .invoke(RuntimeExceptionDecl().requireDeclaredConstructor(StringDecl()))
                .athrow();
        } else {
            // if (this.observable != null)
            Label L0 = code
                .aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .ifnull();

            // observable.setValue($1)
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .aload(1)
                .invoke(WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl()));

            // end if
            L0.resume()
                .vreturn();
        }

        method.setCode(code);
    }

    private void emitGetMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (hasInvariants) {
            // if (this.valid == 0)
            code.aload(0)
                .getfield(requireDeclaredField(FLAGS_FIELD))
                .ifeq(() -> code
                    // validate(false)
                    .aload(0)
                    .iconst(0)
                    .invoke(requireDeclaredMethod(VALIDATE_METHOD, booleanDecl())))
                .aload(0)
                .getfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD))
                .ret(method.returnType());
        } else {
            // return this.observable != null ? this.observable.getValue() : null
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .dup()
                .ifnull(
                    () -> code.pop().defaultconst(valueClass),
                    () -> {
                        if (valueClass.isPrimitive()) {
                            code.invoke(observableClass.requireMethod("get"));
                        } else {
                            code.invoke(observableClass.requireMethod("getValue"));
                        }
                    })
                .ret(method.returnType());
        }

        method.setCode(code);
    }

    private void emitSetMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (groups[segment].getLastPathSegment().getValueSourceKind() == ValueSourceKind.NONE
                || !observableClass.subtypeOf(WritableValueDecl())) {
            code.anew(RuntimeExceptionDecl())
                .dup()
                .ldc(CANNOT_SET_READONLY_PROPERTY)
                .invoke(RuntimeExceptionDecl().requireDeclaredConstructor(StringDecl()))
                .athrow();
        } else {
            // if (this.observable != null)
            Label L0 = code
                .aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .ifnull();

            // this.observable.set($1)
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .load(valueClass, 1)
                .invoke(observableClass.requireMethod("set", valueClass));

            // end if
            L0.resume()
                .vreturn();
        }

        method.setCode(code);
    }

    private void emitNumberValueMethod(MethodDeclaration method, TypeDeclaration primitiveType) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .invoke(requireDeclaredMethod("get"))
            .primconv(widenShortInt(valueClass), primitiveType)
            .ret(primitiveType);

        method.setCode(code);
    }

    private void emitInvalidatedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (dependencyKind == ObservableDependencyKind.CONTENT) {
            Local oldValue = code.acquireLocal(false);

            code.aload(0)
                .getfield(requireDeclaredField(VALUE_FIELD))
                .store(boxedValueClass, oldValue)
                .aload(0)
                .iconst(0)
                .putfield(requireDeclaredField(FLAGS_FIELD));

            code.aload(0)
                .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                .ifnonnull(() -> {
                    if (valueClass.isPrimitive()) {
                        code.aload(0)
                            .iconst(1)
                            .invoke(requireDeclaredMethod(VALIDATE_METHOD, booleanDecl()));
                    } else {
                        code.aload(0)
                            .invoke(requireDeclaredMethod(VALIDATE_METHOD));
                    }

                    code.aload(0)
                        .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                        .aload(0)
                        .aload(oldValue)
                        .aload(0)
                        .getfield(requireDeclaredField(VALUE_FIELD))
                        .invoke(ChangeListenerDecl().requireDeclaredMethod(
                                    "changed",ObservableValueDecl(), ObjectDecl(), ObjectDecl()));
                });

            code.aload(0)
                .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                .ifnonnull(() -> code
                    .aload(0)
                    .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                    .aload(0)
                    .invoke(InvalidationListenerDecl().requireDeclaredMethod("invalidated", ObservableDecl())))
                .vreturn();

            code.releaseLocal(oldValue);
        } else if (hasInvariants) {
            code.aload(0)
                .iconst(0)
                .putfield(requireDeclaredField(FLAGS_FIELD));
        }

        code.aload(0)
            .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
            .aload(0)
            .invoke(InvalidationListenerDecl().requireDeclaredMethod("invalidated", ObservableDecl()))
            .vreturn();

        method.setCode(code);
    }

    private void emitAddListenerMethod(MethodDeclaration method, boolean changeListenerIsTrue) {
        Bytecode code = new Bytecode(method);
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        MethodDeclaration listenerMethod = dependencyKind == ObservableDependencyKind.CONTENT
            ? ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl())
            : changeListenerIsTrue
                ? ObservableValueDecl().requireDeclaredMethod("addListener", ChangeListenerDecl())
                : ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl());

        emitInitSourceListenerField(code, changeListenerIsTrue);

        // if (this.listener != null)
        code.aload(0)
            .getfield(requireDeclaredField(fieldName))
            .ifnonnull(
                () ->
                    // throw new RuntimeException()
                    code.anew(RuntimeExceptionDecl())
                        .dup()
                        .ldc(changeListenerIsTrue ? CHANGE_LISTENERS_ERROR : INVALIDATION_LISTENERS_ERROR)
                        .invoke(RuntimeExceptionDecl().requireDeclaredConstructor(StringDecl()))
                        .athrow(),
                () -> {
                    if (rootSourceIsNonNull) {
                        code.aload(0)
                            .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                            .aload(0)
                            .getfield(getSourceListenerField(changeListenerIsTrue))
                            .invoke(listenerMethod)
                            .aload(0)
                            .aload(1)
                            .putfield(requireDeclaredField(fieldName));
                    } else {
                        // if (this.observable != null)
                        code.aload(0)
                            .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                            .dup()
                            .ifnonnull(() -> code
                                // observable.addListener(this.sourceWeak...Listener)
                                .dup()
                                .aload(0)
                                .getfield(getSourceListenerField(changeListenerIsTrue))
                                .invoke(listenerMethod))

                            // this.listener = $1
                            .pop()
                            .aload(0)
                            .aload(1)
                            .putfield(requireDeclaredField(fieldName));
                    }

                    if (hasInvariants && changeListenerIsTrue) {
                        if (valueClass.isPrimitive()) {
                            // this.validate(true)
                            code.aload(0)
                                .iconst(1)
                                .invoke(requireDeclaredMethod(VALIDATE_METHOD, booleanDecl()));
                        } else {
                            // this.validate()
                            code.aload(0)
                                .invoke(requireDeclaredMethod(VALIDATE_METHOD));
                        }
                    }
                });

        code.vreturn();

        method.setCode(code);
    }

    private void emitRemoveListenerMethod(MethodDeclaration method, boolean changeListenerIsTrue) {
        Bytecode code = new Bytecode(method);
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        MethodDeclaration listenerMethod = dependencyKind == ObservableDependencyKind.CONTENT
            ? ObservableDecl().requireDeclaredMethod("removeListener", InvalidationListenerDecl())
            : changeListenerIsTrue
                ? ObservableValueDecl().requireDeclaredMethod("removeListener", ChangeListenerDecl())
                : ObservableDecl().requireDeclaredMethod("removeListener", InvalidationListenerDecl());

        // if (listener != null...
        code.aload(0)
            .getfield(requireDeclaredField(fieldName))
            .ifnonnull(() -> code
                // ... && listener.equals($1))
                .aload(0)
                .getfield(requireDeclaredField(fieldName))
                .aload(1)
                .invoke(ObjectDecl().requireDeclaredMethod("equals", ObjectDecl()))
                .ifne(() -> code
                    .aload(0)
                    .getfield(getSourceListenerField(changeListenerIsTrue))
                    .ifnonnull(() -> {
                        if (rootSourceIsNonNull) {
                            code.aload(0)
                                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                                .aload(0)
                                .getfield(getSourceListenerField(changeListenerIsTrue))
                                .invoke(listenerMethod);
                        } else {
                            code.aload(0)
                                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                                .ifnonnull(() -> code
                                    .aload(0)
                                    .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                                    .aload(0)
                                    .getfield(getSourceListenerField(changeListenerIsTrue))
                                    .invoke(listenerMethod));
                        }
                    })

                    // listener = $1
                    .aload(0)
                    .aconst_null()
                    .putfield(requireDeclaredField(fieldName))));

        code.vreturn();

        method.setCode(code);
    }

    private void emitUpdateMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (dependencyKind == ObservableDependencyKind.CONTENT) {
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .ifnonnull(() -> code
                    .aload(0)
                    .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        .aload(0)
                        .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                        .aload(0)
                        .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                        .invoke(ObservableDecl().requireDeclaredMethod("removeListener", InvalidationListenerDecl())))
                    .aload(0)
                    .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        .aload(0)
                        .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                        .ifnull(() -> code
                            .aload(0)
                            .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                            .aload(0)
                            .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                            .invoke(ObservableDecl().requireDeclaredMethod("removeListener", InvalidationListenerDecl())))));

            code.aload(1)
                .ifnonnull(() -> code
                    .aload(0)
                    .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        .aload(1)
                        .aload(0)
                        .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                        .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl())))
                    .aload(0)
                    .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        .aload(0)
                        .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                        .ifnull(() -> code
                            .aload(1)
                            .aload(0)
                            .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                            .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl())))));
        } else {
            // if (this.observable != null)
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .ifnonnull(() -> code
                    // if (this.sourceWeakInvalidationListener != null)
                    .aload(0)
                    .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        // this.observable.removeListener(this.sourceWeakInvalidationListener)
                        .aload(0)
                        .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                        .aload(0)
                        .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                        .invoke(ObservableDecl().requireDeclaredMethod("removeListener", InvalidationListenerDecl())))

                    // if (this.sourceWeakChangeListener != null)
                    .aload(0)
                    .getfield(requireDeclaredField(SOURCE_WEAK_CHANGE_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        // this.observable.removeListener(this.sourceWeakChangeListener)
                        .aload(0)
                        .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                        .aload(0)
                        .getfield(requireDeclaredField(SOURCE_WEAK_CHANGE_LISTENER_FIELD))
                        .invoke(ObservableValueDecl().requireDeclaredMethod("removeListener", ChangeListenerDecl()))));

            // if ($1 != null)
            code.aload(1)
                .ifnonnull(() -> code
                    // if (this.sourceWeakInvalidationListener != null)
                    .aload(0)
                    .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        .aload(1)
                        .aload(0)
                        .getfield(requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD))
                        .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl())))

                    // if (this.sourceWeakChangeListener != null)
                    .aload(0)
                    .getfield(requireDeclaredField(SOURCE_WEAK_CHANGE_LISTENER_FIELD))
                    .ifnonnull(() -> code
                        .aload(1)
                        .aload(0)
                        .getfield(requireDeclaredField(SOURCE_WEAK_CHANGE_LISTENER_FIELD))
                        .invoke(ObservableValueDecl().requireDeclaredMethod("addListener", ChangeListenerDecl()))));
        }

        // this.observable = $1
        code.aload(0)
            .aload(1)
            .putfield(requireDeclaredField(OBSERVABLE_FIELD));

        // if (this.invalidationListener != null)
        code.aload(0)
            .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
            .ifnonnull(() -> code
                // this.invalidated(null)
                .aload(0)
                .aconst_null()
                .invoke(InvalidationListenerDecl().requireDeclaredMethod("invalidated", ObservableDecl())));

        // if (this.changeListener != null)
        code.aload(0)
            .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
            .ifnonnull(() -> {
                if (dependencyKind == ObservableDependencyKind.CONTENT) {
                    code.aload(0)
                        .aconst_null()
                        .invoke(InvalidationListenerDecl().requireDeclaredMethod("invalidated", ObservableDecl()));
                } else {
                    code.aload(0)
                        .aconst_null()
                        .aconst_null()
                        .aconst_null()
                        .aload(1)
                        .ifnonnull(() -> code
                            .pop()
                            .aload(1)
                            .invoke(ObservableValueDecl().requireDeclaredMethod("getValue")))
                        .invoke(ChangeListenerDecl().requireDeclaredMethod("changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl()));
                }
            });

        code.vreturn();

        method.setCode(code);
    }

    private void emitInitSourceListenerField(Bytecode code, boolean changeListenerIsTrue) {
        FieldDeclaration field = getSourceListenerField(changeListenerIsTrue);

        code.aload(0)
            .getfield(field)
            .ifnull(() -> code
                .aload(0)
                .anew(field.type())
                .dup()
                .aload(0)
                .invoke(field.type().requireConstructor(
                    field.name().equals(SOURCE_WEAK_CHANGE_LISTENER_FIELD)
                        ? ChangeListenerDecl()
                        : InvalidationListenerDecl()))
                .putfield(field));
    }

    private FieldDeclaration getSourceListenerField(boolean changeListenerIsTrue) {
        return dependencyKind == ObservableDependencyKind.CONTENT || !changeListenerIsTrue
            ? requireDeclaredField(SOURCE_WEAK_INVALIDATION_LISTENER_FIELD)
            : requireDeclaredField(SOURCE_WEAK_CHANGE_LISTENER_FIELD);
    }

    private void emitChangedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (hasInvariants) {
            Local oldValue = code.acquireLocal(false);

            if (valueClass.isPrimitive()) {
                // if (flags == 2)
                code.aload(0)
                    .getfield(requireDeclaredField(FLAGS_FIELD))
                    .iconst(2)
                    .if_icmpeq(
                        () ->
                            // oldValue = this.pvalue
                            code.aload(0)
                                .getfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD))
                                .box(valueClass),
                        () ->
                            // oldValue = this.value
                            code.aload(0)
                                .getfield(requireDeclaredField(VALUE_FIELD)))
                    .store(boxedValueClass, oldValue);
            } else {
                code.aload(0)
                    .getfield(requireDeclaredField(VALUE_FIELD))
                    .store(valueClass, oldValue);
            }

            if (valueClass.isPrimitive()) {
                // validate(true)
                code.aload(0)
                    .iconst(1)
                    .invoke(requireDeclaredMethod(VALIDATE_METHOD, booleanDecl()));
            } else {
                // validate()
                code.aload(0)
                    .invoke(requireDeclaredMethod(VALIDATE_METHOD));
            }

            // this.changeListener.changed(this, oldValue, newValue)
            code.aload(0)
                .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                .aload(0)
                .aload(oldValue)
                .aload(0)
                .getfield(requireDeclaredField(VALUE_FIELD))
                .invoke(ChangeListenerDecl().requireDeclaredMethod("changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl()))
                .vreturn();

            code.releaseLocal(oldValue);
        } else {
            // this.changeListener.changed(this, $2, $3)
            code.aload(0)
                .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                .aload(0)
                .aload(2)
                .aload(3)
                .invoke(ChangeListenerDecl().requireDeclaredMethod("changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl()))
                .vreturn();
        }

        method.setCode(code);
    }

    private void emitValidateMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);
        Local valueLocal = code.acquireLocal(valueClass);

        if (rootSourceIsNonNull) {
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD));

            if (dependencyKind != ObservableDependencyKind.CONTENT) {
                code.invoke(observableClass.requireMethod("getValue"))
                    .dup()
                    .ifnull(
                        () -> code
                            .pop()
                            .defaultconst(valueClass),
                        () -> {
                            code.checkcast(groups[segment].getFirstPathSegment().getValueTypeInstance().declaration());
                            emitInvariants(valueClass, groups[segment].getPath(), code);
                        });
            } else if (groups[segment].getPath().length > 1) {
                code.checkcast(groups[segment].getFirstPathSegment().getValueTypeInstance().declaration());
                emitInvariants(valueClass, groups[segment].getPath(), code);
            }

            code.store(valueClass, valueLocal);
        } else {
            // if (this.observable != null)
            code.aload(0)
                .getfield(requireDeclaredField(OBSERVABLE_FIELD))
                .dup()
                .ifnull(
                    () -> code
                        .pop()
                        .defaultconst(valueClass),
                    () -> {
                        if (dependencyKind != ObservableDependencyKind.CONTENT) {
                            code.invoke(observableClass.requireMethod("getValue"))
                                .dup()
                                .ifnull(
                                    () -> code
                                        .pop()
                                        .defaultconst(valueClass),
                                    () -> {
                                        code.checkcast(groups[segment].getFirstPathSegment().getValueTypeInstance().declaration());
                                        emitInvariants(valueClass, groups[segment].getPath(), code);
                                    });
                        } else if (groups[segment].getPath().length > 1) {
                            code.checkcast(groups[segment].getFirstPathSegment().getValueTypeInstance().declaration());
                            emitInvariants(valueClass, groups[segment].getPath(), code);
                        }
                    })
                .store(valueClass, valueLocal);
        }

        if (valueClass.isPrimitive()) {
            // this.pvalue = valueLocal
            code.aload(0)
                .load(valueClass, valueLocal)
                .putfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD));

            // if (boxValue)
            code.iload(1)
                .ifne(
                    () -> code
                        // this.value = $1
                        .aload(0)
                        .load(valueClass, valueLocal)
                        .box(valueClass)
                        .putfield(requireDeclaredField(VALUE_FIELD))
                        .aload(0)
                        .iconst(1) // 1 = valid, boxed
                        .putfield(requireDeclaredField(FLAGS_FIELD)),
                    () -> code
                        .aload(0)
                        .iconst(2) // 2 = valid, unboxed
                        .putfield(requireDeclaredField(FLAGS_FIELD)));
        } else {
            // this.value = valueLocal
            code.aload(0)
                .aload(valueLocal)
                .putfield(requireDeclaredField(VALUE_FIELD));

            code.aload(0)
                .iconst(1) // 1 = valid, boxed
                .putfield(requireDeclaredField(FLAGS_FIELD));
        }

        code.releaseLocal(valueLocal);
        code.vreturn();

        method.setCode(code);
    }

    private void emitGetBeanMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (!hasInvariants) {
            code.aconst_null()
                .areturn();
        } else if (boxedValueClass.subtypeOf(ReadOnlyPropertyDecl())) {
            code.aload(0)
                .getfield(requireDeclaredField(VALUE_FIELD))
                .dup()
                .ifnonnull(
                    () -> code.invoke(ReadOnlyPropertyDecl().requireDeclaredMethod("getBean")),
                    () -> code.pop().aconst_null())
                .areturn();
        } else {
            code.aload(0)
                .getfield(requireDeclaredField(VALUE_FIELD))
                .dup()
                .isinstanceof(ReadOnlyPropertyDecl())
                .ifne(
                    () -> code
                        .checkcast(ReadOnlyPropertyDecl())
                        .invoke(ReadOnlyPropertyDecl().requireDeclaredMethod("getBean")),
                    () -> code
                        .pop()
                        .aconst_null())
                .areturn();
        }

        method.setCode(code);
    }

    private void emitGetNameMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (!hasInvariants) {
            code.aconst_null()
                .areturn();
        } else if (boxedValueClass.subtypeOf(ReadOnlyPropertyDecl())) {
            code.aload(0)
                .getfield(requireDeclaredField(VALUE_FIELD))
                .dup()
                .ifnonnull(
                    () -> code.invoke(ReadOnlyPropertyDecl().requireDeclaredMethod("getName")),
                    () -> code.pop().aconst_null())
                .areturn();
        } else {
            code.aload(0)
                .getfield(requireDeclaredField(VALUE_FIELD))
                .dup()
                .isinstanceof(ReadOnlyPropertyDecl())
                .ifne(
                    () -> code
                        .checkcast(ReadOnlyPropertyDecl())
                        .invoke(ReadOnlyPropertyDecl().requireDeclaredMethod("getName")),
                    () -> code
                        .pop()
                        .aconst_null())
                .areturn();
        }

        method.setCode(code);
    }
}
