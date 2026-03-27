// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import javafx.beans.value.ObservableMapValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableMap;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.ReferenceTrackerGenerator;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

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
    private static final String ROOT_REF = "root";
    private static final String VALUE_FIELD = "value";
    private static final String ADAPTER_CHANGE_FIELD = "change";
    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";
    private static final String MAP_CHANGE_LISTENER_FIELD = "mapChangeListener";

    private final TypeInstance observableType;

    public MapObservableValueWrapperGenerator() {
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableMapValueDecl());
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
    public final TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            .addInterface(ObservableMapValueDecl())
            .addInterface(MapChangeListenerDecl())
            .addInterface(InvalidationListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);
        createField(ROOT_REF, context.getMarkupClass()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(OBSERVABLE_FIELD, ObservableValueDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(WEAK_MAP_CHANGE_LISTENER_FIELD, WeakMapChangeListenerDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(ADAPTER_CHANGE_FIELD, adapterChangeType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(VALUE_FIELD, ObservableMapDecl()).setModifiers(Modifier.PRIVATE);
        createField(VALID_FIELD, booleanDecl()).setModifiers(Modifier.PRIVATE);
        createField(INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(CHANGE_LISTENER_FIELD, ChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(MAP_CHANGE_LISTENER_FIELD, MapChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        createConstructor(context);
        createMapMethods();
        createGetValueMethod();
        createGetMethod();
        createInvalidatedMethod(context);
        createOnChangedMethod(context);

        createListenerMethods(this, INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl());
        createListenerMethods(this, CHANGE_LISTENER_FIELD, ChangeListenerDecl());
        createListenerMethods(this, MAP_CHANGE_LISTENER_FIELD, MapChangeListenerDecl());
    }

    private void createMapMethods() {
        TypeDeclaration delegateType = MapDecl();
        createDelegateMethod(intDecl(), delegateType, "size");
        createDelegateMethod(booleanDecl(), delegateType, "isEmpty");
        createDelegateMethod(booleanDecl(), delegateType, "containsKey", ObjectDecl());
        createDelegateMethod(booleanDecl(), delegateType, "containsValue", ObjectDecl());
        createDelegateMethod(ObjectDecl(), delegateType, "get", ObjectDecl());
        createDelegateMethod(ObjectDecl(), delegateType, "put", ObjectDecl(), ObjectDecl());
        createDelegateMethod(ObjectDecl(), delegateType, "remove", ObjectDecl());
        createDelegateMethod(voidDecl(), delegateType, "putAll", MapDecl());
        createDelegateMethod(voidDecl(), delegateType, "clear");
        createDelegateMethod(SetDecl(), delegateType, "keySet");
        createDelegateMethod(CollectionDecl(), delegateType, "values");
        createDelegateMethod(SetDecl(), delegateType, "entrySet");
        createDelegateMethod(intDecl(), delegateType, "hashCode");
        createDelegateMethod(booleanDecl(), delegateType, "equals", ObjectDecl());
    }

    private void createGetValueMethod() {
        createBehavior(createMethod("getValue", ObjectDecl()), code -> code
            .aload(0)
            .invoke(ObservableObjectValueDecl().requireDeclaredMethod("get"))
            .areturn());
    }

    private void createGetMethod() {
        var method = createMethod("get", ObjectDecl());
        var code = new Bytecode(method);

        Local mapLocal = code.acquireLocal(false);
        FieldDeclaration validField = requireDeclaredField(VALID_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        FieldDeclaration weakMapChangeListenerField = requireDeclaredField(WEAK_MAP_CHANGE_LISTENER_FIELD);
        FieldDeclaration observableField = requireDeclaredField(OBSERVABLE_FIELD);

        code.aload(0)
            .getfield(validField)
            .ifeq(() -> {
                // if (this.map != null)
                code.aload(0)
                    .getfield(valueField)
                    .ifnonnull(() -> code
                        // this.map.removeListener(weakMapChangeListener);
                        .aload(0)
                        .getfield(valueField)
                        .aload(0)
                        .getfield(weakMapChangeListenerField)
                        .invoke(ObservableMapDecl().requireDeclaredMethod("removeListener", MapChangeListenerDecl()))
                    );

                // valid = true;
                // Map map = source.getValue();
                code.aload(0)
                    .iconst(1)
                    .putfield(validField)
                    .aload(0)
                    .getfield(observableField)
                    .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                    .astore(mapLocal);

                // if (map instanceof ObservableMap)
                code.aload(mapLocal)
                    .isinstanceof(ObservableMapDecl())
                    .ifne(() -> code
                        // this.map = (ObservableMap)map;
                        // this.map.addListener(weakMapChangeListener);
                        .aload(0)
                        .aload(mapLocal)
                        .checkcast(ObservableMapDecl())
                        .putfield(valueField)
                        .aload(0)
                        .getfield(valueField)
                        .aload(0)
                        .getfield(weakMapChangeListenerField)
                        .invoke(ObservableMapDecl().requireDeclaredMethod("addListener", MapChangeListenerDecl())),
                    /*else*/ () -> code
                        // else if (map != null)
                        .aload(mapLocal)
                        .ifnonnull(() -> code
                            // this.map = FXCollections.observableMap(map);
                            .aload(0)
                            .aload(mapLocal)
                            .invoke(FXCollectionsDecl().requireDeclaredMethod("observableMap", MapDecl()))
                            .putfield(valueField),
                        /*else*/ () -> code
                            // else this.map = null;
                            .aload(0)
                            .aconst_null()
                            .putfield(valueField)
                        )
                    );
            })
            .aload(0)
            .getfield(valueField)
            .areturn()
            .releaseLocal(mapLocal);

        method.setCode(code)
              .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    /**
     * The {@code invalidated} method is called when the value of the ObservableValue changes,
     * i.e. when the map wrapped by the ObservableValue is replaced entirely.
     */
    private void createInvalidatedMethod(BytecodeEmitContext context) {
        var method = createMethod("invalidated", voidDecl(), ObservableDecl());
        var code = new Bytecode(method);

        FieldDeclaration rootRefField = requireDeclaredField(ROOT_REF);
        FieldDeclaration validField = requireDeclaredField(VALID_FIELD);
        FieldDeclaration invalidationListenerField = requireDeclaredField(INVALIDATION_LISTENER_FIELD);
        FieldDeclaration changeListenerField = requireDeclaredField(CHANGE_LISTENER_FIELD);
        FieldDeclaration mapChangeListenerField = requireDeclaredField(MAP_CHANGE_LISTENER_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        TypeDeclaration addRemoveChangeType = context.getNestedClasses().find(MapAddRemoveChangeGenerator.CLASS_NAME);

        Local oldValueLocal = code.acquireLocal(false);
        Local currentValueLocal = code.acquireLocal(false);
        Local iteratorLocal = code.acquireLocal(false);
        Local addRemoveChangeLocal = code.acquireLocal(false);
        Local entryLocal = code.acquireLocal(false);
        Local oldEntryKeyLocal = code.acquireLocal(false);
        Local oldEntryValueLocal = code.acquireLocal(false);
        Local newEntryKeyLocal = code.acquireLocal(false);
        Local newEntryValueLocal = code.acquireLocal(false);

        if (context.isGeneratorActive(ReferenceTrackerGenerator.class)) {
            // markupRef.clearStaleReferences();
            code.aload(0)
                .getfield(rootRefField)
                .invoke(context.getMarkupClass()
                               .requireDeclaredMethod(ReferenceTrackerGenerator.CLEAR_STALE_REFERENCES_METHOD));
        }

        // this.valid = false;
        code.aload(0)
            .iconst(0)
            .putfield(validField);

        // if (invalidationListener != null)
        code.aload(0)
            .getfield(invalidationListenerField)
            .ifnonnull(() -> code
                // invalidationListener.invalidated(this);
                .aload(0)
                .getfield(invalidationListenerField)
                .aload(0)
                .invoke(InvalidationListenerDecl().requireDeclaredMethod("invalidated", ObservableDecl()))
            );

        // if (changeListener != null || mapChangeListener != null)
        code.aload(0)
            .getfield(changeListenerField)
            .ifnonnull(
                () -> code.iconst(1),
                () -> code
                    .aload(0)
                    .getfield(mapChangeListenerField)
                    .ifnonnull(
                        () -> code.iconst(1),
                        () -> code.iconst(0)))
            .ifne(() -> code
                // ObservableMap oldValue = this.value;
                // ObservableMap currentValue = this.get();
                .aload(0)
                .getfield(valueField)
                .astore(oldValueLocal)
                .aload(0)
                .invoke(ObservableObjectValueDecl().requireDeclaredMethod("get"))
                .checkcast(ObservableMapDecl())
                .astore(currentValueLocal)
                // if (oldValue != currentValue) {
                .aload(oldValueLocal)
                .aload(currentValueLocal)
                .if_acmpne(() -> code
                    // if (changeListener != null)
                    .aload(0)
                    .getfield(changeListenerField)
                    .ifnonnull(() -> code
                        // changeListener.changed(this, oldValue, currentValue);
                        .aload(0)
                        .getfield(changeListenerField)
                        .aload(0)
                        .aload(oldValueLocal)
                        .aload(currentValueLocal)
                        .invoke(ChangeListenerDecl().requireDeclaredMethod("changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl())))
                    // if (mapChangeListener != null)
                    .aload(0)
                    .getfield(mapChangeListenerField)
                    .ifnonnull(() -> code
                        // var change = new MapAddRemoveChange(this)
                        .anew(addRemoveChangeType)
                        .dup()
                        .aload(0)
                        .invoke(addRemoveChangeType.requireDeclaredConstructor(ObservableMapDecl()))
                        .astore(addRemoveChangeLocal)
                        // if (currentValue == null)
                        .aload(currentValueLocal)
                        .ifnull(() -> {
                            // for (Map.Entry removed : oldValue.entrySet()) {
                            //     addRemoveChange.initChange(removed.getKey(), removed.getValue(), false);
                            //     mapChangeListener.onChanged(addRemoveChange);
                            // }
                            code.aload(oldValueLocal)
                                .invoke(MapDecl().requireDeclaredMethod("entrySet"))
                                .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                .astore(iteratorLocal);

                            int position = code.position() + 1;

                            code.aload(iteratorLocal)
                                .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                .ifne(() -> code
                                    .aload(addRemoveChangeLocal)
                                    .aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                    .checkcast(MapEntryDecl())
                                    .astore(entryLocal)
                                    .aload(entryLocal)
                                    .invoke(MapEntryDecl().requireDeclaredMethod("getKey"))
                                    .aload(entryLocal)
                                    .invoke(MapEntryDecl().requireDeclaredMethod("getValue"))
                                    .invoke(addRemoveChangeType.requireDeclaredMethod(MapAddRemoveChangeGenerator.INIT_REMOVE_METHOD_NAME, ObjectDecl(), ObjectDecl()))
                                    .aload(0)
                                    .getfield(mapChangeListenerField)
                                    .aload(addRemoveChangeLocal)
                                    .invoke(MapChangeListenerDecl().requireDeclaredMethod("onChanged", MapChangeListenerChangeDecl()))
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
                                    .invoke(MapDecl().requireDeclaredMethod("entrySet"))
                                    .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                    .astore(iteratorLocal);

                                int position = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                    .ifne(() -> code
                                        .aload(addRemoveChangeLocal)
                                        .aload(iteratorLocal)
                                        .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                        .checkcast(MapEntryDecl())
                                        .astore(entryLocal)
                                        .aload(entryLocal)
                                        .invoke(MapEntryDecl().requireDeclaredMethod("getKey"))
                                        .aload(entryLocal)
                                        .invoke(MapEntryDecl().requireDeclaredMethod("getValue"))
                                        .invoke(addRemoveChangeType.requireDeclaredMethod(MapAddRemoveChangeGenerator.INIT_ADD_METHOD_NAME, ObjectDecl(), ObjectDecl()))
                                        .aload(0)
                                        .getfield(mapChangeListenerField)
                                        .aload(addRemoveChangeLocal)
                                        .invoke(MapChangeListenerDecl().requireDeclaredMethod("onChanged", MapChangeListenerChangeDecl()))
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
                                    .invoke(MapDecl().requireDeclaredMethod("entrySet"))
                                    .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                    .astore(iteratorLocal);

                                int position1 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                        .checkcast(MapEntryDecl())
                                        .astore(entryLocal)
                                        .aload(entryLocal)
                                        .invoke(MapEntryDecl().requireDeclaredMethod("getKey"))
                                        .astore(oldEntryKeyLocal)
                                        .aload(entryLocal)
                                        .invoke(MapEntryDecl().requireDeclaredMethod("getValue"))
                                        .astore(oldEntryValueLocal)
                                        .aload(currentValueLocal)
                                        .aload(oldEntryKeyLocal)
                                        .invoke(MapDecl().requireDeclaredMethod("containsKey", ObjectDecl()))
                                        .ifne(() -> code
                                            .aload(currentValueLocal)
                                            .aload(oldEntryKeyLocal)
                                            .invoke(MapDecl().requireDeclaredMethod("get", ObjectDecl()))
                                            .astore(newEntryValueLocal)
                                            .aload(oldEntryValueLocal)
                                            .aload(newEntryValueLocal)
                                            .invoke(ObjectsDecl().requireDeclaredMethod("equals", ObjectDecl(), ObjectDecl()))
                                            .ifeq(() -> code
                                                .aload(addRemoveChangeLocal)
                                                .aload(oldEntryKeyLocal)
                                                .aload(oldEntryValueLocal)
                                                .aload(newEntryValueLocal)
                                                .invoke(addRemoveChangeType.requireDeclaredMethod(
                                                    MapAddRemoveChangeGenerator.INIT_REPLACE_METHOD_NAME,
                                                    ObjectDecl(), ObjectDecl(), ObjectDecl()))
                                                .aload(0)
                                                .getfield(mapChangeListenerField)
                                                .aload(addRemoveChangeLocal)
                                                .invoke(MapChangeListenerDecl().requireDeclaredMethod(
                                                    "onChanged", MapChangeListenerChangeDecl()))
                                            ),
                                        /*else*/ () -> code
                                           .aload(addRemoveChangeLocal)
                                           .aload(oldEntryKeyLocal)
                                           .aload(oldEntryValueLocal)
                                           .invoke(addRemoveChangeType.requireDeclaredMethod(
                                               MapAddRemoveChangeGenerator.INIT_REMOVE_METHOD_NAME, ObjectDecl(), ObjectDecl()))
                                           .aload(0)
                                           .getfield(mapChangeListenerField)
                                           .aload(addRemoveChangeLocal)
                                           .invoke(MapChangeListenerDecl().requireDeclaredMethod(
                                               "onChanged", MapChangeListenerChangeDecl()))
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
                                    .invoke(MapDecl().requireDeclaredMethod("entrySet"))
                                    .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                    .astore(iteratorLocal);

                                int position2 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                        .checkcast(MapEntryDecl())
                                        .astore(entryLocal)
                                        .aload(entryLocal)
                                        .invoke(MapEntryDecl().requireDeclaredMethod("getKey"))
                                        .astore(newEntryKeyLocal)
                                        .aload(oldValueLocal)
                                        .aload(newEntryKeyLocal)
                                        .invoke(MapDecl().requireDeclaredMethod("containsKey", ObjectDecl()))
                                        .ifeq(() -> code
                                            .aload(addRemoveChangeLocal)
                                            .aload(newEntryKeyLocal)
                                            .aload(entryLocal)
                                            .invoke(MapEntryDecl().requireDeclaredMethod("getValue"))
                                            .invoke(addRemoveChangeType.requireDeclaredMethod(
                                                MapAddRemoveChangeGenerator.INIT_ADD_METHOD_NAME, ObjectDecl(), ObjectDecl()))
                                            .aload(0)
                                            .getfield(mapChangeListenerField)
                                            .aload(addRemoveChangeLocal)
                                            .invoke(MapChangeListenerDecl().requireDeclaredMethod(
                                                "onChanged", MapChangeListenerChangeDecl()))
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

        method.setCode(code)
              .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    private void createOnChangedMethod(BytecodeEmitContext context) {
        var method = createMethod("onChanged", voidDecl(), MapChangeListenerChangeDecl());
        var code = new Bytecode(method);

        FieldDeclaration rootRefField = requireDeclaredField(ROOT_REF);
        FieldDeclaration invalidationListenerField = requireDeclaredField(INVALIDATION_LISTENER_FIELD);
        FieldDeclaration changeListenerField = requireDeclaredField(CHANGE_LISTENER_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        FieldDeclaration mapChangeListenerField = requireDeclaredField(MAP_CHANGE_LISTENER_FIELD);
        FieldDeclaration adapterChangeField = requireDeclaredField(ADAPTER_CHANGE_FIELD);
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);

        if (context.isGeneratorActive(ReferenceTrackerGenerator.class)) {
            code.aload(0)
                .getfield(rootRefField)
                .invoke(context.getMarkupClass()
                               .requireDeclaredMethod(ReferenceTrackerGenerator.CLEAR_STALE_REFERENCES_METHOD));
        }

        code.aload(0)
            .getfield(invalidationListenerField)
            .ifnonnull(() -> code
                .aload(0)
                .getfield(invalidationListenerField)
                .aload(0)
                .invoke(InvalidationListenerDecl().requireDeclaredMethod("invalidated", ObservableDecl()))
            );

        code.aload(0)
            .getfield(changeListenerField)
            .ifnonnull(() -> code
                .aload(0)
                .getfield(changeListenerField)
                .aload(0)
                .aload(0)
                .getfield(valueField)
                .aload(0)
                .getfield(valueField)
                .invoke(ChangeListenerDecl().requireDeclaredMethod(
                    "changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl()))
            );

        code.aload(0)
            .getfield(mapChangeListenerField)
            .ifnonnull(() -> code
                .aload(0)
                .getfield(adapterChangeField)
                .aload(1)
                .invoke(adapterChangeType.requireDeclaredMethod(
                    MapSourceAdapterChangeGenerator.INIT_CHANGE_METHOD_NAME, MapChangeListenerChangeDecl()))
                .aload(0)
                .getfield(mapChangeListenerField)
                .aload(0)
                .getfield(adapterChangeField)
                .invoke(MapChangeListenerDecl().requireDeclaredMethod(
                    "onChanged", MapChangeListenerChangeDecl()))
            );

        code.vreturn();

        method.setCode(code)
              .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    private void createConstructor(BytecodeEmitContext context) {
        var constructor = createConstructor(context.getMarkupClass(), ObservableValueDecl());
        var code = new Bytecode(constructor);

        FieldDeclaration rootRefField = requireDeclaredField(ROOT_REF);
        FieldDeclaration observableField = requireDeclaredField(OBSERVABLE_FIELD);
        FieldDeclaration weakMapChangeListenerField = requireDeclaredField(WEAK_MAP_CHANGE_LISTENER_FIELD);
        FieldDeclaration adapterChangeField = requireDeclaredField(ADAPTER_CHANGE_FIELD);
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(MapSourceAdapterChangeGenerator.CLASS_NAME);

        // super()
        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor());

        // markupRef = $1
        code.aload(0)
            .aload(1)
            .putfield(rootRefField);

        // observable = $2
        code.aload(0)
            .aload(2)
            .putfield(observableField);

        // weakMapChangeListener = new WeakMapChangeListener(this);
        code.aload(0)
            .anew(WeakMapChangeListenerDecl())
            .dup()
            .aload(0)
            .invoke(WeakMapChangeListenerDecl().requireConstructor(MapChangeListenerDecl()))
            .putfield(weakMapChangeListenerField);

        // mapAdapterChange = new MapAdapterChange(this);
        code.aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invoke(adapterChangeType.requireDeclaredConstructor(ObservableMapDecl()))
            .putfield(adapterChangeField);

        // $1.addListener(new WeakInvalidationListener(this));
        code.aload(2)
            .anew(WeakInvalidationListenerDecl())
            .dup()
            .aload(0)
            .invoke(WeakInvalidationListenerDecl().requireConstructor(InvalidationListenerDecl()))
            .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl()));

        code.aload(0)
            .invoke(ObservableObjectValueDecl().requireDeclaredMethod("get"))
            .pop()
            .vreturn();

        constructor.setCode(code)
                   .setModifiers(Modifier.PUBLIC);
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
    private void createDelegateMethod(TypeDeclaration retType,
                                      TypeDeclaration delegateType,
                                      String methodName,
                                      TypeDeclaration... params) {
        boolean delegatesToObservableMap = delegateType.subtypeOf(ObservableMapDecl());

        createBehavior(
            createMethod(methodName, retType, params),
            code -> {
                Local mapLocal = code.acquireLocal(false);
                Local retLocal = code.acquireLocal(retType);

                Runnable branch = () -> {
                    int slot = 1;
                    for (TypeDeclaration param : params) {
                        code.load(param, slot);
                        slot += param.slots();
                    }

                    code.invoke(delegateType.requireDeclaredMethod(methodName, params));

                    if (!retType.equals(voidDecl())) {
                        code.store(retType, retLocal);
                    }
                };

                code.aload(0)
                    .invoke(ObservableObjectValueDecl().requireDeclaredMethod("get"))
                    .checkcast(ObservableMapDecl())
                    .astore(mapLocal)
                    .aload(mapLocal)
                    .ifnull(
                        () -> {
                            if (delegatesToObservableMap) {
                                code.invoke(FXCollectionsDecl().requireDeclaredMethod("emptyObservableMap"));
                            } else {
                                code.invoke(CollectionsDecl().requireDeclaredMethod("emptyMap"));
                            }
                            branch.run();
                        },
                        () -> {
                            code.aload(mapLocal);
                            branch.run();
                        });

                if (retType.equals(voidDecl())) {
                    code.vreturn();
                } else {
                    code.load(retType, retLocal);

                    if (retType.equals(longDecl())) {
                        code.lreturn();
                    } else if (retType.equals(booleanDecl()) || retType.isIntegralPrimitive()) {
                        code.ireturn();
                    } else if (retType.equals(doubleDecl())) {
                        code.dreturn();
                    } else if (retType.equals(floatDecl())) {
                        code.freturn();
                    } else {
                        code.areturn();
                    }
                }

                code.releaseLocal(mapLocal);
                code.releaseLocal(retLocal);
            }
        );
    }
}
