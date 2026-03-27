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
import static org.jfxcore.compiler.type.TypeSymbols.*;

public class MapAddRemoveChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("MapAddRemoveChange");
    public static final String INIT_ADD_METHOD_NAME = "initAdd";
    public static final String INIT_REMOVE_METHOD_NAME = "initRemove";
    public static final String INIT_REPLACE_METHOD_NAME = "initReplace";

    private static final String KEY_FIELD = "key";
    private static final String ADDED_FIELD = "added";
    private static final String REMOVED_FIELD = "removed";
    private static final String WAS_ADDED_FIELD = "wasAdded";
    private static final String WAS_REMOVED_FIELD = "wasRemoved";

    private ConstructorDeclaration constructor;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return new TypeInvoker(SourceInfo.none()).invokeType(MapChangeListenerChangeDecl());
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            .setSuperClass(MapChangeListenerChangeDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(KEY_FIELD, ObjectDecl()).setModifiers(Modifier.PRIVATE);
        createField(ADDED_FIELD, ObjectDecl()).setModifiers(Modifier.PRIVATE);
        createField(REMOVED_FIELD, ObjectDecl()).setModifiers(Modifier.PRIVATE);
        createField(WAS_ADDED_FIELD, booleanDecl()).setModifiers(Modifier.PRIVATE);
        createField(WAS_REMOVED_FIELD, booleanDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(ObservableMapDecl());
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor(constructor);

        createBehavior(
            createMethod(INIT_ADD_METHOD_NAME, voidDecl(), ObjectDecl(), ObjectDecl()),
            code -> code
                .aload(0)
                .aload(1)
                .putfield(requireDeclaredField(KEY_FIELD))
                .aload(0)
                .aload(2)
                .putfield(requireDeclaredField(ADDED_FIELD))
                .aload(0)
                .aconst_null()
                .putfield(requireDeclaredField(REMOVED_FIELD))
                .aload(0)
                .iconst(1)
                .putfield(requireDeclaredField(WAS_ADDED_FIELD))
                .aload(0)
                .iconst(0)
                .putfield(requireDeclaredField(WAS_REMOVED_FIELD))
                .vreturn());

        createBehavior(
            createMethod(INIT_REMOVE_METHOD_NAME, voidDecl(), ObjectDecl(), ObjectDecl()),
            code -> code
                .aload(0)
                .aload(1)
                .putfield(requireDeclaredField(KEY_FIELD))
                .aload(0)
                .aconst_null()
                .putfield(requireDeclaredField(ADDED_FIELD))
                .aload(0)
                .aload(2)
                .putfield(requireDeclaredField(REMOVED_FIELD))
                .aload(0)
                .iconst(0)
                .putfield(requireDeclaredField(WAS_ADDED_FIELD))
                .aload(0)
                .iconst(1)
                .putfield(requireDeclaredField(WAS_REMOVED_FIELD))
                .vreturn());

        createBehavior(
            createMethod(INIT_REPLACE_METHOD_NAME, voidDecl(), ObjectDecl(), ObjectDecl(), ObjectDecl()),
            code -> code
                .aload(0)
                .aload(1)
                .putfield(requireDeclaredField(KEY_FIELD))
                .aload(0)
                .aload(2)
                .putfield(requireDeclaredField(REMOVED_FIELD))
                .aload(0)
                .aload(3)
                .putfield(requireDeclaredField(ADDED_FIELD))
                .aload(0)
                .iconst(1)
                .putfield(requireDeclaredField(WAS_ADDED_FIELD))
                .aload(0)
                .iconst(1)
                .putfield(requireDeclaredField(WAS_REMOVED_FIELD))
                .vreturn());

        createBehavior(
            createMethod("wasAdded", booleanDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(WAS_ADDED_FIELD))
                .ireturn());

        createBehavior(
            createMethod("wasRemoved", booleanDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(WAS_REMOVED_FIELD))
                .ireturn());

        createBehavior(
            createMethod("getKey", ObjectDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(KEY_FIELD))
                .areturn());

        createBehavior(
            createMethod("getValueAdded", ObjectDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(ADDED_FIELD))
                .areturn());

        createBehavior(
            createMethod("getValueRemoved", ObjectDecl()), code -> code
                .aload(0)
                .getfield(requireDeclaredField(REMOVED_FIELD))
                .areturn());
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        createBehavior(constructor, code -> code
            .aload(0)
            .aload(1)
            .invoke(requireSuperClass().requireConstructor(ObservableMapDecl()))
            .vreturn());
    }
}
