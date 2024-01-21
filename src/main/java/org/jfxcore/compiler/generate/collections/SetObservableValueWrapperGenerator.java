// Copyright (c) 2023, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.ReferenceTrackerGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import javafx.beans.value.ObservableSetValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.generate.collections.SetWrapperGenerator.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Implements an {@link ObservableSetValue} that wraps a {@link Set} contained in an {@link ObservableValue}.
 * <p>
 * The generated class is a thin wrapper, as it simply delegates to the wrapped set.
 * Therefore, modifications of either set will be visible in the other set as well.
 * <p>
 * If the wrapped set implements {@link ObservableSet}, modifications will also fire change
 * events on the generated {@code ObservableSetValue}.
 * <p>
 * The generated class has a constructor that accepts the {@code ObservableValue};
 * {@code null} is not an acceptable argument value.
 */
public class SetObservableValueWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("SetObservableValueWrapper");

    private static final String OBSERVABLE_FIELD = "observable";
    private static final String WEAK_SET_CHANGE_LISTENER_FIELD = "weakSetChangeListener";
    private static final String VALID_FIELD = "valid";

    private final TypeInstance observableType;

    public SetObservableValueWrapperGenerator() {
        observableType = new Resolver(SourceInfo.none()).getTypeInstance(ObservableSetValueType());
    }

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(
            new SetAddRemoveChangeGenerator(),
            new SetSourceAdapterChangeGenerator());
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return observableType;
    }

    @Override
    public final void emitClass(BytecodeEmitContext context) throws Exception {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addInterface(ObservableSetValueType());
        generatedClass.addInterface(SetChangeListenerType());
        generatedClass.addInterface(InvalidationListenerType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(context.getMarkupClass(), ROOT_REF, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(ObservableValueType(), OBSERVABLE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(WeakSetChangeListenerType(), WEAK_SET_CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(context.getNestedClasses().find(SetSourceAdapterChangeGenerator.CLASS_NAME),
                            ADAPTER_CHANGE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(ObservableSetType(), VALUE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(booleanType, VALID_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(InvalidationListenerType(), INVALIDATION_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(ChangeListenerType(), CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(SetChangeListenerType(), SET_CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        createConstructor(context);
        createSetMethods(context);
        createGetValueMethod(context);
        createGetMethod(context);
        createInvalidatedMethod(context);
        createOnChangedMethod(context, generatedClass);
        createListenerMethods(context, generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType());
        createListenerMethods(context, generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType());
        createListenerMethods(context, generatedClass, SET_CHANGE_LISTENER_FIELD, SetChangeListenerType());
    }

    private void createSetMethods(BytecodeEmitContext context) throws Exception {
        CtClass arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        CtClass delegateType = SetType();

        createDelegateMethod(context, intType, delegateType, "size");
        createDelegateMethod(context, booleanType, delegateType, "isEmpty");
        createDelegateMethod(context, booleanType, delegateType, "contains", ObjectType());
        createDelegateMethod(context, IteratorType(), delegateType, "iterator");
        createDelegateMethod(context, arrayType, delegateType, "toArray");
        createDelegateMethod(context, arrayType, delegateType, "toArray", arrayType);
        createDelegateMethod(context, booleanType, delegateType, "add", ObjectType());
        createDelegateMethod(context, booleanType, delegateType, "remove", ObjectType());
        createDelegateMethod(context, booleanType, delegateType, "containsAll", CollectionType());
        createDelegateMethod(context, booleanType, delegateType, "addAll", CollectionType());
        createDelegateMethod(context, booleanType, delegateType, "removeAll", CollectionType());
        createDelegateMethod(context, booleanType, delegateType, "retainAll", CollectionType());
        createDelegateMethod(context, voidType, delegateType, "clear");
    }

    private void createGetValueMethod(BytecodeEmitContext context) throws Exception {
        CtMethod method = new CtMethod(ObjectType(), "getValue", new CtClass[0], generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        var ctx = new BytecodeEmitContext(context, generatedClass, 1, -1);
        ctx.getOutput()
            .aload(0)
            .invokeinterface(ObservableObjectValueType(), "get", function(ObjectType()))
            .areturn();
        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void createGetMethod(BytecodeEmitContext context) throws Exception {
        CtMethod method = new CtMethod(ObjectType(), "get", new CtClass[0], generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        var ctx = new BytecodeEmitContext(context, generatedClass, 1, -1);
        Bytecode code = ctx.getOutput();
        Local listLocal = code.acquireLocal(false);

        code.aload(0)
            .getfield(generatedClass, VALID_FIELD, booleanType)
            .ifeq(() -> {
                // if (this.set != null)
                code.aload(0)
                    .getfield(generatedClass, VALUE_FIELD, ObservableSetType())
                    .ifnonnull(() -> {
                        // this.set.removeListener(weakSetChangeListener);
                        code.aload(0)
                            .getfield(generatedClass, VALUE_FIELD, ObservableSetType())
                            .aload(0)
                            .getfield(generatedClass, WEAK_SET_CHANGE_LISTENER_FIELD, WeakSetChangeListenerType())
                            .invokeinterface(ObservableSetType(), "removeListener",
                                             function(voidType, SetChangeListenerType()));
                    });

                // valid = true;
                // Set set = source.getValue();
                code.aload(0)
                    .iconst(1)
                    .putfield(generatedClass, VALID_FIELD, booleanType)
                    .aload(0)
                    .getfield(generatedClass, OBSERVABLE_FIELD, ObservableValueType())
                    .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                    .astore(listLocal);

                // if (set instanceof ObservableSet)
                code.aload(listLocal)
                    .isinstanceof(ObservableSetType())
                    .ifne(() -> code
                        // this.set = (ObservableSet)set;
                        // this.set.addListener(weakSetChangeListener);
                        .aload(0)
                        .aload(listLocal)
                        .checkcast(ObservableSetType())
                        .putfield(generatedClass, VALUE_FIELD, ObservableSetType())
                        .aload(0)
                        .getfield(generatedClass, VALUE_FIELD, ObservableSetType())
                        .aload(0)
                        .getfield(generatedClass, WEAK_SET_CHANGE_LISTENER_FIELD, WeakSetChangeListenerType())
                        .invokeinterface(ObservableSetType(), "addListener",
                                         function(voidType, SetChangeListenerType())),
                    /*else*/ () -> code
                        // else if (set != null)
                        .aload(listLocal)
                        .ifnonnull(() -> code
                            // this.set = FXCollections.observableSet(set);
                            .aload(0)
                            .aload(listLocal)
                            .invokestatic(FXCollectionsType(), "observableSet",
                                          function(ObservableSetType(), SetType()))
                            .putfield(generatedClass, VALUE_FIELD, ObservableSetType()),
                        /*else*/ () -> code
                            // else this.set = null;
                            .aload(0)
                            .aconst_null()
                            .putfield(generatedClass, VALUE_FIELD, ObservableSetType())
                        )
                    );
            })
            .aload(0)
            .getfield(generatedClass, VALUE_FIELD, ObservableSetType())
            .areturn()
            .releaseLocal(listLocal);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    /**
     * The {@code invalidated} method is called when the value of the ObservableValue changes,
     * i.e. when the set wrapped by the ObservableValue is replaced entirely.
     */
    private void createInvalidatedMethod(BytecodeEmitContext context) throws Exception {
        CtMethod method = new CtMethod(voidType, "invalidated", new CtClass[]{ObservableType()}, generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        var ctx = new BytecodeEmitContext(context, generatedClass, 2, -1);
        Bytecode code = ctx.getOutput();
        Local oldValueLocal = code.acquireLocal(false);
        Local currentValueLocal = code.acquireLocal(false);
        Local iteratorLocal = code.acquireLocal(false);
        Local elementLocal = code.acquireLocal(false);
        Local addRemoveChangeLocal = code.acquireLocal(false);
        CtClass addRemoveChangeType = context.getNestedClasses().find(SetAddRemoveChangeGenerator.CLASS_NAME);

        if (context.isGeneratorActive(ReferenceTrackerGenerator.class)) {
            // markupRef.clearStaleReferences();
            code.aload(0)
                .getfield(generatedClass, ROOT_REF, context.getMarkupClass())
                .invokevirtual(context.getMarkupClass(), ReferenceTrackerGenerator.CLEAR_STALE_REFERENCES_METHOD,
                               function(voidType));
        }

        // this.valid = false;
        code.aload(0)
            .iconst(0)
            .putfield(generatedClass, VALID_FIELD, booleanType);

        // if (invalidationListener != null)
        code.aload(0)
            .getfield(generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
            .ifnonnull(() -> code
                // invalidationListener.invalidated(this);
                .aload(0)
                .getfield(generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
                .aload(0)
                .invokeinterface(InvalidationListenerType(), "invalidated",
                                 function(voidType, ObservableType()))
            );

        // if (changeListener != null || setChangeListener != null)
        code.aload(0)
            .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
            .ifnonnull(
                () -> code.iconst(1),
                () -> code
                    .aload(0)
                    .getfield(generatedClass, SET_CHANGE_LISTENER_FIELD, SetChangeListenerType())
                    .ifnonnull(
                        () -> code.iconst(1),
                        () -> code.iconst(0)))
            .ifne(() -> code
                // ObservableSet oldValue = this.value;
                // ObservableSet currentValue = this.get();
                .aload(0)
                .getfield(generatedClass, VALUE_FIELD, ObservableSetType())
                .astore(oldValueLocal)
                .aload(0)
                .invokeinterface(ObservableObjectValueType(), "get", function(ObjectType()))
                .checkcast(ObservableSetType())
                .astore(currentValueLocal)
                // if (oldValue != currentValue) {
                .aload(oldValueLocal)
                .aload(currentValueLocal)
                .if_acmpne(() -> code
                    // if (changeListener != null)
                    .aload(0)
                    .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                    .ifnonnull(() -> code
                        // changeListener.changed(this, oldValue, currentValue);
                        .aload(0)
                        .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                        .aload(0)
                        .aload(oldValueLocal)
                        .aload(currentValueLocal)
                        .invokeinterface(ChangeListenerType(), "changed",
                                         function(voidType, ObservableValueType(), ObjectType(), ObjectType())))
                    // if (setChangeListener != null)
                    .aload(0)
                    .getfield(generatedClass, SET_CHANGE_LISTENER_FIELD, SetChangeListenerType())
                    .ifnonnull(() -> code
                        // var change = new SetAddRemoveChange(this)
                        .anew(addRemoveChangeType)
                        .dup()
                        .aload(0)
                        .invokespecial(addRemoveChangeType, MethodInfo.nameInit, constructor(ObservableSetType()))
                        .astore(addRemoveChangeLocal)
                        // if (currentValue == null)
                        .aload(currentValueLocal)
                        .ifnull(() -> {
                            // for (E removed : oldValue) {
                            //     addRemoveChange.initChange(removed, false);
                            //     setChangeListener.onChanged(addRemoveChange);
                            // }
                            code.aload(oldValueLocal)
                                .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                .astore(iteratorLocal);

                            int position = code.position() + 1;

                            code.aload(iteratorLocal)
                                .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                .ifne(() -> code
                                    .aload(addRemoveChangeLocal)
                                    .aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                    .iconst(0)
                                    .invokevirtual(addRemoveChangeType, SetAddRemoveChangeGenerator.INIT_METHOD_NAME,
                                                   function(voidType, ObjectType(), booleanType))
                                    .aload(0)
                                    .getfield(generatedClass, SET_CHANGE_LISTENER_FIELD, SetChangeListenerType())
                                    .aload(addRemoveChangeLocal)
                                    .invokeinterface(SetChangeListenerType(), "onChanged",
                                                     function(voidType, SetChangeListenerChangeType()))
                                    .goto_position(position)
                                );
                            },
                        /*else*/ () -> code
                            // else if (oldValue == null)
                            .aload(oldValueLocal)
                            .ifnull(() -> {
                                // for (E added : currentValue) {
                                //     addRemoveChange.initChange(removed, true);
                                //     setChangeListener.onChanged(addRemoveChange);
                                // }
                                code.aload(currentValueLocal)
                                    .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                    .astore(iteratorLocal);

                                int position = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                    .ifne(() -> code
                                        .aload(addRemoveChangeLocal)
                                        .aload(iteratorLocal)
                                        .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                        .iconst(1)
                                        .invokevirtual(addRemoveChangeType, SetAddRemoveChangeGenerator.INIT_METHOD_NAME,
                                                       function(voidType, ObjectType(), booleanType))
                                        .aload(0)
                                        .getfield(generatedClass, SET_CHANGE_LISTENER_FIELD, SetChangeListenerType())
                                        .aload(addRemoveChangeLocal)
                                        .invokeinterface(SetChangeListenerType(), "onChanged",
                                                         function(voidType, SetChangeListenerChangeType()))
                                        .goto_position(position)
                                    );
                                },
                            /*else*/ () -> {
                                // for (E element : oldValue) {
                                //     if (!currentValue.contains(element)) {
                                //         addRemoveChange.initChange(element, false);
                                //         setChangeListener.onChanged(addRemoveChange);
                                //     }
                                // }
                                code.aload(oldValueLocal)
                                    .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                    .astore(iteratorLocal);

                                int position1 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                        .astore(elementLocal)
                                        .aload(currentValueLocal)
                                        .aload(elementLocal)
                                        .invokeinterface(SetType(), "contains",
                                                         function(booleanType, ObjectType()))
                                        .ifeq(() -> code
                                            .aload(addRemoveChangeLocal)
                                            .aload(elementLocal)
                                            .iconst(0)
                                            .invokevirtual(addRemoveChangeType, SetAddRemoveChangeGenerator.INIT_METHOD_NAME,
                                                           function(voidType, ObjectType(), booleanType))
                                            .aload(0)
                                            .getfield(generatedClass, SET_CHANGE_LISTENER_FIELD, SetChangeListenerType())
                                            .aload(addRemoveChangeLocal)
                                            .invokeinterface(SetChangeListenerType(), "onChanged",
                                                             function(voidType, SetChangeListenerChangeType()))
                                        )
                                        .goto_position(position1)
                                    );

                                // for (E element : currentValue) {
                                //     if (!oldValue.contains(element)) {
                                //         addRemoveChange.initChange(element, true);
                                //         setChangeListener.onChanged(addRemoveChange);
                                //     }
                                // }
                                code.aload(currentValueLocal)
                                    .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                    .astore(iteratorLocal);

                                int position2 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                        .astore(elementLocal)
                                        .aload(oldValueLocal)
                                        .aload(elementLocal)
                                        .invokeinterface(SetType(), "contains",
                                                         function(booleanType, ObjectType()))
                                        .ifeq(() -> code
                                            .aload(addRemoveChangeLocal)
                                            .aload(elementLocal)
                                            .iconst(1)
                                            .invokevirtual(addRemoveChangeType, SetAddRemoveChangeGenerator.INIT_METHOD_NAME,
                                                           function(voidType, ObjectType(), booleanType))
                                            .aload(0)
                                            .getfield(generatedClass, SET_CHANGE_LISTENER_FIELD, SetChangeListenerType())
                                            .aload(addRemoveChangeLocal)
                                            .invokeinterface(SetChangeListenerType(), "onChanged",
                                                             function(voidType, SetChangeListenerChangeType()))
                                        )
                                        .goto_position(position2)
                                    );
                            }))
                )));

        code.releaseLocal(elementLocal);
        code.releaseLocal(iteratorLocal);
        code.releaseLocal(oldValueLocal);
        code.releaseLocal(currentValueLocal);
        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void createConstructor(BytecodeEmitContext context) throws Exception {
        CtConstructor constructor = new CtConstructor(
                new CtClass[]{context.getMarkupClass(), ObservableValueType()}, generatedClass);
        constructor.setModifiers(Modifier.PUBLIC);
        generatedClass.addConstructor(constructor);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, generatedClass, 3, -1);
        CtClass adapterChangeType = context.getNestedClasses().find(SetSourceAdapterChangeGenerator.CLASS_NAME);
        Bytecode code = ctx.getOutput();

        // super()
        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor());

        // markupRef = $1
        code.aload(0)
            .aload(1)
            .putfield(generatedClass, ROOT_REF, context.getMarkupClass());

        // observable = $2
        code.aload(0)
            .aload(2)
            .putfield(generatedClass, OBSERVABLE_FIELD, ObservableValueType());

        // weakSetChangeListener = new WeakSetChangeListener(this);
        code.aload(0)
            .anew(WeakSetChangeListenerType())
            .dup()
            .aload(0)
            .invokespecial(WeakSetChangeListenerType(), MethodInfo.nameInit,
                           constructor(SetChangeListenerType()))
            .putfield(generatedClass, WEAK_SET_CHANGE_LISTENER_FIELD, WeakSetChangeListenerType());

        // setAdapterChange = new SetAdapterChange(this);
        code.aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invokespecial(adapterChangeType, MethodInfo.nameInit, constructor(ObservableSetType()))
            .putfield(generatedClass, ADAPTER_CHANGE_FIELD, adapterChangeType);

        // $1.addListener(new WeakInvalidationListener(this));
        code.aload(2)
            .anew(WeakInvalidationListenerType())
            .dup()
            .aload(0)
            .invokespecial(WeakInvalidationListenerType(), MethodInfo.nameInit,
                           constructor(InvalidationListenerType()))
            .invokeinterface(ObservableType(), "addListener",
                             function(voidType, InvalidationListenerType()));

        code.aload(0)
            .invokeinterface(ObservableSetValueType(), "get", function(ObjectType()))
            .pop()
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    /**
     * Creates a method that delegates the method call to an ObservableSet that is wrapped by the current object.
     * The implementation is similar to:
     * <pre>{@code
     *     public boolean add(E element) {
     *         ObservableSet<E> set = get();
     *         return set == null ? Collections.emptyList().add(element) : set.add(element);
     *     }
     * }</pre>
     */
    private void createDelegateMethod(BytecodeEmitContext context, CtClass retType, CtClass delegateType,
                                      String methodName, CtClass... params) throws Exception {
        boolean delegatesToObservableSet = delegateType.subtypeOf(ObservableSetType());

        createBehavior(
            context, generatedClass, new CtMethod(retType, methodName, params, generatedClass),
            Arrays.stream(params).mapToInt(TypeHelper::getSlots).sum() + 1,
            code -> {
                Local listLocal = code.acquireLocal(false);
                Local retLocal = code.acquireLocal(retType);

                Runnable branch = () -> {
                    int slot = 1;
                    for (CtClass param : params) {
                        code.ext_load(param, slot);
                        slot += TypeHelper.getSlots(param);
                    }

                    code.invokeinterface(delegateType, methodName, function(retType, params));

                    if (retType != CtClass.voidType) {
                        code.ext_store(retType, retLocal);
                    }
                };

                code.aload(0)
                    .invokeinterface(ObservableObjectValueType(), "get", function(ObjectType()))
                    .checkcast(ObservableSetType())
                    .astore(listLocal)
                    .aload(listLocal)
                    .ifnull(
                        () -> {
                            if (delegatesToObservableSet) {
                                code.invokestatic(FXCollectionsType(), "emptyObservableSet", function(SetType()));
                            } else {
                                code.invokestatic(CollectionsType(), "emptySet", function(SetType()));
                            }
                            branch.run();
                        },
                        () -> {
                            code.aload(listLocal);
                            branch.run();
                        });

                if (retType == CtClass.voidType) {
                    code.vreturn();
                } else {
                    code.ext_load(retType, retLocal);

                    if (retType == CtClass.longType) {
                        code.lreturn();
                    } else if (retType == CtClass.booleanType || TypeHelper.isIntegralPrimitive(retType)) {
                        code.ireturn();
                    } else if (retType == CtClass.doubleType) {
                        code.dreturn();
                    } else if (retType == CtClass.floatType) {
                        code.freturn();
                    } else {
                        code.areturn();
                    }
                }

                code.releaseLocal(listLocal);
                code.releaseLocal(retLocal);
            }
        );
    }

}
