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
import org.jfxcore.compiler.util.TypeInstance;
import javafx.beans.value.ObservableListValue;
import javafx.collections.ObservableList;
import java.util.List;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Implements an {@link ObservableListValue} that wraps a {@link List}.
 * <p>
 * The generated class is a thin wrapper, as it simply delegates to the wrapped list.
 * Therefore, modifications of either list will be visible in the other list as well.
 * <p>
 * If the wrapped list implements {@link ObservableList}, modifications will also fire change
 * events on the generated {@code ObservableListValue}.
 * <p>
 * The generated class has a constructor that accepts the wrapped list, or {@code null}.
 * If {@code null} is passed to the constructor, the generated class represents an empty list.
 */
public class ListWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("ListWrapper");

    static final String ROOT_REF = "root";
    static final String VALUE_FIELD = "value";
    static final String ADAPTER_CHANGE_FIELD = "change";
    static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    static final String CHANGE_LISTENER_FIELD = "changeListener";
    static final String LIST_CHANGE_LISTENER_FIELD = "listChangeListener";

    private final TypeInstance observableType;

    public ListWrapperGenerator() {
        observableType = new Resolver(SourceInfo.none()).getTypeInstance(ObservableListValueType());
    }

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(new ListSourceAdapterChangeGenerator());
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
        generatedClass.addInterface(ObservableListValueType());
        generatedClass.addInterface(ListChangeListenerType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(context.getMarkupClass(), ROOT_REF, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(ObservableListType(), VALUE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME),
                            ADAPTER_CHANGE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(InvalidationListenerType(), INVALIDATION_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(ChangeListenerType(), CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(ListChangeListenerType(), LIST_CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        createConstructor(context);
        createListMethods(context);
        createObservableListMethods(context);
        createObservableValueMethods(context);
        createOnChangedMethod(context, generatedClass);
        createListenerMethods(context, generatedClass, LIST_CHANGE_LISTENER_FIELD, ListChangeListenerType());
    }

    private void createListMethods(BytecodeEmitContext context) throws Exception {
        CtClass arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        CtClass fieldType = ObservableListType();

        createFieldDelegateMethod(context, generatedClass, intType, VALUE_FIELD, fieldType, "size");
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "isEmpty");
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "contains", ObjectType());
        createFieldDelegateMethod(context, generatedClass, IteratorType(), VALUE_FIELD, fieldType, "iterator");
        createFieldDelegateMethod(context, generatedClass, arrayType, VALUE_FIELD, fieldType, "toArray");
        createFieldDelegateMethod(context, generatedClass, arrayType, VALUE_FIELD, fieldType, "toArray", arrayType);
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "add", ObjectType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "remove", ObjectType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "containsAll", CollectionType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "addAll", CollectionType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "addAll", intType, CollectionType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "removeAll", CollectionType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "retainAll", CollectionType());
        createFieldDelegateMethod(context, generatedClass, voidType, VALUE_FIELD, fieldType, "clear");
        createFieldDelegateMethod(context, generatedClass, ObjectType(), VALUE_FIELD, fieldType, "get", intType);
        createFieldDelegateMethod(context, generatedClass, ObjectType(), VALUE_FIELD, fieldType, "set", intType, ObjectType());
        createFieldDelegateMethod(context, generatedClass, ObjectType(), VALUE_FIELD, fieldType, "add", intType, ObjectType());
        createFieldDelegateMethod(context, generatedClass, ObjectType(), VALUE_FIELD, fieldType, "remove", intType);
        createFieldDelegateMethod(context, generatedClass, intType, VALUE_FIELD, fieldType, "indexOf", ObjectType());
        createFieldDelegateMethod(context, generatedClass, intType, VALUE_FIELD, fieldType, "lastIndexOf", ObjectType());
        createFieldDelegateMethod(context, generatedClass, ListIteratorType(), VALUE_FIELD, fieldType, "listIterator");
        createFieldDelegateMethod(context, generatedClass, ListIteratorType(), VALUE_FIELD, fieldType, "listIterator", intType);
        createFieldDelegateMethod(context, generatedClass, ListType(), VALUE_FIELD, fieldType, "subList", intType, intType);
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "equals", ObjectType());
        createFieldDelegateMethod(context, generatedClass, intType, VALUE_FIELD, fieldType, "hashCode");
        createFieldDelegateMethod(context, generatedClass, intType, VALUE_FIELD, fieldType, "toString");
    }

    private void createObservableListMethods(BytecodeEmitContext context) throws Exception {
        CtClass arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        CtClass fieldType = ObservableListType();

        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "addAll", arrayType);
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "setAll", arrayType);
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "setAll", CollectionType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "removeAll", arrayType);
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "retainAll", arrayType);
        createFieldDelegateMethod(context, generatedClass, voidType, VALUE_FIELD, fieldType, "remove", intType, intType);
    }

    private void createObservableValueMethods(BytecodeEmitContext context) throws Exception {
        createListenerMethods(context, generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType());
        createListenerMethods(context, generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType());

        CtMethod method = new CtMethod(ObjectType(), "get", new CtClass[0], generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        var ctx = new BytecodeEmitContext(context, generatedClass, 1, -1);
        ctx.getOutput().aload(0).getfield(generatedClass, VALUE_FIELD, ObservableListType()).areturn();
        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());

        method = new CtMethod(ObjectType(), "getValue", new CtClass[0], generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        ctx = new BytecodeEmitContext(context, generatedClass, 1, -1);
        ctx.getOutput().aload(0).getfield(generatedClass, VALUE_FIELD, ObservableListType()).areturn();
        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void createConstructor(BytecodeEmitContext context) throws Exception {
        CtConstructor constructor = new CtConstructor(new CtClass[]{context.getMarkupClass(), ListType()}, generatedClass);
        constructor.setModifiers(Modifier.PUBLIC);
        generatedClass.addConstructor(constructor);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, generatedClass, 3, -1);
        Bytecode code = ctx.getOutput();
        Local valueLocal = code.acquireLocal(false);
        CtClass adapterChangeType = context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME);

        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor())
            .aload(0)
            .aload(1)
            .putfield(generatedClass, ROOT_REF, context.getMarkupClass())
            .aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invokespecial(adapterChangeType, MethodInfo.nameInit, constructor(ObservableListType()))
            .putfield(generatedClass, ADAPTER_CHANGE_FIELD, adapterChangeType)
            .aconst_null()
            .astore(valueLocal)
            .aload(2)
            .isinstanceof(ObservableListType())
            .ifne(() -> code
                .aload(2)
                .checkcast(ObservableListType())
                .astore(valueLocal),
            /*else*/ () -> code
                .aload(2)
                .ifnonnull(() -> {
                    code.aload(2)
                        .invokestatic(FXCollectionsType(), "observableList",
                                      function(ObservableListType(), ListType()))
                        .astore(valueLocal);
                })
            )
            .aload(valueLocal)
            .ifnonnull(() -> code
                .aload(valueLocal)
                .anew(WeakListChangeListenerType())
                .dup()
                .aload(0)
                .invokespecial(WeakListChangeListenerType(), MethodInfo.nameInit,
                               constructor(ListChangeListenerType()))
                .invokeinterface(ObservableListType(), "addListener",
                                 function(voidType, ListChangeListenerType())),
            /*else*/ () -> code
                .invokestatic(FXCollectionsType(), "emptyObservableList",
                              function(ObservableListType()))
                .astore(valueLocal)
            )
            .aload(0)
            .aload(valueLocal)
            .putfield(generatedClass, VALUE_FIELD, ObservableListType())
            .vreturn()
            .releaseLocal(valueLocal);

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    static void createOnChangedMethod(BytecodeEmitContext context, CtClass generatedClass) throws Exception {
        CtMethod method = new CtMethod(
            voidType, "onChanged", new CtClass[] {ListChangeListenerChangeType()}, generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, generatedClass, 2, -1);
        Bytecode code = ctx.getOutput();
        CtClass adapterChangeType = context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME);

        if (context.isGeneratorActive(ReferenceTrackerGenerator.class)) {
            code.aload(0)
                .getfield(generatedClass, ROOT_REF, context.getMarkupClass())
                .invokevirtual(context.getMarkupClass(), ReferenceTrackerGenerator.CLEAR_STALE_REFERENCES_METHOD,
                               function(voidType));
        }

        code.aload(0)
            .getfield(generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
            .ifnonnull(() -> code
                .aload(0)
                .getfield(generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
                .aload(0)
                .invokeinterface(InvalidationListenerType(), "invalidated",
                                 function(voidType, ObservableType()))
            );

        code.aload(0)
            .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
            .ifnonnull(() -> code
                .aload(0)
                .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                .aload(0)
                .aload(0)
                .getfield(generatedClass, VALUE_FIELD, ObservableListType())
                .aload(0)
                .getfield(generatedClass, VALUE_FIELD, ObservableListType())
                .invokeinterface(ChangeListenerType(), "changed",
                                 function(voidType, ObservableValueType(), ObjectType(), ObjectType()))
            );

        code.aload(0)
            .getfield(generatedClass, LIST_CHANGE_LISTENER_FIELD, ListChangeListenerType())
            .ifnonnull(() -> code
                .aload(0)
                .getfield(generatedClass, ADAPTER_CHANGE_FIELD, adapterChangeType)
                .aload(1)
                .invokevirtual(adapterChangeType, ListSourceAdapterChangeGenerator.INIT_CHANGE_METHOD_NAME,
                               function(voidType, ListChangeListenerChangeType()))
                .aload(0)
                .getfield(generatedClass, LIST_CHANGE_LISTENER_FIELD, ListChangeListenerType())
                .aload(0)
                .getfield(generatedClass, ADAPTER_CHANGE_FIELD, adapterChangeType)
                .invokeinterface(ListChangeListenerType(), "onChanged",
                                 function(voidType, ListChangeListenerChangeType()))
            );

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

}
