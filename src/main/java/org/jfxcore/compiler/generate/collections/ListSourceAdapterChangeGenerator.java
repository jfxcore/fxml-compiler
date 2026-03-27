// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.TypeSymbols.*;

public class ListSourceAdapterChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("ListSourceAdapterChange");
    public static final String INIT_CHANGE_METHOD_NAME = "initChange";

    private static final String SOURCE_FIELD = "source";

    private ConstructorDeclaration constructor;
    private MethodDeclaration initChangeMethod;

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
        createField(SOURCE_FIELD, ListChangeListenerChangeDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(ObservableListDecl()).setModifiers(Modifier.PRIVATE);
        initChangeMethod = createMethod(INIT_CHANGE_METHOD_NAME, voidDecl(), ListChangeListenerChangeDecl());
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor(constructor);
        emitInitChangeMethod(initChangeMethod);

        TypeDeclaration type = ListChangeListenerChangeDecl();
        createFieldDelegateMethod(this, StringDecl(), SOURCE_FIELD, type, "toString");
        createFieldDelegateMethod(this, voidDecl(), SOURCE_FIELD, type, "reset");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "next");
        createFieldDelegateMethod(this, intDecl(), SOURCE_FIELD, type, "getFrom");
        createFieldDelegateMethod(this, intDecl(), SOURCE_FIELD, type, "getTo");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "wasAdded");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "wasRemoved");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "wasPermutated");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "wasReplaced");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "wasUpdated");
        createFieldDelegateMethod(this, intDecl(), SOURCE_FIELD, type, "getAddedSize");
        createFieldDelegateMethod(this, ListDecl(), SOURCE_FIELD, type, "getAddedSubList");
        createFieldDelegateMethod(this, intDecl(), SOURCE_FIELD, type, "getRemovedSize");
        createFieldDelegateMethod(this, ListDecl(), SOURCE_FIELD, type, "getRemoved");
        createFieldDelegateMethod(this, intDecl(), SOURCE_FIELD, type, "getPermutation", intDecl());

        var method = createMethod("getPermutation", new Resolver(SourceInfo.none()).resolveClass("int[]"));
        createBehavior(method, code -> code
            .anew(UnsupportedOperationExceptionDecl())
            .dup()
            .invoke(UnsupportedOperationExceptionDecl().requireConstructor())
            .athrow()
        );
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        createBehavior(constructor, code -> code
            .aload(0)
            .aload(1)
            .invoke(requireSuperClass().requireConstructor(ObservableListDecl()))
            .vreturn());
    }

    private void emitInitChangeMethod(MethodDeclaration method) {
        createBehavior(method, code -> code
            .aload(0)
            .aload(1)
            .putfield(requireDeclaredField(SOURCE_FIELD))
            .vreturn());
    }
}
