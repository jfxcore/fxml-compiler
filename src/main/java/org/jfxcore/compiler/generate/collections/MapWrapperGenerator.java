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
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
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
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableMapValueType());
    }

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(new MapSourceAdapterChangeGenerator());
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
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(context.getMarkupClass(), ROOT_REF, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(ObservableMapType(), VALUE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME),
                            ADAPTER_CHANGE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
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
        createObservableValueMethods(context);
        createOnChangedMethod(context, generatedClass);
        createListenerMethods(context, generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType());
    }

    private void createMapMethods(BytecodeEmitContext context) throws Exception {
        CtClass fieldType = ObservableMapType();
        createFieldDelegateMethod(context, generatedClass, intType, VALUE_FIELD, fieldType, "size");
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "isEmpty");
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "containsKey", ObjectType());
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "containsValue", ObjectType());
        createFieldDelegateMethod(context, generatedClass, ObjectType(), VALUE_FIELD, fieldType, "get", ObjectType());
        createFieldDelegateMethod(context, generatedClass, ObjectType(), VALUE_FIELD, fieldType, "put", ObjectType(), ObjectType());
        createFieldDelegateMethod(context, generatedClass, ObjectType(), VALUE_FIELD, fieldType, "remove", ObjectType());
        createFieldDelegateMethod(context, generatedClass, voidType, VALUE_FIELD, fieldType, "putAll", MapType());
        createFieldDelegateMethod(context, generatedClass, voidType, VALUE_FIELD, fieldType, "clear");
        createFieldDelegateMethod(context, generatedClass, SetType(), VALUE_FIELD, fieldType, "keySet");
        createFieldDelegateMethod(context, generatedClass, CollectionType(), VALUE_FIELD, fieldType, "values");
        createFieldDelegateMethod(context, generatedClass, SetType(), VALUE_FIELD, fieldType, "entrySet");
        createFieldDelegateMethod(context, generatedClass, booleanType, VALUE_FIELD, fieldType, "equals", ObjectType());
        createFieldDelegateMethod(context, generatedClass, intType, VALUE_FIELD, fieldType, "hashCode");
    }

    private void createObservableValueMethods(BytecodeEmitContext context) throws Exception {
        createListenerMethods(context, generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType());
        createListenerMethods(context, generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType());

        CtMethod method = new CtMethod(ObjectType(), "get", new CtClass[0], generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        var ctx = new BytecodeEmitContext(context, generatedClass, 1, -1);
        ctx.getOutput().aload(0).getfield(generatedClass, VALUE_FIELD, ObservableMapType()).areturn();
        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());

        method = new CtMethod(ObjectType(), "getValue", new CtClass[0], generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        ctx = new BytecodeEmitContext(context, generatedClass, 1, -1);
        ctx.getOutput().aload(0).getfield(generatedClass, VALUE_FIELD, ObservableMapType()).areturn();
        method.getMethodInfo().setCodeAttribute(ctx.getOutput().toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void createConstructor(BytecodeEmitContext context) throws Exception {
        CtConstructor constructor = new CtConstructor(new CtClass[] {context.getMarkupClass(), MapType()}, generatedClass);
        constructor.setModifiers(Modifier.PUBLIC);
        generatedClass.addConstructor(constructor);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, generatedClass, 3, -1);
        Bytecode code = ctx.getOutput();
        Local valueLocal = code.acquireLocal(false);
        CtClass adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);

        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor())
            .aload(0)
            .aload(1)
            .putfield(generatedClass, ROOT_REF, context.getMarkupClass())
            .aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invokespecial(adapterChangeType, MethodInfo.nameInit, constructor(ObservableMapType()))
            .putfield(generatedClass, ADAPTER_CHANGE_FIELD, adapterChangeType)
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
            .putfield(generatedClass, VALUE_FIELD, ObservableMapType())
            .vreturn()
            .releaseLocal(valueLocal);

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    static void createOnChangedMethod(BytecodeEmitContext context, CtClass generatedClass) throws Exception {
        CtMethod method = new CtMethod(
            voidType, "onChanged", new CtClass[] {MapChangeListenerChangeType()}, generatedClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(method);
        BytecodeEmitContext ctx = new BytecodeEmitContext(context, generatedClass, 2, -1);
        Bytecode code = ctx.getOutput();
        CtClass adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);

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
                .getfield(generatedClass, VALUE_FIELD, ObservableMapType())
                .aload(0)
                .getfield(generatedClass, VALUE_FIELD, ObservableMapType())
                .invokeinterface(ChangeListenerType(), "changed",
                                 function(voidType, ObservableValueType(), ObjectType(), ObjectType()))
            );

        code.aload(0)
            .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
            .ifnonnull(() -> code
                .aload(0)
                .getfield(generatedClass, ADAPTER_CHANGE_FIELD, adapterChangeType)
                .aload(1)
                .invokevirtual(adapterChangeType, MapSourceAdapterChangeGenerator.INIT_CHANGE_METHOD_NAME,
                               function(voidType, MapChangeListenerChangeType()))
                .aload(0)
                .getfield(generatedClass, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerType())
                .aload(0)
                .getfield(generatedClass, ADAPTER_CHANGE_FIELD, adapterChangeType)
                .invokeinterface(MapChangeListenerType(), "onChanged",
                                 function(voidType, MapChangeListenerChangeType()))
            );

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }
}
