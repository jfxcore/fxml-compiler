// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class SetSourceAdapterChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("SetSourceAdapterChange");
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
        createField(SOURCE_FIELD, SetChangeListenerChangeDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(ObservableSetDecl()).setModifiers(Modifier.PRIVATE);
        initChangeMethod = createMethod(INIT_CHANGE_METHOD_NAME, voidDecl(), SetChangeListenerChangeDecl());
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor(constructor);
        emitInitChangeMethod(initChangeMethod);

        TypeDeclaration type = SetChangeListenerChangeDecl();
        createFieldDelegateMethod(this, StringDecl(), SOURCE_FIELD, type, "toString");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "wasAdded");
        createFieldDelegateMethod(this, booleanDecl(), SOURCE_FIELD, type, "wasRemoved");
        createFieldDelegateMethod(this, ObjectDecl(), SOURCE_FIELD, type, "getElementAdded");
        createFieldDelegateMethod(this, ObjectDecl(), SOURCE_FIELD, type, "getElementRemoved");
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        createBehavior(constructor, code -> code
            .aload(0)
            .aload(1)
            .invoke(requireSuperClass().requireConstructor(ObservableSetDecl()))
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
