// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class ListAddRemoveChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("ListAddRemoveChange");

    private static final String REMOVED_FIELD = "removed";
    private static final String FROM_FIELD = "from";
    private static final String TO_FIELD = "to";
    private static final String INVALID_FIELD = "invalid";

    private ConstructorDeclaration constructor;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return new TypeInvoker(SourceInfo.none()).invokeType(ListChangeListenerChangeDecl());
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            .setSuperClass(ListChangeListenerChangeDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(REMOVED_FIELD, ListDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(FROM_FIELD, intDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(TO_FIELD, intDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(INVALID_FIELD, booleanDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(intDecl(), intDecl(), ListDecl(), ObservableListDecl());
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor(constructor);

        createBehavior(createMethod("checkState", voidDecl()), code -> code
            .aload(0)
            .getfield(requireDeclaredField(INVALID_FIELD))
            .ifne(() -> code
                .anew(IllegalStateExceptionDecl())
                .dup()
                .ldc("next() must be called before inspecting the change")
                .invoke(IllegalStateExceptionDecl().requireConstructor(StringDecl()))
                .athrow())
            .vreturn()
        );

        createBehavior(createMethod("getFrom", intDecl()), code -> code
            .aload(0)
            .invoke(requireDeclaredMethod("checkState"))
            .aload(0)
            .getfield(requireDeclaredField(FROM_FIELD))
            .ireturn()
        );

        createBehavior(createMethod("getTo", intDecl()), code -> code
            .aload(0)
            .invoke(requireDeclaredMethod("checkState"))
            .aload(0)
            .getfield(requireDeclaredField(TO_FIELD))
            .ireturn()
        );

        createBehavior(createMethod("getPermutation",
                new Resolver(SourceInfo.none()).resolveClass("int[]")), code -> code
            .aload(0)
            .invoke(requireDeclaredMethod("checkState"))
            .newarray(intDecl(), 0)
            .areturn()
        );

        createBehavior(createMethod("getRemoved", ListDecl()), code -> code
            .aload(0)
            .invoke(requireDeclaredMethod("checkState"))
            .aload(0)
            .getfield(requireDeclaredField(REMOVED_FIELD))
            .areturn()
        );

        createBehavior(createMethod("reset", voidDecl()), code -> code
            .aload(0)
            .iconst(1)
            .putfield(requireDeclaredField(INVALID_FIELD))
            .vreturn()
        );

        createBehavior(createMethod("next", booleanDecl()), code -> code
            .aload(0)
            .getfield(requireDeclaredField(INVALID_FIELD))
            .ifne(
                () -> code
                    .aload(0)
                    .iconst(0)
                    .putfield(requireDeclaredField(INVALID_FIELD))
                    .iconst(1),
                () -> code
                    .iconst(0))
            .ireturn()
        );
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        var code = new Bytecode(constructor);

        code.aload(0)
            .aload(4)
            .invoke(requireSuperClass().requireDeclaredConstructor(ObservableListDecl()))
            .aload(0)
            .aload(3)
            .putfield(requireDeclaredField(REMOVED_FIELD))
            .aload(0)
            .iload(1)
            .putfield(requireDeclaredField(FROM_FIELD))
            .aload(0)
            .iload(2)
            .putfield(requireDeclaredField(TO_FIELD))
            .aload(0)
            .iconst(1)
            .putfield(requireDeclaredField(INVALID_FIELD))
            .vreturn();

        constructor.setCode(code);
    }
}
