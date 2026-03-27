// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class SetAddRemoveChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("SetAddRemoveChange");
    public static final String INIT_METHOD_NAME = "initChange";

    private static final String VALUE_FIELD = "value";
    private static final String WAS_ADDED_FIELD = "added";

    private ConstructorDeclaration constructor;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return new TypeInvoker(SourceInfo.none()).invokeType(SetChangeListenerChangeDecl());
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            .setSuperClass(SetChangeListenerChangeDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        generatedClass.createField(VALUE_FIELD, ObjectDecl()).setModifiers(Modifier.PRIVATE);
        generatedClass.createField(WAS_ADDED_FIELD, booleanDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(ObservableSetDecl());
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor(constructor);

        createBehavior(
            createMethod(INIT_METHOD_NAME, voidDecl(), ObjectDecl(), booleanDecl()), code -> code
                .aload(0)
                .aload(1)
                .putfield(requireDeclaredField(VALUE_FIELD))
                .aload(0)
                .iload(2)
                .putfield(requireDeclaredField(WAS_ADDED_FIELD))
                .vreturn());

        createBehavior(
            createMethod("wasAdded", booleanDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(WAS_ADDED_FIELD))
                .ireturn());

        createBehavior(
            createMethod("wasRemoved", booleanDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(WAS_ADDED_FIELD))
                .iconst(1)
                .ixor()
                .ireturn());

        createBehavior(
            createMethod("getElementAdded", ObjectDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(WAS_ADDED_FIELD))
                .ifeq(
                    code::aconst_null,
                    () -> code
                        .aload(0)
                        .getfield(requireDeclaredField(VALUE_FIELD)))
                .areturn());

        createBehavior(
            createMethod("getElementRemoved", ObjectDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(WAS_ADDED_FIELD))
                .iconst(1)
                .ixor()
                .ifeq(
                    code::aconst_null,
                    () -> code
                        .aload(0)
                        .getfield(requireDeclaredField(VALUE_FIELD)))
                .areturn());
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        createBehavior(constructor, code -> code
            .aload(0)
            .aload(1)
            .invoke(requireSuperClass().requireConstructor(ObservableSetDecl()))
            .vreturn());
    }
}
