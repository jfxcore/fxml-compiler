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

public class MapReseatableSourceWrapperGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("MapReseatableSourceWrapper");

    private static final String SOURCE_FIELD = "source";
    private static final String TARGET_FIELD = "target";
    private static final String CURRENT_FIELD = "current";

    private final TypeInstance observableType;

    public MapReseatableSourceWrapperGenerator() {
        observableType = new TypeInvoker(SourceInfo.none()).invokeType(ObservableMapValueDecl());
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
            .setSuperClass(AbstractMapDecl())
            .addInterface(InvalidationListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(SOURCE_FIELD, ObservableValueDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(TARGET_FIELD, ObservableMapDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(CURRENT_FIELD, MapDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        createConstructor();
        createInvalidatedMethod();
        createDelegateMethod(intDecl(), "size", false);
        createDelegateMethod(ObjectDecl(), "get", false, ObjectDecl());
        createDelegateMethod(ObjectDecl(), "put", true, ObjectDecl(), ObjectDecl());
        createDelegateMethod(ObjectDecl(), "remove", true, ObjectDecl());
        createDelegateMethod(voidDecl(), "putAll", true, MapDecl());
        createDelegateMethod(voidDecl(), "clear", true);
    }

    private void createConstructor() {
        var constructor = createConstructor(ObservableMapDecl(), ObservableValueDecl());
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
            .checkcast(MapDecl())
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
        Local oldMap = code.acquireLocal(false);
        Local newMap = code.acquireLocal(false);

        // oldMap = current
        code.aload(0)
            .getfield(currentField)
            .astore(oldMap);

        // newMap = source.getValue()
        code.aload(0)
            .getfield(sourceField)
            .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
            .checkcast(MapDecl())
            .astore(newMap);

        // if (oldMap == newMap) return
        code.aload(oldMap)
            .aload(newMap)
            .if_acmpeq(code::vreturn);

        // current = newMap
        code.aload(0)
            .aload(newMap)
            .putfield(currentField);

        // if (newMap == null) return
        code.aload(newMap)
            .ifnull(code::vreturn);

        // if (newMap == target) return
        code.aload(newMap)
            .aload(0)
            .getfield(targetField)
            .if_acmpeq(code::vreturn);

        // newMap.clear();
        // newMap.putAll(target);
        code.aload(newMap)
            .invoke(MapDecl().requireDeclaredMethod("clear"))
            .aload(newMap)
            .aload(0)
            .getfield(targetField)
            .invoke(MapDecl().requireDeclaredMethod("putAll", MapDecl()))
            .vreturn();

        code.releaseLocal(oldMap);
        code.releaseLocal(newMap);
        method.setCode(code);
    }

    private void createDelegateMethod(TypeDeclaration retType,
                                      String methodName,
                                      boolean setter,
                                      TypeDeclaration... params) {
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

                    code.invoke(MapDecl().requireDeclaredMethod(methodName, params));

                    if (!retType.equals(voidDecl())) {
                        code.store(retType, retLocal);
                    }
                };

                code.aload(0)
                    .getfield(requireDeclaredField(CURRENT_FIELD))
                    .astore(mapLocal)
                    .aload(mapLocal);

                if (setter) {
                    code.ifnonnull(
                        () -> {
                            code.aload(mapLocal);
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
                        () -> code.aload(mapLocal),
                        () -> code.invoke(CollectionsDecl().requireDeclaredMethod("emptyMap")));

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

                code.releaseLocal(mapLocal);
                code.releaseLocal(retLocal);
            }
        );
    }
}

