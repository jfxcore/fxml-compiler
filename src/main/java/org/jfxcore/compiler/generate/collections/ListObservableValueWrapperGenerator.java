// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import javafx.beans.value.ObservableListValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
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

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.TypeSymbols.*;

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
    private static final String ROOT_REF = "root";
    private static final String VALUE_FIELD = "value";
    private static final String ADAPTER_CHANGE_FIELD = "change";
    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";
    private static final String LIST_CHANGE_LISTENER_FIELD = "listChangeListener";

    private final TypeInstance observableType;

    public ListObservableValueWrapperGenerator() {
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableListValueDecl());
    }

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(
            new ListAddRemoveChangeGenerator(),
            new ListSourceAdapterChangeGenerator());
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
            .addInterface(ObservableListValueDecl())
            .addInterface(ListChangeListenerDecl())
            .addInterface(InvalidationListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME);
        createField(ROOT_REF, context.getMarkupClass()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(OBSERVABLE_FIELD, ObservableValueDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(WEAK_LIST_CHANGE_LISTENER_FIELD, WeakListChangeListenerDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(ADAPTER_CHANGE_FIELD, adapterChangeType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(VALUE_FIELD, ObservableListDecl()).setModifiers(Modifier.PRIVATE);
        createField(VALID_FIELD, booleanDecl()).setModifiers(Modifier.PRIVATE);
        createField(INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(CHANGE_LISTENER_FIELD, ChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
        createField(LIST_CHANGE_LISTENER_FIELD, ListChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        createConstructor(context);
        createListMethods();
        createObservableListMethods();
        createObservableValueMethods();
        createInvalidatedMethod(context);
        createOnChangedMethod(context);
    }

    private void createListMethods() {
        TypeDeclaration arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        TypeDeclaration delegateType = ListDecl();

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
        createDelegateMethod(booleanDecl(), delegateType, "addAll", intDecl(), CollectionDecl());
        createDelegateMethod(booleanDecl(), delegateType, "removeAll", CollectionDecl());
        createDelegateMethod(booleanDecl(), delegateType, "retainAll", CollectionDecl());
        createDelegateMethod(voidDecl(), delegateType, "clear");
        createDelegateMethod(ObjectDecl(), delegateType, "get", intDecl());
        createDelegateMethod(ObjectDecl(), delegateType, "set", intDecl(), ObjectDecl());
        createDelegateMethod(voidDecl(), delegateType, "add", intDecl(), ObjectDecl());
        createDelegateMethod(ObjectDecl(), delegateType, "remove", intDecl());
        createDelegateMethod(intDecl(), delegateType, "indexOf", ObjectDecl());
        createDelegateMethod(intDecl(), delegateType, "lastIndexOf", ObjectDecl());
        createDelegateMethod(ListIteratorDecl(), delegateType, "listIterator");
        createDelegateMethod(ListIteratorDecl(), delegateType, "listIterator", intDecl());
        createDelegateMethod(ListDecl(), delegateType, "subList", intDecl(), intDecl());
    }

    private void createObservableListMethods() {
        TypeDeclaration arrayType = new Resolver(SourceInfo.none()).resolveClass("java.lang.Object[]");
        TypeDeclaration delegateType = ObservableListDecl();

        createDelegateMethod(booleanDecl(), delegateType, "addAll", arrayType);
        createDelegateMethod(booleanDecl(), delegateType, "setAll", arrayType);
        createDelegateMethod(booleanDecl(), delegateType, "setAll", CollectionDecl());
        createDelegateMethod(booleanDecl(), delegateType, "removeAll", arrayType);
        createDelegateMethod(booleanDecl(), delegateType, "retainAll", arrayType);
        createDelegateMethod(voidDecl(), delegateType, "remove", intDecl(), intDecl());
    }

    private void createObservableValueMethods() {
        createListenerMethods(this, INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl());
        createListenerMethods(this, CHANGE_LISTENER_FIELD, ChangeListenerDecl());
        createListenerMethods(this, LIST_CHANGE_LISTENER_FIELD, ListChangeListenerDecl());
        createGetMethod();
        createGetValueMethod();
    }

    private void createGetMethod() {
        var method = createMethod("get", ObjectDecl());
        var code = new Bytecode(method);

        Local listLocal = code.acquireLocal(false);
        FieldDeclaration validField = requireDeclaredField(VALID_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        FieldDeclaration weakListChangeListenerField = requireDeclaredField(WEAK_LIST_CHANGE_LISTENER_FIELD);
        FieldDeclaration observableField = requireDeclaredField(OBSERVABLE_FIELD);

        code.aload(0)
            .getfield(validField)
            .ifeq(() -> {
                // if (this.list != null)
                code.aload(0)
                    .getfield(valueField)
                    .ifnonnull(() -> code
                        // this.list.removeListener(weakListChangeListener);
                        .aload(0)
                        .getfield(valueField)
                        .aload(0)
                        .getfield(weakListChangeListenerField)
                        .invoke(ObservableListDecl().requireDeclaredMethod("removeListener", ListChangeListenerDecl()))
                    );

                // valid = true;
                // List list = source.getValue();
                code.aload(0)
                    .iconst(1)
                    .putfield(validField)
                    .aload(0)
                    .getfield(observableField)
                    .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                    .astore(listLocal);

                // if (list instanceof ObservableList)
                code.aload(listLocal)
                    .isinstanceof(ObservableListDecl())
                    .ifne(() -> code
                        // this.list = (ObservableList)list;
                        // this.list.addListener(weakListChangeListener);
                        .aload(0)
                        .aload(listLocal)
                        .checkcast(ObservableListDecl())
                        .putfield(valueField)
                        .aload(0)
                        .getfield(valueField)
                        .aload(0)
                        .getfield(weakListChangeListenerField)
                        .invoke(ObservableListDecl().requireDeclaredMethod("addListener", ListChangeListenerDecl())),
                    /*else*/ () -> code
                        // else if (list != null)
                        .aload(listLocal)
                        .ifnonnull(() -> code
                            // this.list = FXCollections.observableList(list);
                            .aload(0)
                            .aload(listLocal)
                            .invoke(FXCollectionsDecl().requireDeclaredMethod("observableList", ListDecl()))
                            .putfield(valueField),
                        /*else*/ () -> code
                            // else this.list = null;
                            .aload(0)
                            .aconst_null()
                            .putfield(valueField)
                        )
                    );
            })
            .aload(0)
            .getfield(valueField)
            .areturn()
            .releaseLocal(listLocal);

        method.setCode(code)
              .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    private void createGetValueMethod() {
        createBehavior(createMethod("getValue", ObjectDecl()), code -> code
            .aload(0)
            .invoke(ObservableObjectValueDecl().requireDeclaredMethod("get"))
            .areturn());
    }

    private void createInvalidatedMethod(BytecodeEmitContext context) {
        var method = createMethod("invalidated", voidDecl(), ObservableDecl());
        var code = new Bytecode(method);

        FieldDeclaration rootRefField = requireDeclaredField(ROOT_REF);
        FieldDeclaration validField = requireDeclaredField(VALID_FIELD);
        FieldDeclaration invalidationListenerField = requireDeclaredField(INVALIDATION_LISTENER_FIELD);
        FieldDeclaration changeListenerField = requireDeclaredField(CHANGE_LISTENER_FIELD);
        FieldDeclaration listChangeListenerField = requireDeclaredField(LIST_CHANGE_LISTENER_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        TypeDeclaration changeType = context.getNestedClasses().find(ListAddRemoveChangeGenerator.CLASS_NAME);
        Local oldValueLocal = code.acquireLocal(false);
        Local currentValueLocal = code.acquireLocal(false);
        Local safeSizeLocal = code.acquireLocal(false);
        Local safeOldValueLocal = code.acquireLocal(false);

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

        // if (changeListener != null || listChangeListener != null)
        code.aload(0)
            .getfield(changeListenerField)
            .ifnonnull(
                () -> code.iconst(1),
                () -> code
                    .aload(0)
                    .getfield(listChangeListenerField)
                    .ifnonnull(
                        () -> code.iconst(1),
                        () -> code.iconst(0)))
            .ifne(() -> code
                // ObservableList oldValue = this.value;
                // ObservableList currentValue = this.get();
                .aload(0)
                .getfield(valueField)
                .astore(oldValueLocal)
                .aload(0)
                .invoke(ObservableObjectValueDecl().requireDeclaredMethod("get"))
                .checkcast(ObservableListDecl())
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
                    // if (listChangeListener != null)
                    .aload(0)
                    .getfield(listChangeListenerField)
                    .ifnonnull(() -> code
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
                                    .invoke(ListDecl().requireDeclaredMethod("size"))
                                    .istore(safeSizeLocal)
                                );

                            // if (oldValue == null)
                            code.aload(oldValueLocal)
                                .ifnull(() -> code
                                    // safeOldValue = FXCollections.emptyObservableList();
                                    .invoke(FXCollectionsDecl().requireDeclaredMethod("emptyObservableList"))
                                    .astore(safeOldValueLocal),
                                /*else*/ () -> code
                                    // safeOldValue = FXCollections.unmodifiableObservableList(oldValue);
                                    .aload(oldValueLocal)
                                    .invoke(FXCollectionsDecl().requireDeclaredMethod("unmodifiableObservableList", ObservableListDecl()))
                                    .astore(safeOldValueLocal)
                                );

                            // listChangeListener.onChanged(new AddRemoveChange(0, safeSize, safeOldValue, this));
                            code.aload(0)
                                .getfield(listChangeListenerField)
                                .anew(changeType)
                                .dup()
                                .iconst(0)
                                .iload(safeSizeLocal)
                                .aload(safeOldValueLocal)
                                .aload(0)
                                .invoke(changeType.requireDeclaredConstructor(intDecl(), intDecl(), ListDecl(), ObservableListDecl()))
                                .invoke(ListChangeListenerDecl().requireDeclaredMethod("onChanged", ListChangeListenerChangeDecl()));
                        })
                    )
                )
            );

        code.releaseLocal(oldValueLocal);
        code.releaseLocal(currentValueLocal);
        code.releaseLocal(safeSizeLocal);
        code.releaseLocal(safeOldValueLocal);
        code.vreturn();

        method.setCode(code)
              .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    private void createOnChangedMethod(BytecodeEmitContext context) {
        var method = createMethod("onChanged", voidDecl(), ListChangeListenerChangeDecl());
        var code = new Bytecode(method);

        FieldDeclaration rootRefField = requireDeclaredField(ROOT_REF);
        FieldDeclaration invalidationListenerField = requireDeclaredField(INVALIDATION_LISTENER_FIELD);
        FieldDeclaration changeListenerField = requireDeclaredField(CHANGE_LISTENER_FIELD);
        FieldDeclaration valueField = requireDeclaredField(VALUE_FIELD);
        FieldDeclaration listChangeListenerField = requireDeclaredField(LIST_CHANGE_LISTENER_FIELD);
        FieldDeclaration adapterChangeField = requireDeclaredField(ADAPTER_CHANGE_FIELD);
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME);

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
                .invoke(ChangeListenerDecl().requireDeclaredMethod("changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl()))
            );

        code.aload(0)
            .getfield(listChangeListenerField)
            .ifnonnull(() -> code
                .aload(0)
                .getfield(adapterChangeField)
                .aload(1)
                .invoke(adapterChangeType.requireDeclaredMethod(ListSourceAdapterChangeGenerator.INIT_CHANGE_METHOD_NAME, ListChangeListenerChangeDecl()))
                .aload(0)
                .getfield(listChangeListenerField)
                .aload(0)
                .getfield(adapterChangeField)
                .invoke(ListChangeListenerDecl().requireDeclaredMethod("onChanged", ListChangeListenerChangeDecl()))
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
        FieldDeclaration weakListChangeListenerField = requireDeclaredField(WEAK_LIST_CHANGE_LISTENER_FIELD);
        FieldDeclaration adapterChangeField = requireDeclaredField(ADAPTER_CHANGE_FIELD);
        TypeDeclaration adapterChangeType = context.getNestedClasses().find(ListSourceAdapterChangeGenerator.CLASS_NAME);

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

        // weakListChangeListener = new WeakListChangeListener(this);
        code.aload(0)
            .anew(WeakListChangeListenerDecl())
            .dup()
            .aload(0)
            .invoke(WeakListChangeListenerDecl().requireConstructor(ListChangeListenerDecl()))
            .putfield(weakListChangeListenerField);

        code.aload(0)
            .anew(adapterChangeType)
            .dup()
            .aload(0)
            .invoke(adapterChangeType.requireDeclaredConstructor(ObservableListDecl()))
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
     * Creates a method that delegates the method call to an ObservableList that is wrapped by the current object.
     * The implementation is similar to:
     * <pre>{@code
     *     public boolean add(E element) {
     *         ObservableList<E> list = get();
     *         return list == null ? Collections.emptyList().add(element) : list.add(element);
     *     }
     * }</pre>
     */
    private void createDelegateMethod(TypeDeclaration retType,
                                      TypeDeclaration delegateType,
                                      String methodName,
                                      TypeDeclaration... params) {
        boolean delegatesToObservableList = delegateType.subtypeOf(ObservableListDecl());

        createBehavior(
            createMethod(methodName, retType, params),
            code -> {
                Local listLocal = code.acquireLocal(false);
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
                    .checkcast(ObservableListDecl())
                    .astore(listLocal)
                    .aload(listLocal)
                    .ifnull(
                        () -> {
                            if (delegatesToObservableList) {
                                code.invoke(FXCollectionsDecl().requireDeclaredMethod("emptyObservableList"));
                            } else {
                                code.invoke(CollectionsDecl().requireDeclaredMethod("emptyList"));
                            }
                            branch.run();
                        },
                        () -> {
                            code.aload(listLocal);
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

                code.releaseLocal(listLocal);
                code.releaseLocal(retLocal);
            }
        );
    }
}
