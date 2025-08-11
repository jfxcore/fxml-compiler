// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
import javafx.beans.value.ObservableMapValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.generate.collections.MapWrapperGenerator.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Implements an {@link ObservableMapValue} that wraps a {@link Map} contained in an {@link ObservableValue}.
 * <p>
 * The generated class is a thin wrapper, as it simply delegates to the wrapped map.
 * Therefore, modifications of either map will be visible in the other map as well.
 * <p>
 * If the wrapped set implements {@link ObservableMap}, modifications will also fire change
 * events on the generated {@code ObservableMapValue}.
 * <p>
 * The generated class has a constructor that accepts the {@code ObservableValue};
 * {@code null} is not an acceptable argument value.
 */
public class MapObservableValueWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("MapObservableValueWrapper");

    private static final String OBSERVABLE_FIELD = "observable";
    private static final String WEAK_MAP_CHANGE_LISTENER_FIELD = "weakSetChangeListener";
    private static final String VALID_FIELD = "valid";

    private final TypeInstance observableType;

    public MapObservableValueWrapperGenerator() {
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableMapValueType());
    }

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(
            new MapAddRemoveChangeGenerator(),
            new MapSourceAdapterChangeGenerator());
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
        generatedClass.addInterface(ObservableMapValueType());
        generatedClass.addInterface(MapChangeListenerType());
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

        field = new CtField(WeakMapChangeListenerType(), WEAK_MAP_CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME),
                            ADAPTER_CHANGE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(ObservableMapType(), VALUE_FIELD, generatedClass);
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

        field = new CtField(MapChangeListenerType(), MAP_CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        createConstructor(context);
        createMapMethods(context);
        createGetValueMethod(context);
        createGetMethod(context);
        createInvalidatedMethod(context);
        createOnChangedMethod(context, generatedClass);
        createListenerMethods(context, generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType());
        createListenerMethods(context, generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType());
        createListenerMethods(context, generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType());
    }

    private void createMapMethods(BytecodeEmitContext context) throws Exception {
        CtClass delegateType = MapType();
        createDelegateMethod(context, intType, delegateType, "size");
        createDelegateMethod(context, booleanType, delegateType, "isEmpty");
        createDelegateMethod(context, booleanType, delegateType, "containsKey", ObjectType());
        createDelegateMethod(context, booleanType, delegateType, "containsValue", ObjectType());
        createDelegateMethod(context, ObjectType(), delegateType, "get", ObjectType());
        createDelegateMethod(context, ObjectType(), delegateType, "put", ObjectType(), ObjectType());
        createDelegateMethod(context, ObjectType(), delegateType, "remove", ObjectType());
        createDelegateMethod(context, voidType, delegateType, "putAll", MapType());
        createDelegateMethod(context, voidType, delegateType, "clear");
        createDelegateMethod(context, SetType(), delegateType, "keySet");
        createDelegateMethod(context, CollectionType(), delegateType, "values");
        createDelegateMethod(context, SetType(), delegateType, "entrySet");
        createDelegateMethod(context, intType, delegateType, "hashCode");
        createDelegateMethod(context, booleanType, delegateType, "equals", ObjectType());
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
                // if (this.map != null)
                code.aload(0)
                    .getfield(generatedClass, VALUE_FIELD, ObservableMapType())
                    .ifnonnull(() -> {
                        // this.map.removeListener(weakMapChangeListener);
                        code.aload(0)
                            .getfield(generatedClass, VALUE_FIELD, ObservableMapType())
                            .aload(0)
                            .getfield(generatedClass, WEAK_MAP_CHANGE_LISTENER_FIELD, WeakMapChangeListenerType())
                            .invokeinterface(ObservableMapType(), "removeListener",
                                             function(voidType, MapChangeListenerType()));
                    });

                // valid = true;
                // Map map = source.getValue();
                code.aload(0)
                    .iconst(1)
                    .putfield(generatedClass, VALID_FIELD, booleanType)
                    .aload(0)
                    .getfield(generatedClass, OBSERVABLE_FIELD, ObservableValueType())
                    .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                    .astore(listLocal);

                // if (map instanceof ObservableMap)
                code.aload(listLocal)
                    .isinstanceof(ObservableMapType())
                    .ifne(() -> code
                        // this.map = (ObservableMap)map;
                        // this.map.addListener(weakMapChangeListener);
                        .aload(0)
                        .aload(listLocal)
                        .checkcast(ObservableMapType())
                        .putfield(generatedClass, VALUE_FIELD, ObservableMapType())
                        .aload(0)
                        .getfield(generatedClass, VALUE_FIELD, ObservableMapType())
                        .aload(0)
                        .getfield(generatedClass, WEAK_MAP_CHANGE_LISTENER_FIELD, WeakMapChangeListenerType())
                        .invokeinterface(ObservableMapType(), "addListener",
                                         function(voidType, MapChangeListenerType())),
                    /*else*/ () -> code
                        // else if (set != null)
                        .aload(listLocal)
                        .ifnonnull(() -> code
                            // this.map = FXCollections.observableMap(map);
                            .aload(0)
                            .aload(listLocal)
                            .invokestatic(FXCollectionsType(), "observableMap",
                                          function(ObservableMapType(), MapType()))
                            .putfield(generatedClass, VALUE_FIELD, ObservableMapType()),
                        /*else*/ () -> code
                            // else this.set = null;
                            .aload(0)
                            .aconst_null()
                            .putfield(generatedClass, VALUE_FIELD, ObservableMapType())
                        )
                    );
            })
            .aload(0)
            .getfield(generatedClass, VALUE_FIELD, ObservableMapType())
            .areturn()
            .releaseLocal(listLocal);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    /**
     * The {@code invalidated} method is called when the value of the ObservableValue changes,
     * i.e. when the map wrapped by the ObservableValue is replaced entirely.
     */
    private void createInvalidatedMethod(BytecodeEmitContext context) throws Exception {
        CtMethod method = new CtMethod(voidType, "invalidated", new CtClass[] {ObservableType()}, generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        var ctx = new BytecodeEmitContext(context, generatedClass, 2, -1);
        Bytecode code = ctx.getOutput();
        Local oldValueLocal = code.acquireLocal(false);
        Local currentValueLocal = code.acquireLocal(false);
        Local iteratorLocal = code.acquireLocal(false);
        Local addRemoveChangeLocal = code.acquireLocal(false);
        Local entryLocal = code.acquireLocal(false);
        Local oldEntryKeyLocal = code.acquireLocal(false);
        Local oldEntryValueLocal = code.acquireLocal(false);
        Local newEntryKeyLocal = code.acquireLocal(false);
        Local newEntryValueLocal = code.acquireLocal(false);
        CtClass addRemoveChangeType = context.getNestedClasses().find(MapAddRemoveChangeGenerator.CLASS_NAME);

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

        // if (changeListener != null || mapChangeListener != null)
        code.aload(0)
            .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
            .ifnonnull(
                () -> code.iconst(1),
                () -> code
                    .aload(0)
                    .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                    .ifnonnull(
                        () -> code.iconst(1),
                        () -> code.iconst(0)))
            .ifne(() -> code
                // ObservableMap oldValue = this.value;
                // ObservableMap currentValue = this.get();
                .aload(0)
                .getfield(generatedClass, VALUE_FIELD, ObservableMapType())
                .astore(oldValueLocal)
                .aload(0)
                .invokeinterface(ObservableObjectValueType(), "get", function(ObjectType()))
                .checkcast(ObservableMapType())
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
                    // if (mapChangeListener != null)
                    .aload(0)
                    .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                    .ifnonnull(() -> code
                        // var change = new MapAddRemoveChange(this)
                        .anew(addRemoveChangeType)
                        .dup()
                        .aload(0)
                        .invokespecial(addRemoveChangeType, MethodInfo.nameInit,
                                       constructor(ObservableMapType()))
                        .astore(addRemoveChangeLocal)
                        // if (currentValue == null)
                        .aload(currentValueLocal)
                        .ifnull(() -> {
                            // for (Map.Entry removed : oldValue.entrySet()) {
                            //     addRemoveChange.initChange(removed.getKey(), removed.getValue(), false);
                            //     mapChangeListener.onChanged(addRemoveChange);
                            // }
                            code.aload(oldValueLocal)
                                .invokeinterface(MapType(), "entrySet", function(SetType()))
                                .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                .astore(iteratorLocal);

                            int position = code.position() + 1;

                            code.aload(iteratorLocal)
                                .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                .ifne(() -> code
                                    .aload(addRemoveChangeLocal)
                                    .aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                    .checkcast(MapEntryType())
                                    .astore(entryLocal)
                                    .aload(entryLocal)
                                    .invokeinterface(MapEntryType(), "getKey", function(ObjectType()))
                                    .aload(entryLocal)
                                    .invokeinterface(MapEntryType(), "getValue", function(ObjectType()))
                                    .invokevirtual(addRemoveChangeType, MapAddRemoveChangeGenerator.INIT_REMOVE_METHOD_NAME,
                                                   function(voidType, ObjectType(), ObjectType()))
                                    .aload(0)
                                    .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                                    .aload(addRemoveChangeLocal)
                                    .invokeinterface(MapChangeListenerType(), "onChanged",
                                                     function(voidType, MapChangeListenerChangeType()))
                                    .goto_position(position)
                                );
                            },
                        /*else*/ () -> code
                            // else if (oldValue == null)
                            .aload(oldValueLocal)
                            .ifnull(() -> {
                                // for (Map.Entry added : currentValue.entrySet()) {
                                //     addRemoveChange.initChange(added.getKey(), added.getValue(), true);
                                //     mapChangeListener.onChanged(addRemoveChange);
                                // }
                                code.aload(currentValueLocal)
                                    .invokeinterface(MapType(), "entrySet", function(SetType()))
                                    .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                    .astore(iteratorLocal);

                                int position = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                    .ifne(() -> code
                                        .aload(addRemoveChangeLocal)
                                        .aload(iteratorLocal)
                                        .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                        .checkcast(MapEntryType())
                                        .astore(entryLocal)
                                        .aload(entryLocal)
                                        .invokeinterface(MapEntryType(), "getKey", function(ObjectType()))
                                        .aload(entryLocal)
                                        .invokeinterface(MapEntryType(), "getValue", function(ObjectType()))
                                        .invokevirtual(addRemoveChangeType, MapAddRemoveChangeGenerator.INIT_ADD_METHOD_NAME,
                                                       function(voidType, ObjectType(), ObjectType()))
                                        .aload(0)
                                        .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                                        .aload(addRemoveChangeLocal)
                                        .invokeinterface(MapChangeListenerType(), "onChanged",
                                                         function(voidType, MapChangeListenerChangeType()))
                                        .goto_position(position)
                                    );
                                },
                            /*else*/ () -> {
                                // for (Map.Entry entry : oldValue.entrySet()) {
                                //     var oldEntryKey = entry.getKey();
                                //     var oldEntryValue = entry.getValue();
                                //     if (currentValue.containsKey(oldEntryKey)) {
                                //         var newEntryValue = currentValue.get(oldEntryKey);
                                //         if (!Objects.equals(oldEntryValue, newEntryValue)) {
                                //             addRemoveChange.initPut(oldEntryKey, newEntryValue);
                                //             mapChangeListener.onChanged(addRemoveChange);
                                //         }
                                //     } else {
                                //         addRemoveChange.initRemoved(oldEntryKey, oldEntryValue);
                                //         mapChangeListener.onChanged(addRemoveChange);
                                //     }
                                // }
                                code.aload(oldValueLocal)
                                    .invokeinterface(MapType(), "entrySet", function(SetType()))
                                    .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                    .astore(iteratorLocal);

                                int position1 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                        .checkcast(MapEntryType())
                                        .astore(entryLocal)
                                        .aload(entryLocal)
                                        .invokeinterface(MapEntryType(), "getKey", function(ObjectType()))
                                        .astore(oldEntryKeyLocal)
                                        .aload(entryLocal)
                                        .invokeinterface(MapEntryType(), "getValue", function(ObjectType()))
                                        .astore(oldEntryValueLocal)
                                        .aload(currentValueLocal)
                                        .aload(oldEntryKeyLocal)
                                        .invokeinterface(MapType(), "containsKey", function(booleanType, ObjectType()))
                                        .ifne(() -> code
                                            .aload(currentValueLocal)
                                            .aload(oldEntryKeyLocal)
                                            .invokeinterface(MapType(), "get", function(ObjectType(), ObjectType()))
                                            .astore(newEntryValueLocal)
                                            .aload(oldEntryValueLocal)
                                            .aload(newEntryValueLocal)
                                            .invokestatic(ObjectsType(), "equals", function(booleanType, ObjectType(), ObjectType()))
                                            .ifeq(() -> code
                                                .aload(addRemoveChangeLocal)
                                                .aload(oldEntryKeyLocal)
                                                .aload(oldEntryValueLocal)
                                                .aload(newEntryValueLocal)
                                                .invokevirtual(addRemoveChangeType, MapAddRemoveChangeGenerator.INIT_REPLACE_METHOD_NAME,
                                                               function(voidType, ObjectType(), ObjectType(), ObjectType()))
                                                .aload(0)
                                                .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                                                .aload(addRemoveChangeLocal)
                                                .invokeinterface(MapChangeListenerType(), "onChanged",
                                                                 function(voidType, MapChangeListenerChangeType()))
                                            ),
                                        /*else*/ () -> code
                                           .aload(addRemoveChangeLocal)
                                           .aload(oldEntryKeyLocal)
                                           .aload(oldEntryValueLocal)
                                           .invokevirtual(addRemoveChangeType, MapAddRemoveChangeGenerator.INIT_REMOVE_METHOD_NAME,
                                                          function(voidType, ObjectType(), ObjectType()))
                                           .aload(0)
                                           .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                                           .aload(addRemoveChangeLocal)
                                           .invokeinterface(MapChangeListenerType(), "onChanged",
                                                            function(voidType, MapChangeListenerChangeType()))
                                        )
                                        .goto_position(position1)
                                    );

                                // for (Entry entry : currentValue.entrySet()) {
                                //     var key = entry.getKey();
                                //     if (!oldValue.containsKey(key)) {
                                //         addRemoveChange.initAdd(key, entry.getValue());
                                //         mapChangeListener.onChanged(addRemoveChange);
                                //     }
                                // }
                                code.aload(currentValueLocal)
                                    .invokeinterface(MapType(), "entrySet", function(SetType()))
                                    .invokeinterface(SetType(), "iterator", function(IteratorType()))
                                    .astore(iteratorLocal);

                                int position2 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invokeinterface(IteratorType(), "hasNext", function(booleanType))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invokeinterface(IteratorType(), "next", function(ObjectType()))
                                        .checkcast(MapEntryType())
                                        .astore(entryLocal)
                                        .aload(entryLocal)
                                        .invokeinterface(MapEntryType(), "getKey", function(ObjectType()))
                                        .astore(newEntryKeyLocal)
                                        .aload(oldValueLocal)
                                        .aload(newEntryKeyLocal)
                                        .invokeinterface(MapType(), "containsKey",
                                                         function(booleanType, ObjectType()))
                                        .ifeq(() -> code
                                            .aload(addRemoveChangeLocal)
                                            .aload(newEntryKeyLocal)
                                            .aload(entryLocal)
                                            .invokeinterface(MapEntryType(), "getValue", function(ObjectType()))
                                            .invokevirtual(addRemoveChangeType, MapAddRemoveChangeGenerator.INIT_ADD_METHOD_NAME,
                                                           function(voidType, ObjectType(), ObjectType()))
                                            .aload(0)
                                            .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                                            .aload(addRemoveChangeLocal)
                                            .invokeinterface(MapChangeListenerType(), "onChanged",
                                                             function(voidType, MapChangeListenerChangeType()))
                                        )
                                        .goto_position(position2)
                                    );
                            }))
                )));

        code.releaseLocal(oldEntryKeyLocal);
        code.releaseLocal(oldEntryValueLocal);
        code.releaseLocal(newEntryKeyLocal);
        code.releaseLocal(newEntryValueLocal);
        code.releaseLocal(entryLocal);
        code.releaseLocal(addRemoveChangeLocal);
        code.releaseLocal(iteratorLocal);
        code.releaseLocal(oldValueLocal);
        code.releaseLocal(currentValueLocal);
        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void createConstructor(BytecodeEmitContext context) throws Exception {
        CtConstructor constructor = new CtConstructor(
                new CtClass[] {context.getMarkupClass(), ObservableValueType()}, generatedClass);
        constructor.setModifiers(Modifier.PUBLIC);
        generatedClass.addConstructor(constructor);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, generatedClass, 3, -1);
        CtClass adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);
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

        // weakMapChangeListener = new WeakMapChangeListener(this);
        code.aload(0)
            .anew(WeakMapChangeListenerType())
            .dup()
            .aload(0)
            .invokespecial(WeakMapChangeListenerType(), MethodInfo.nameInit,
                           constructor(MapChangeListenerType()))
            .putfield(generatedClass, WEAK_MAP_CHANGE_LISTENER_FIELD, WeakMapChangeListenerType());

        // mapAdapterChange = new MapAdapterChange(this);
        code.aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invokespecial(adapterChangeType, MethodInfo.nameInit, constructor(ObservableMapType()))
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
            .invokeinterface(ObservableMapValueType(), "get", function(ObjectType()))
            .pop()
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    /**
     * Creates a method that delegates the method call to an ObservableMap that is wrapped by the current object.
     * The implementation is similar to:
     * <pre>{@code
     *     public boolean containsKey(Object key) {
     *         ObservableMap map = get();
     *         return map == null ? Collections.emptyMap().containsKey(key) : map.containsKey(key);
     *     }
     * }</pre>
     */
    private void createDelegateMethod(BytecodeEmitContext context, CtClass retType, CtClass delegateType,
                                      String methodName, CtClass... params) throws Exception {
        boolean delegatesToObservableMap = delegateType.subtypeOf(ObservableMapType());

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
                    .checkcast(ObservableMapType())
                    .astore(listLocal)
                    .aload(listLocal)
                    .ifnull(
                        () -> {
                            if (delegatesToObservableMap) {
                                code.invokestatic(FXCollectionsType(), "emptyObservableMap", function(MapType()));
                            } else {
                                code.invokestatic(CollectionsType(), "emptyMap", function(MapType()));
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
