// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class ListReseatableSourceWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("ListReseatableSourceWrapper");

    private static final String SOURCE_FIELD = "source";
    private static final String TARGET_FIELD = "target";
    private static final String CURRENT_FIELD = "current";

    private final TypeInstance observableType;

    public ListReseatableSourceWrapperGenerator() {
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableListValueDecl());
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
            .setSuperClass(AbstractListDecl())
            .addInterface(InvalidationListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(SOURCE_FIELD, ObservableValueDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(TARGET_FIELD, ObservableListDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(CURRENT_FIELD, ListDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        createConstructor();
        createInvalidatedMethod();
        createDelegateMethod(intDecl(), "size", false);
        createDelegateMethod(ObjectDecl(), "get", false, intDecl());
        createDelegateMethod(ObjectDecl(), "set", true, intDecl(), ObjectDecl());
        createDelegateMethod(voidDecl(), "add", true, intDecl(), ObjectDecl());
        createDelegateMethod(ObjectDecl(), "remove", true, intDecl());
        createDelegateMethod(voidDecl(), "clear", true);
        createDelegateMethod(booleanDecl(), "addAll", true, CollectionDecl());
        createDelegateMethod(booleanDecl(), "addAll", true, intDecl(), CollectionDecl());
        createDelegateMethod(booleanDecl(), "removeAll", true, CollectionDecl());
        createDelegateMethod(booleanDecl(), "retainAll", true, CollectionDecl());
    }

    private void createConstructor() {
        var constructor = createConstructor(ObservableListDecl(), ObservableValueDecl());
        var code = new Bytecode(constructor);

        FieldDeclaration targetField = requireDeclaredField(TARGET_FIELD);
        FieldDeclaration sourceField = requireDeclaredField(SOURCE_FIELD);
        FieldDeclaration currentField = requireDeclaredField(CURRENT_FIELD);

        // super()
        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor());

        // target = $1
        code.aload(0)
            .aload(1)
            .putfield(targetField);

        // source = $2
        code.aload(0)
            .aload(2)
            .putfield(sourceField);

        // current = source.getValue();
        code.aload(0)
            .aload(2)
            .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
            .checkcast(ListDecl())
            .putfield(currentField);

        // source.addListener(this);
        code.aload(2)
            .aload(0)
            .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl()))
            .vreturn();

        constructor.setCode(code);
    }

    private void createInvalidatedMethod() {
        var method = createMethod("invalidated", voidDecl(), ObservableDecl()).setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        var code = new Bytecode(method);

        FieldDeclaration targetField = requireDeclaredField(TARGET_FIELD);
        FieldDeclaration sourceField = requireDeclaredField(SOURCE_FIELD);
        FieldDeclaration currentField = requireDeclaredField(CURRENT_FIELD);
        Local oldList = code.acquireLocal(false);
        Local newList = code.acquireLocal(false);

        // oldList = current
        code.aload(0)
            .getfield(currentField)
            .astore(oldList);

        // newList = source.getValue()
        code.aload(0)
            .getfield(sourceField)
            .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
            .checkcast(ListDecl())
            .astore(newList);

        // if (oldList == newList) return
        code.aload(oldList)
            .aload(newList)
            .if_acmpeq(code::vreturn);

        // current = newList
        code.aload(0)
            .aload(newList)
            .putfield(currentField);

        // if (newList == null) return
        code.aload(newList)
            .ifnull(code::vreturn);

        // if (newList == target) return
        code.aload(newList)
            .aload(0)
            .getfield(targetField)
            .if_acmpeq(code::vreturn);

        // if (newList instanceof ObservableList list) {
        //     list.setAll(target)
        // } else {
        //     newList.clear();
        //     newList.addAll(target);
        // }
        code.aload(newList)
            .isinstanceof(ObservableListDecl())
            .ifne(() -> code
                .aload(newList)
                .checkcast(ObservableListDecl())
                .aload(0)
                .getfield(targetField)
                .invoke(ObservableListDecl().requireDeclaredMethod("setAll", CollectionDecl())),
            /* else */ () -> code
                .aload(newList)
                .invoke(ListDecl().requireDeclaredMethod("clear"))
                .aload(newList)
                .aload(0)
                .getfield(targetField)
                .invoke(ListDecl().requireDeclaredMethod("addAll", CollectionDecl()))
            )
            .vreturn();

        code.releaseLocal(oldList);
        code.releaseLocal(newList);
        method.setCode(code);
    }

    private void createDelegateMethod(TypeDeclaration retType,
                                      String methodName,
                                      boolean setter,
                                      TypeDeclaration... params) {
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

                    code.invoke(ListDecl().requireDeclaredMethod(methodName, params));

                    if (!retType.equals(voidDecl())) {
                        code.store(retType, retLocal);
                    }
                };

                code.aload(0)
                    .getfield(requireDeclaredField(CURRENT_FIELD))
                    .astore(listLocal)
                    .aload(listLocal);

                if (setter) {
                    code.ifnonnull(
                        () -> {
                            code.aload(listLocal);
                            branch.run();
                        },
                        () -> {
                            if (!retType.equals(voidDecl())) {
                                code.defaultconst(retType)
                                    .store(retType, retLocal);
                            }
                        });
                } else {
                    code.ifnonnull(
                        () -> code.aload(listLocal),
                        () -> code.invoke(CollectionsDecl().requireDeclaredMethod("emptyList")));

                    branch.run();
                }

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
