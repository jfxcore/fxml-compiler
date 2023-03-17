// Copyright (c) 2023, JFXcore. All rights reserved.
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
import javafx.beans.value.ObservableListValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import java.util.Arrays;
import java.util.List;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.generate.collections.ListWrapperGenerator.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Implements an {@link ObservableListValue} that wraps a {@link List} contained in an {@link ObservableValue}.
 * <p>
 * The generated class is a thin wrapper, as it simply delegates to the wrapped list.
 * Therefore, modifications of either list will be visible in the other list as well.
 * <p>
 * If the wrapped list implements {@link ObservableList}, modifications will also fire change
 * events on the generated {@code ObservableListValue}.
 * <p>
 * The generated class has a constructor that accepts the {@code ObservableValue};
 * {@code null} is not an acceptable argument value.
 */
public class ListObservableValueWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("ListObservableValueWrapper");

    private static final String OBSERVABLE_FIELD = "observable";
    private static final String WEAK_LIST_CHANGE_LISTENER_FIELD = "weakListChangeListener";
    private static final String VALID_FIELD = "valid";

    private final TypeInstance observableType;

    public ListObservableValueWrapperGenerator() {
        observableType = new Resolver(SourceInfo.none()).getTypeInstance(ObservableListValueType());
    }

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(
            new ListAddRemoveChangeGenerator(),
            new ListSourceAdapterChangeGenerator(),
            new ReferenceTrackerGenerator());
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
        clazz = context.getNestedClasses().create(getClassName());
        clazz.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addInterface(ObservableListValueType());
        clazz.addInterface(ListChangeListenerType());
        clazz.addInterface(InvalidationListenerType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(context.getMarkupClass(), ROOT_REF, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(ObservableValueType(), OBSERVABLE_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(WeakListChangeListenerType(), WEAK_LIST_CHANGE_LISTENER_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME),
                            ADAPTER_CHANGE_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(ObservableListType(), VALUE_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        field = new CtField(booleanType, VALID_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        field = new CtField(InvalidationListenerType(), INVALIDATION_LISTENER_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        field = new CtField(ChangeListenerType(), CHANGE_LISTENER_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        field = new CtField(ListChangeListenerType(), LIST_CHANGE_LISTENER_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        createConstructor(context);
        createListMethods(context);
        createObservableListMethods(context);
        createObservableValueMethods(context);
        createInvalidatedMethod(context);
        createOnChangedMethod(context, clazz);
    }

    private void createListMethods(BytecodeEmitContext context) throws Exception {
        CtClass arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        CtClass delegateType = ListType();

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
        createDelegateMethod(context, booleanType, delegateType, "addAll", intType, CollectionType());
        createDelegateMethod(context, booleanType, delegateType, "removeAll", CollectionType());
        createDelegateMethod(context, booleanType, delegateType, "retainAll", CollectionType());
        createDelegateMethod(context, voidType, delegateType, "clear");
        createDelegateMethod(context, ObjectType(), delegateType, "get", intType);
        createDelegateMethod(context, ObjectType(), delegateType, "set", intType, ObjectType());
        createDelegateMethod(context, ObjectType(), delegateType, "add", intType, ObjectType());
        createDelegateMethod(context, ObjectType(), delegateType, "remove", intType);
        createDelegateMethod(context, intType, delegateType, "indexOf", ObjectType());
        createDelegateMethod(context, intType, delegateType, "lastIndexOf", ObjectType());
        createDelegateMethod(context, ListIteratorType(), delegateType, "listIterator");
        createDelegateMethod(context, ListIteratorType(), delegateType, "listIterator", intType);
        createDelegateMethod(context, ListType(), delegateType, "subList", intType, intType);
    }

    private void createObservableListMethods(BytecodeEmitContext context) throws Exception {
        CtClass arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        CtClass delegateType = ObservableListType();

        createDelegateMethod(context, booleanType, delegateType, "addAll", arrayType);
        createDelegateMethod(context, booleanType, delegateType, "setAll", arrayType);
        createDelegateMethod(context, booleanType, delegateType, "setAll", CollectionType());
        createDelegateMethod(context, booleanType, delegateType, "removeAll", arrayType);
        createDelegateMethod(context, booleanType, delegateType, "retainAll", arrayType);
        createDelegateMethod(context, voidType, delegateType, "remove", intType, intType);
    }

    private void createObservableValueMethods(BytecodeEmitContext context) throws Exception {
        createListenerMethods(context, clazz, INVALIDATION_LISTENER_FIELD, InvalidationListenerType());
        createListenerMethods(context, clazz, CHANGE_LISTENER_FIELD, ChangeListenerType());
        createListenerMethods(context, clazz, LIST_CHANGE_LISTENER_FIELD, ListChangeListenerType());
        createGetMethod(context);
        createGetValueMethod(context);
    }

    private void createGetMethod(BytecodeEmitContext context) throws Exception {
        CtMethod method = new CtMethod(ObjectType(), "get", new CtClass[0], clazz);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(method);
        var ctx = new BytecodeEmitContext(context, clazz, 1, -1);
        Bytecode code = ctx.getOutput();
        Local listLocal = code.acquireLocal(false);

        code.aload(0)
            .getfield(clazz, VALID_FIELD, booleanType)
            .ifeq(() -> {
                // if (this.list != null)
                code.aload(0)
                    .getfield(clazz, VALUE_FIELD, ObservableListType())
                    .ifnonnull(() -> code
                        // this.list.removeListener(weakListChangeListener);
                        .aload(0)
                        .getfield(clazz, VALUE_FIELD, ObservableListType())
                        .aload(0)
                        .getfield(clazz, WEAK_LIST_CHANGE_LISTENER_FIELD, WeakListChangeListenerType())
                        .invokeinterface(ObservableListType(), "removeListener",
                                         function(voidType, ListChangeListenerType()))
                    );

                // valid = true;
                // List list = source.getValue();
                code.aload(0)
                    .iconst(1)
                    .putfield(clazz, VALID_FIELD, booleanType)
                    .aload(0)
                    .getfield(clazz, OBSERVABLE_FIELD, ObservableValueType())
                    .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                    .astore(listLocal);

                // if (list instanceof ObservableList)
                code.aload(listLocal)
                    .isinstanceof(ObservableListType())
                    .ifne(() -> code
                        // this.list = (ObservableList)list;
                        // this.list.addListener(weakListChangeListener);
                        .aload(0)
                        .aload(listLocal)
                        .checkcast(ObservableListType())
                        .putfield(clazz, VALUE_FIELD, ObservableListType())
                        .aload(0)
                        .getfield(clazz, VALUE_FIELD, ObservableListType())
                        .aload(0)
                        .getfield(clazz, WEAK_LIST_CHANGE_LISTENER_FIELD, WeakListChangeListenerType())
                        .invokeinterface(ObservableListType(), "addListener",
                                         function(voidType, ListChangeListenerType())),
                    /*else*/ () -> code
                        // else if (list != null)
                        .aload(listLocal)
                        .ifnonnull(() -> code
                            // this.list = FXCollections.observableList(list);
                            .aload(0)
                            .aload(listLocal)
                            .invokestatic(FXCollectionsType(), "observableList",
                                          function(ObservableListType(), ListType()))
                            .putfield(clazz, VALUE_FIELD, ObservableListType()),
                        /*else*/ () -> code
                            // else this.list = null;
                            .aload(0)
                            .aconst_null()
                            .putfield(clazz, VALUE_FIELD, ObservableListType())
                        )
                    );
            })
            .aload(0)
            .getfield(clazz, VALUE_FIELD, ObservableListType())
            .areturn()
            .releaseLocal(listLocal);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    private void createGetValueMethod(BytecodeEmitContext context) throws Exception {
        CtMethod method = new CtMethod(ObjectType(), "getValue", new CtClass[0], clazz);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(method);

        var ctx = new BytecodeEmitContext(context, clazz, 1, -1);
        ctx.getOutput()
            .aload(0)
            .invokeinterface(ObservableObjectValueType(), "get", function(ObjectType()))
            .areturn();

        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    private void createInvalidatedMethod(BytecodeEmitContext context) throws Exception {
        CtMethod method = new CtMethod(voidType, "invalidated", new CtClass[]{ObservableType()}, clazz);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(method);
        var ctx = new BytecodeEmitContext(context, clazz, 2, -1);
        Bytecode code = ctx.getOutput();
        Local oldValueLocal = code.acquireLocal(false);
        Local currentValueLocal = code.acquireLocal(false);
        Local safeSizeLocal = code.acquireLocal(false);
        Local safeOldValueLocal = code.acquireLocal(false);

        // markupRef.clearStaleReferences();
        code.aload(0)
            .getfield(clazz, ROOT_REF, context.getMarkupClass())
            .invokevirtual(context.getMarkupClass(), ReferenceTrackerGenerator.CLEAR_STALE_REFERENCES_METHOD,
                           function(voidType));

        // this.valid = false;
        code.aload(0)
            .iconst(0)
            .putfield(clazz, VALID_FIELD, booleanType);

        // if (invalidationListener != null)
        code.aload(0)
            .getfield(clazz, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
            .ifnonnull(() -> code
                // invalidationListener.invalidated(this);
                .aload(0)
                .getfield(clazz, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
                .aload(0)
                .invokeinterface(InvalidationListenerType(), "invalidated",
                                 function(voidType, ObservableType()))
            );

        // if (changeListener != null)
        code.aload(0)
            .getfield(clazz, CHANGE_LISTENER_FIELD, ChangeListenerType())
            .ifnonnull(() -> code
                // ObservableList oldValue = this.list;
                // ObservableList currentValue = this.get();
                .aload(0)
                .getfield(clazz, VALUE_FIELD, ObservableListType())
                .astore(oldValueLocal)
                .aload(0)
                .invokeinterface(ObservableObjectValueType(), "get", function(ObjectType()))
                .checkcast(ObservableListType())
                .astore(currentValueLocal)
                // if (oldValue != currentValue) {
                .aload(oldValueLocal)
                .aload(currentValueLocal)
                .if_acmpne(() -> code
                    // changeListener.changed(this, oldValue, currentValue);
                    .aload(0)
                    .getfield(clazz, CHANGE_LISTENER_FIELD, ChangeListenerType())
                    .aload(0)
                    .aload(oldValueLocal)
                    .aload(currentValueLocal)
                    .invokeinterface(ChangeListenerType(), "changed",
                                     function(voidType, ObservableType(), ObjectType(), ObjectType()))
                )
            );

        // if (listChangeListener != null)
        code.aload(0)
            .getfield(clazz, LIST_CHANGE_LISTENER_FIELD, ListChangeListenerType())
            .ifnonnull(() -> code
                // ObservableList oldValue = this.list;
                // ObservableList currentValue = this.get();
                .aload(0)
                .getfield(clazz, VALUE_FIELD, ObservableListType())
                .astore(oldValueLocal)
                .aload(0)
                .invokeinterface(ObservableObjectValueType(), "get", function(ObjectType()))
                .checkcast(ObservableListType())
                .astore(currentValueLocal)
                // if (oldValue != currentValue)
                .aload(oldValueLocal)
                .aload(currentValueLocal)
                .if_acmpne(() -> {
                    // int safeSize = currentValue != null ? currentValue.size() : 0;
                    code.aload(currentValueLocal)
                        .ifnull(() -> code
                            .iconst(0)
                            .istore(safeSizeLocal),
                        /*else*/ () -> code
                            .aload(currentValueLocal)
                            .invokeinterface(ListType(), "size", function(intType))
                            .istore(safeSizeLocal)
                        );

                    // if (oldValue == null)
                    code.aload(oldValueLocal)
                        .ifnull(() -> code
                            // safeOldValue = FXCollections.emptyObservableList();
                            .invokestatic(FXCollectionsType(), "emptyObservableList",
                                          function(ObservableListType()))
                            .astore(safeOldValueLocal),
                        /*else*/ () -> code
                            // safeOldValue = FXCollections.unmodifiableObservableList(oldValue);
                            .aload(oldValueLocal)
                            .invokestatic(FXCollectionsType(), "unmodifiableObservableList",
                                          function(ObservableListType(), ObservableListType()))
                            .astore(safeOldValueLocal)
                        );

                    CtClass changeType = context.getNestedClasses().find(ListAddRemoveChangeGenerator.CLASS_NAME);

                    // listChangeListener.onChanged(new AddRemoveChange(0, safeSize, safeOldValue, this));
                    code.aload(0)
                        .getfield(clazz, LIST_CHANGE_LISTENER_FIELD, ListChangeListenerType())
                        .anew(changeType)
                        .dup()
                        .iconst(0)
                        .iload(safeSizeLocal)
                        .aload(safeOldValueLocal)
                        .aload(0)
                        .invokespecial(changeType, MethodInfo.nameInit,
                                       constructor(intType, intType, ListType(), ObservableListType()))
                        .invokeinterface(ListChangeListenerType(), "onChanged",
                                         function(voidType, ListChangeListenerChangeType()));
                })
            );

        code.releaseLocal(oldValueLocal);
        code.releaseLocal(currentValueLocal);
        code.releaseLocal(safeSizeLocal);
        code.releaseLocal(safeOldValueLocal);
        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    private void createConstructor(BytecodeEmitContext context) throws Exception {
        CtConstructor constructor = new CtConstructor(
                new CtClass[]{context.getMarkupClass(), ObservableValueType()}, clazz);
        constructor.setModifiers(Modifier.PUBLIC);
        clazz.addConstructor(constructor);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, clazz, 3, -1);
        CtClass adapterChangeType = context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME);
        Bytecode code = ctx.getOutput();

        // super()
        code.aload(0)
            .invokespecial(clazz.getSuperclass(), MethodInfo.nameInit, constructor());

        // markupRef = $1
        code.aload(0)
            .aload(1)
            .putfield(clazz, ROOT_REF, context.getMarkupClass());

        // observable = $2
        code.aload(0)
            .aload(2)
            .putfield(clazz, OBSERVABLE_FIELD, ObservableValueType());

        // weakListChangeListener = new WeakListChangeListener(this);
        code.aload(0)
            .anew(WeakListChangeListenerType())
            .dup()
            .aload(0)
            .invokespecial(WeakListChangeListenerType(), MethodInfo.nameInit,
                           constructor(ListChangeListenerType()))
            .putfield(clazz, WEAK_LIST_CHANGE_LISTENER_FIELD, WeakListChangeListenerType());

        code.aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invokespecial(adapterChangeType, MethodInfo.nameInit, constructor(ObservableListType()))
            .putfield(clazz, ADAPTER_CHANGE_FIELD, adapterChangeType);

        // $1.addListener(new WeakInvalidationListener(this));
        code.aload(2)
            .anew(WeakInvalidationListenerType())
            .dup()
            .aload(0)
            .invokespecial(WeakInvalidationListenerType(), MethodInfo.nameInit,
                           constructor(InvalidationListenerType()))
            .invokeinterface(ObservableType(), "addListener", function(voidType, InvalidationListenerType()))
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    /**
     * Creates a method that delegates the method call to an ObservableList that is wrapped by the current object.
     * The implementation is similar to:
     * <pre>{@code
     *     public boolean add(E element) {
     *         ObservableList<E> list = get();
     *         return list == null ? Collections.emptyList().add(element) : list.add(element);
     *     }
     * }</pre>
     */
    private void createDelegateMethod(BytecodeEmitContext context, CtClass retType, CtClass delegateType,
                                      String methodName, CtClass... params) throws Exception {
        boolean delegatesToObservableList = delegateType.subtypeOf(ObservableListType());

        createBehavior(
            context, clazz, new CtMethod(retType, methodName, params, clazz),
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
                    .checkcast(ObservableListType())
                    .astore(listLocal)
                    .aload(listLocal)
                    .ifnull(
                        () -> {
                            if (delegatesToObservableList) {
                                code.invokestatic(FXCollectionsType(), "emptyObservableList", function(ListType()));
                            } else {
                                code.invokestatic(CollectionsType(), "emptyList", function(ListType()));
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
