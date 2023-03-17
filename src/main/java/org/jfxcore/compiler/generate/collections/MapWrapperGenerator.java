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
import org.jfxcore.compiler.util.TypeInstance;
import javafx.beans.value.ObservableMapValue;
import javafx.collections.ObservableMap;
import java.util.List;
import java.util.Map;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Implements an {@link ObservableMapValue} that wraps a {@link Map}.
 * <p>
 * The generated class is a thin wrapper, as it simply delegates to the wrapped map.
 * Therefore, modifications of either map will be visible in the other map as well.
 * <p>
 * If the wrapped map implements {@link ObservableMap}, modifications will also fire change
 * events on the generated {@code ObservableMapValue}.
 * <p>
 * The generated class has a constructor that accepts the wrapped map, or {@code null}.
 * If {@code null} is passed to the constructor, the generated class represents an empty map.
 */
public class MapWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("MapWrapper");

    static final String ROOT_REF = "root";
    static final String VALUE_FIELD = "value";
    static final String ADAPTER_CHANGE_FIELD = "change";
    static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    static final String CHANGE_LISTENER_FIELD = "changeListener";
    static final String MAP_CHANGE_LISTENER_FIELD = "mapChangeListener";

    private final TypeInstance observableType;

    public MapWrapperGenerator() {
        observableType = new Resolver(SourceInfo.none()).getTypeInstance(ObservableMapValueType());
    }

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(new MapSourceAdapterChangeGenerator(), new ReferenceTrackerGenerator());
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
        clazz.addInterface(ObservableMapValueType());
        clazz.addInterface(MapChangeListenerType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(context.getMarkupClass(), ROOT_REF, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(ObservableMapType(), VALUE_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME),
                            ADAPTER_CHANGE_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(InvalidationListenerType(), INVALIDATION_LISTENER_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        field = new CtField(ChangeListenerType(), CHANGE_LISTENER_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        field = new CtField(MapChangeListenerType(), MAP_CHANGE_LISTENER_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        createConstructor(context);
        createMapMethods(context);
        createObservableValueMethods(context);
        createOnChangedMethod(context, clazz);
        createListenerMethods(context, clazz, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType());
    }

    private void createMapMethods(BytecodeEmitContext context) throws Exception {
        CtClass fieldType = ObservableMapType();
        createFieldDelegateMethod(context, clazz, intType, VALUE_FIELD, fieldType, "size");
        createFieldDelegateMethod(context, clazz, booleanType, VALUE_FIELD, fieldType, "isEmpty");
        createFieldDelegateMethod(context, clazz, booleanType, VALUE_FIELD, fieldType, "containsKey", ObjectType());
        createFieldDelegateMethod(context, clazz, booleanType, VALUE_FIELD, fieldType, "containsValue", ObjectType());
        createFieldDelegateMethod(context, clazz, ObjectType(), VALUE_FIELD, fieldType, "get", ObjectType());
        createFieldDelegateMethod(context, clazz, ObjectType(), VALUE_FIELD, fieldType, "put", ObjectType(), ObjectType());
        createFieldDelegateMethod(context, clazz, ObjectType(), VALUE_FIELD, fieldType, "putAll", MapType());
        createFieldDelegateMethod(context, clazz, ObjectType(), VALUE_FIELD, fieldType, "remove", ObjectType());
        createFieldDelegateMethod(context, clazz, voidType, VALUE_FIELD, fieldType, "clear");
        createFieldDelegateMethod(context, clazz, SetType(), VALUE_FIELD, fieldType, "keySet");
        createFieldDelegateMethod(context, clazz, CollectionType(), VALUE_FIELD, fieldType, "values");
        createFieldDelegateMethod(context, clazz, SetType(), VALUE_FIELD, fieldType, "entrySet");
        createFieldDelegateMethod(context, clazz, booleanType, VALUE_FIELD, fieldType, "equals", ObjectType());
        createFieldDelegateMethod(context, clazz, intType, VALUE_FIELD, fieldType, "hashCode");
    }

    private void createObservableValueMethods(BytecodeEmitContext context) throws Exception {
        createListenerMethods(context, clazz, INVALIDATION_LISTENER_FIELD, InvalidationListenerType());
        createListenerMethods(context, clazz, CHANGE_LISTENER_FIELD, ChangeListenerType());

        CtMethod method = new CtMethod(ObjectType(), "get", new CtClass[0], clazz);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(method);
        var ctx = new BytecodeEmitContext(context, clazz, 1, -1);
        ctx.getOutput().aload(0).getfield(clazz, VALUE_FIELD, ObservableMapType()).areturn();
        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(clazz.getClassPool());

        method = new CtMethod(ObjectType(), "getValue", new CtClass[0], clazz);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(method);
        ctx = new BytecodeEmitContext(context, clazz, 1, -1);
        ctx.getOutput().aload(0).getfield(clazz, VALUE_FIELD, ObservableMapType()).areturn();
        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    private void createConstructor(BytecodeEmitContext context) throws Exception {
        CtConstructor constructor = new CtConstructor(new CtClass[] {context.getMarkupClass(), MapType()}, clazz);
        constructor.setModifiers(Modifier.PUBLIC);
        clazz.addConstructor(constructor);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, clazz, 3, -1);
        Bytecode code = ctx.getOutput();
        Local valueLocal = code.acquireLocal(false);
        CtClass adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);

        code.aload(0)
            .invokespecial(clazz.getSuperclass(), MethodInfo.nameInit, constructor())
            .aload(0)
            .aload(1)
            .putfield(clazz, ROOT_REF, context.getMarkupClass())
            .aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invokespecial(adapterChangeType, MethodInfo.nameInit, constructor(ObservableMapType()))
            .putfield(clazz, ADAPTER_CHANGE_FIELD, adapterChangeType)
            .aconst_null()
            .astore(valueLocal)
            .aload(2)
            .isinstanceof(ObservableMapType())
            .ifne(() -> code
                .aload(2)
                .checkcast(ObservableMapType())
                .astore(valueLocal),
            /*else*/ () -> code
                .aload(2)
                .ifnonnull(() -> code
                    .aload(2)
                    .invokestatic(FXCollectionsType(), "observableMap",
                                  function(ObservableMapType(), MapType()))
                    .astore(valueLocal)
                )
            )
            .aload(valueLocal)
            .ifnonnull(() -> code
                .aload(valueLocal)
                .anew(WeakMapChangeListenerType())
                .dup()
                .aload(0)
                .invokespecial(WeakMapChangeListenerType(), MethodInfo.nameInit,
                               constructor(MapChangeListenerType()))
                .invokeinterface(ObservableMapType(), "addListener",
                                 function(voidType, MapChangeListenerType())),
            /*else*/ () -> code
                .invokestatic(FXCollectionsType(), "emptyObservableMap",
                              function(ObservableMapType()))
                .astore(valueLocal)
            )
            .aload(0)
            .aload(valueLocal)
            .putfield(clazz, VALUE_FIELD, ObservableMapType())
            .vreturn()
            .releaseLocal(valueLocal);

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

    static void createOnChangedMethod(BytecodeEmitContext context, CtClass clazz) throws Exception {
        CtMethod method = new CtMethod(
            voidType, "onChanged", new CtClass[] {MapChangeListenerChangeType()}, clazz);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(method);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, clazz, 2, -1);
        Bytecode code = ctx.getOutput();
        CtClass adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);

        code.aload(0)
            .getfield(clazz, ROOT_REF, context.getMarkupClass())
            .invokevirtual(context.getMarkupClass(), ReferenceTrackerGenerator.CLEAR_STALE_REFERENCES_METHOD,
                           function(voidType));

        code.aload(0)
            .getfield(clazz, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
            .ifnonnull(() -> code
                .aload(0)
                .getfield(clazz, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
                .aload(0)
                .invokeinterface(InvalidationListenerType(), "invalidated",
                                 function(voidType, ObservableType()))
            );

        code.aload(0)
            .getfield(clazz, CHANGE_LISTENER_FIELD, ChangeListenerType())
            .ifnonnull(() -> code
                .aload(0)
                .getfield(clazz, CHANGE_LISTENER_FIELD, ChangeListenerType())
                .aload(0)
                .aload(0)
                .getfield(clazz, VALUE_FIELD, ObservableMapType())
                .aload(0)
                .getfield(clazz, VALUE_FIELD, ObservableMapType())
                .invokeinterface(ChangeListenerType(), "changed",
                                 function(voidType, ObservableType(), ObjectType(), ObjectType()))
            );

        code.aload(0)
            .getfield(clazz, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
            .ifnonnull(() -> code
                .aload(0)
                .getfield(clazz, ADAPTER_CHANGE_FIELD, adapterChangeType)
                .aload(1)
                .invokevirtual(adapterChangeType, MapSourceAdapterChangeGenerator.INIT_CHANGE_METHOD_NAME,
                               function(voidType, MapChangeListenerChangeType()))
                .aload(0)
                .getfield(clazz, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                .aload(0)
                .getfield(clazz, ADAPTER_CHANGE_FIELD, adapterChangeType)
                .invokeinterface(MapChangeListenerType(), "onChanged",
                                 function(voidType, MapChangeListenerChangeType()))
            );

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

}
