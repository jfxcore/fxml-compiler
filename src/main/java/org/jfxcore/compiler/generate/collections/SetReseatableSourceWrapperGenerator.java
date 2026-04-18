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

public class SetReseatableSourceWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("SetReseatableSourceWrapper");

    private static final String SOURCE_FIELD = "source";
    private static final String TARGET_FIELD = "target";
    private static final String CURRENT_FIELD = "current";

    private final TypeInstance observableType;

    public SetReseatableSourceWrapperGenerator() {
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableSetValueDecl());
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
            .setSuperClass(AbstractSetDecl())
            .addInterface(InvalidationListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(SOURCE_FIELD, ObservableValueDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(TARGET_FIELD, ObservableSetDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(CURRENT_FIELD, SetDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        createConstructor();
        createInvalidatedMethod();
        createDelegateMethod(intDecl(), "size", false);
        createDelegateMethod(booleanDecl(), "add", true, ObjectDecl());
        createDelegateMethod(booleanDecl(), "remove", true, ObjectDecl());
        createDelegateMethod(booleanDecl(), "addAll", true, CollectionDecl());
        createDelegateMethod(booleanDecl(), "removeAll", true, CollectionDecl());
        createDelegateMethod(booleanDecl(), "retainAll", true, CollectionDecl());
        createDelegateMethod(voidDecl(), "clear", true);
    }

    private void createConstructor() {
        var constructor = createConstructor(ObservableSetDecl(), ObservableValueDecl());
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
            .checkcast(SetDecl())
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
        Local oldSet = code.acquireLocal(false);
        Local newSet = code.acquireLocal(false);

        // oldSet = current
        code.aload(0)
            .getfield(currentField)
            .astore(oldSet);

        // newSet = source.getValue()
        code.aload(0)
            .getfield(sourceField)
            .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
            .checkcast(SetDecl())
            .astore(newSet);

        // if (oldSet == newSet) return
        code.aload(oldSet)
            .aload(newSet)
            .if_acmpeq(code::vreturn);

        // current = newSet
        code.aload(0)
            .aload(newSet)
            .putfield(currentField);

        // if (newSet == null) return
        code.aload(newSet)
            .ifnull(code::vreturn);

        // if (newSet == target) return
        code.aload(newSet)
            .aload(0)
            .getfield(targetField)
            .if_acmpeq(code::vreturn);

        // newSet.clear();
        // newSet.addAll(target);
        code.aload(newSet)
            .invoke(SetDecl().requireDeclaredMethod("clear"))
            .aload(newSet)
            .aload(0)
            .getfield(targetField)
            .invoke(SetDecl().requireDeclaredMethod("addAll", CollectionDecl()))
            .vreturn();

        code.releaseLocal(oldSet);
        code.releaseLocal(newSet);
        method.setCode(code);
    }

    private void createDelegateMethod(TypeDeclaration retType,
                                      String methodName,
                                      boolean setter,
                                      TypeDeclaration... params) {
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

                    code.invoke(SetDecl().requireDeclaredMethod(methodName, params));

                    if (!retType.equals(voidDecl())) {
                        code.store(retType, retLocal);
                    }
                };

                code.aload(0)
                    .getfield(requireDeclaredField(CURRENT_FIELD))
                    .astore(setLocal)
                    .aload(setLocal);

                if (setter) {
                    code.ifnonnull(
                        () -> {
                            code.aload(setLocal);
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
                        () -> code.aload(setLocal),
                        () -> code.invoke(CollectionsDecl().requireDeclaredMethod("emptySet")));

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

                code.releaseLocal(setLocal);
                code.releaseLocal(retLocal);
            }
        );
    }
}

