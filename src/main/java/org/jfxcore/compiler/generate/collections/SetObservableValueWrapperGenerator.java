// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import javafx.beans.value.ObservableSetValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.ReferenceTrackerGenerator;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.Types.*;

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
    private static final String ROOT_REF = "root";
    private static final String VALUE_FIELD = "value";
    private static final String ADAPTER_CHANGE_FIELD = "change";
    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";
    private static final String SET_CHANGE_LISTENER_FIELD = "setChangeListener";

    private final TypeInstance observableType;

    public SetObservableValueWrapperGenerator() {
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableSetValueDecl());
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
    public final TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            .addInterface(ObservableSetValueDecl())
            .addInterface(SetChangeListenerDecl())
            .addInterface(InvalidationListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(SetSourceAdapterChangeGenerator.CLASS_NAME);
        createField(ROOT_REF, context.getMarkupClass()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(OBSERVABLE_FIELD, ObservableValueDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(WEAK_SET_CHANGE_LISTENER_FIELD, WeakSetChangeListenerDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(ADAPTER_CHANGE_FIELD, adapterChangeType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(VALUE_FIELD, ObservableSetDecl()).setModifiers(Modifier.PRIVATE);
        createField(VALID_FIELD, booleanDecl()).setModifiers(Modifier.PRIVATE);
        createField(INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(CHANGE_LISTENER_FIELD, ChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(SET_CHANGE_LISTENER_FIELD, SetChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        createConstructor(context);
        createSetMethods();
        createGetValueMethod();
        createGetMethod();
        createInvalidatedMethod(context);
        createOnChangedMethod(context);

        createListenerMethods(this, INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl());
        createListenerMethods(this, CHANGE_LISTENER_FIELD, ChangeListenerDecl());
        createListenerMethods(this, SET_CHANGE_LISTENER_FIELD, SetChangeListenerDecl());
    }

    private void createSetMethods() {
        TypeDeclaration arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        TypeDeclaration delegateType = SetDecl();

        createDelegateMethod(intDecl(), delegateType, "size");
        createDelegateMethod(booleanDecl(), delegateType, "isEmpty");
        createDelegateMethod(booleanDecl(), delegateType, "contains", ObjectDecl());
        createDelegateMethod(IteratorDecl(), delegateType, "iterator");
        createDelegateMethod(arrayType, delegateType, "toArray");
        createDelegateMethod(arrayType, delegateType, "toArray", arrayType);
        createDelegateMethod(booleanDecl(), delegateType, "add", ObjectDecl());
        createDelegateMethod(booleanDecl(), delegateType, "remove", ObjectDecl());
        createDelegateMethod(booleanDecl(), delegateType, "containsAll", CollectionDecl());
        createDelegateMethod(booleanDecl(), delegateType, "addAll", CollectionDecl());
        createDelegateMethod(booleanDecl(), delegateType, "removeAll", CollectionDecl());
        createDelegateMethod(booleanDecl(), delegateType, "retainAll", CollectionDecl());
        createDelegateMethod(voidDecl(), delegateType, "clear");
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

        Local setLocal = code.acquireLocal(false);
        FieldDeclaration validField = requireDeclaredField(VALID_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        FieldDeclaration weakSetChangeListenerField = requireDeclaredField(WEAK_SET_CHANGE_LISTENER_FIELD);
        FieldDeclaration observableField = requireDeclaredField(OBSERVABLE_FIELD);

        code.aload(0)
            .getfield(validField)
            .ifeq(() -> {
                // if (this.set != null)
                code.aload(0)
                    .getfield(valueField)
                    .ifnonnull(() -> code
                        // this.set.removeListener(weakSetChangeListener);
                        .aload(0)
                        .getfield(valueField)
                        .aload(0)
                        .getfield(weakSetChangeListenerField)
                        .invoke(ObservableSetDecl().requireDeclaredMethod("removeListener", SetChangeListenerDecl()))
                    );

                // valid = true;
                // Set set = source.getValue();
                code.aload(0)
                    .iconst(1)
                    .putfield(validField)
                    .aload(0)
                    .getfield(observableField)
                    .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                    .astore(setLocal);

                // if (set instanceof ObservableSet)
                code.aload(setLocal)
                    .isinstanceof(ObservableSetDecl())
                    .ifne(() -> code
                        // this.set = (ObservableSet)set;
                        // this.set.addListener(weakSetChangeListener);
                        .aload(0)
                        .aload(setLocal)
                        .checkcast(ObservableSetDecl())
                        .putfield(valueField)
                        .aload(0)
                        .getfield(valueField)
                        .aload(0)
                        .getfield(weakSetChangeListenerField)
                        .invoke(ObservableSetDecl().requireDeclaredMethod("addListener", SetChangeListenerDecl())),
                    /*else*/ () -> code
                        // else if (set != null)
                        .aload(setLocal)
                        .ifnonnull(() -> code
                            // this.set = FXCollections.observableSet(set);
                            .aload(0)
                            .aload(setLocal)
                            .invoke(FXCollectionsDecl().requireDeclaredMethod("observableSet", SetDecl()))
                            .putfield(valueField),
                        /*else*/ () -> code
                            // else this.set = null;
                            .aload(0)
                            .aconst_null()
                            .putfield(valueField)
                        )
                    );
            })
            .aload(0)
            .getfield(valueField)
            .areturn()
            .releaseLocal(setLocal);

        method.setCode(code)
              .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    /**
     * The {@code invalidated} method is called when the value of the ObservableValue changes,
     * i.e. when the set wrapped by the ObservableValue is replaced entirely.
     */
    private void createInvalidatedMethod(BytecodeEmitContext context) {
        var method = createMethod("invalidated", voidDecl(), ObservableDecl());
        var code = new Bytecode(method);

        FieldDeclaration rootRefField = requireDeclaredField(ROOT_REF);
        FieldDeclaration validField = requireDeclaredField(VALID_FIELD);
        FieldDeclaration invalidationListenerField = requireDeclaredField(INVALIDATION_LISTENER_FIELD);
        FieldDeclaration changeListenerField = requireDeclaredField(CHANGE_LISTENER_FIELD);
        FieldDeclaration setChangeListenerField = requireDeclaredField(SET_CHANGE_LISTENER_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        Local oldValueLocal = code.acquireLocal(false);
        Local currentValueLocal = code.acquireLocal(false);
        Local iteratorLocal = code.acquireLocal(false);
        Local elementLocal = code.acquireLocal(false);
        Local addRemoveChangeLocal = code.acquireLocal(false);
        TypeDeclaration addRemoveChangeType = context.getNestedClasses().find(SetAddRemoveChangeGenerator.CLASS_NAME);

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

        // if (changeListener != null || setChangeListener != null)
        code.aload(0)
            .getfield(changeListenerField)
            .ifnonnull(
                () -> code.iconst(1),
                () -> code
                    .aload(0)
                    .getfield(setChangeListenerField)
                    .ifnonnull(
                        () -> code.iconst(1),
                        () -> code.iconst(0)))
            .ifne(() -> code
                // ObservableSet oldValue = this.value;
                // ObservableSet currentValue = this.get();
                .aload(0)
                .getfield(valueField)
                .astore(oldValueLocal)
                .aload(0)
                .invoke(ObservableObjectValueDecl().requireDeclaredMethod("get"))
                .checkcast(ObservableSetDecl())
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
                    // if (setChangeListener != null)
                    .aload(0)
                    .getfield(setChangeListenerField)
                    .ifnonnull(() -> code
                        // var change = new SetAddRemoveChange(this)
                        .anew(addRemoveChangeType)
                        .dup()
                        .aload(0)
                        .invoke(addRemoveChangeType.requireDeclaredConstructor(ObservableSetDecl()))
                        .astore(addRemoveChangeLocal)
                        // if (currentValue == null)
                        .aload(currentValueLocal)
                        .ifnull(() -> {
                            // for (E removed : oldValue) {
                            //     addRemoveChange.initChange(removed, false);
                            //     setChangeListener.onChanged(addRemoveChange);
                            // }
                            code.aload(oldValueLocal)
                                .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                .astore(iteratorLocal);

                            int position = code.position() + 1;

                            code.aload(iteratorLocal)
                                .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                .ifne(() -> code
                                    .aload(addRemoveChangeLocal)
                                    .aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                    .iconst(0)
                                    .invoke(addRemoveChangeType.requireDeclaredMethod(
                                        SetAddRemoveChangeGenerator.INIT_METHOD_NAME, ObjectDecl(), booleanDecl()))
                                    .aload(0)
                                    .getfield(setChangeListenerField)
                                    .aload(addRemoveChangeLocal)
                                    .invoke(SetChangeListenerDecl().requireDeclaredMethod(
                                        "onChanged", SetChangeListenerChangeDecl()))
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
                                    .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                    .astore(iteratorLocal);

                                int position = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                    .ifne(() -> code
                                        .aload(addRemoveChangeLocal)
                                        .aload(iteratorLocal)
                                        .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                        .iconst(1)
                                        .invoke(addRemoveChangeType.requireDeclaredMethod(
                                            SetAddRemoveChangeGenerator.INIT_METHOD_NAME, ObjectDecl(), booleanDecl()))
                                        .aload(0)
                                        .getfield(setChangeListenerField)
                                        .aload(addRemoveChangeLocal)
                                        .invoke(SetChangeListenerDecl().requireDeclaredMethod(
                                            "onChanged", SetChangeListenerChangeDecl()))
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
                                    .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                    .astore(iteratorLocal);

                                int position1 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                        .astore(elementLocal)
                                        .aload(currentValueLocal)
                                        .aload(elementLocal)
                                        .invoke(SetDecl().requireDeclaredMethod("contains", ObjectDecl()))
                                        .ifeq(() -> code
                                            .aload(addRemoveChangeLocal)
                                            .aload(elementLocal)
                                            .iconst(0)
                                            .invoke(addRemoveChangeType.requireDeclaredMethod(
                                                SetAddRemoveChangeGenerator.INIT_METHOD_NAME, ObjectDecl(), booleanDecl()))
                                            .aload(0)
                                            .getfield(setChangeListenerField)
                                            .aload(addRemoveChangeLocal)
                                            .invoke(SetChangeListenerDecl().requireDeclaredMethod(
                                                "onChanged", SetChangeListenerChangeDecl()))
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
                                    .invoke(SetDecl().requireDeclaredMethod("iterator"))
                                    .astore(iteratorLocal);

                                int position2 = code.position() + 1;

                                code.aload(iteratorLocal)
                                    .invoke(IteratorDecl().requireDeclaredMethod("hasNext"))
                                    .ifne(() -> code
                                        .aload(iteratorLocal)
                                        .invoke(IteratorDecl().requireDeclaredMethod("next"))
                                        .astore(elementLocal)
                                        .aload(oldValueLocal)
                                        .aload(elementLocal)
                                        .invoke(SetDecl().requireDeclaredMethod("contains", ObjectDecl()))
                                        .ifeq(() -> code
                                            .aload(addRemoveChangeLocal)
                                            .aload(elementLocal)
                                            .iconst(1)
                                            .invoke(addRemoveChangeType.requireDeclaredMethod(
                                                SetAddRemoveChangeGenerator.INIT_METHOD_NAME, ObjectDecl(), booleanDecl()))
                                            .aload(0)
                                            .getfield(setChangeListenerField)
                                            .aload(addRemoveChangeLocal)
                                            .invoke(SetChangeListenerDecl().requireDeclaredMethod(
                                                "onChanged", SetChangeListenerChangeDecl()))
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

        method.setCode(code)
              .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    private void createOnChangedMethod(BytecodeEmitContext context) {
        var method = createMethod("onChanged", voidDecl(), SetChangeListenerChangeDecl());
        var code = new Bytecode(method);

        FieldDeclaration rootRefField = requireDeclaredField(ROOT_REF);
        FieldDeclaration invalidationListenerField = requireDeclaredField(INVALIDATION_LISTENER_FIELD);
        FieldDeclaration changeListenerField = requireDeclaredField(CHANGE_LISTENER_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        FieldDeclaration setChangeListenerField = requireDeclaredField(SET_CHANGE_LISTENER_FIELD);
        FieldDeclaration adapterChangeField = requireDeclaredField(ADAPTER_CHANGE_FIELD);
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(SetSourceAdapterChangeGenerator.CLASS_NAME);

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
            .getfield(setChangeListenerField)
            .ifnonnull(() -> code
                .aload(0)
                .getfield(adapterChangeField)
                .aload(1)
                .invoke(adapterChangeType.requireDeclaredMethod(
                    SetSourceAdapterChangeGenerator.INIT_CHANGE_METHOD_NAME, SetChangeListenerChangeDecl()))
                .aload(0)
                .getfield(setChangeListenerField)
                .aload(0)
                .getfield(adapterChangeField)
                .invoke(SetChangeListenerDecl().requireDeclaredMethod(
                    "onChanged", SetChangeListenerChangeDecl()))
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
        FieldDeclaration weakSetChangeListenerField = requireDeclaredField(WEAK_SET_CHANGE_LISTENER_FIELD);
        FieldDeclaration adapterChangeField = requireDeclaredField(ADAPTER_CHANGE_FIELD);
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(SetSourceAdapterChangeGenerator.CLASS_NAME);

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

        // weakSetChangeListener = new WeakSetChangeListener(this);
        code.aload(0)
            .anew(WeakSetChangeListenerDecl())
            .dup()
            .aload(0)
            .invoke(WeakSetChangeListenerDecl().requireConstructor(SetChangeListenerDecl()))
            .putfield(weakSetChangeListenerField);

        // setAdapterChange = new SetAdapterChange(this);
        code.aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invoke(adapterChangeType.requireDeclaredConstructor(ObservableSetDecl()))
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
     * Creates a method that delegates the method call to an ObservableSet that is wrapped by the current object.
     * The implementation is similar to:
     * <pre>{@code
     *     public boolean add(E element) {
     *         ObservableSet<E> set = get();
     *         return set == null ? Collections.emptyList().add(element) : set.add(element);
     *     }
     * }</pre>
     */
    private void createDelegateMethod(TypeDeclaration retType,
                                      TypeDeclaration delegateType,
                                      String methodName,
                                      TypeDeclaration... params) {
        boolean delegatesToObservableSet = delegateType.subtypeOf(ObservableSetDecl());

        createBehavior(
            createMethod(methodName, retType, params),
            code -> {
                Local setLocal = code.acquireLocal(false);
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
                    .checkcast(ObservableSetDecl())
                    .astore(setLocal)
                    .aload(setLocal)
                    .ifnull(
                        () -> {
                            if (delegatesToObservableSet) {
                                code.invoke(FXCollectionsDecl().requireDeclaredMethod("emptyObservableSet"));
                            } else {
                                code.invoke(CollectionsDecl().requireDeclaredMethod("emptySet"));
                            }
                            branch.run();
                        },
                        () -> {
                            code.aload(setLocal);
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

                code.releaseLocal(setLocal);
                code.releaseLocal(retLocal);
            }
        );
    }
}
