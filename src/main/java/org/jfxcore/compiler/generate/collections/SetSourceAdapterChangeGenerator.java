// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate.collections;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class SetSourceAdapterChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("SetSourceAdapterChange");
    public static final String INIT_CHANGE_METHOD_NAME = "initChange";

    private static final String SOURCE_FIELD = "source";

    private CtConstructor constructor;
    private CtMethod initChangeMethod;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return new Resolver(SourceInfo.none()).getTypeInstance(SetChangeListenerChangeType());
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.setSuperclass(SetChangeListenerChangeType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(SetChangeListenerChangeType(), SOURCE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor(new CtClass[] {ObservableSetType()}, generatedClass);
        initChangeMethod = new CtMethod(
            voidType, INIT_CHANGE_METHOD_NAME, new CtClass[] {SetChangeListenerChangeType()}, generatedClass);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context, constructor);
        emitInitChangeMethod(context, initChangeMethod);

        CtClass type = SetChangeListenerChangeType();
        createFieldDelegateMethod(context, generatedClass, StringType(), SOURCE_FIELD, type, "toString");
        createFieldDelegateMethod(context, generatedClass, booleanType, SOURCE_FIELD, type, "wasAdded");
        createFieldDelegateMethod(context, generatedClass, booleanType, SOURCE_FIELD, type, "wasRemoved");
        createFieldDelegateMethod(context, generatedClass, ObjectType(), SOURCE_FIELD, type, "getElementAdded");
        createFieldDelegateMethod(context, generatedClass, ObjectType(), SOURCE_FIELD, type, "getElementRemoved");
    }

    private void emitConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        createBehavior(parentContext, generatedClass, constructor, 2, code -> code
            .aload(0)
            .aload(1)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor(ObservableSetType()))
            .vreturn());
    }

    private void emitInitChangeMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        createBehavior(parentContext, generatedClass, method, 2, code -> code
            .aload(0)
            .aload(1)
            .putfield(generatedClass, SOURCE_FIELD, SetChangeListenerChangeType())
            .vreturn());
    }

}
