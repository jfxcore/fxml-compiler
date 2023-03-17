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

public class SetAddRemoveChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("SetAddRemoveChange");
    public static final String INIT_METHOD_NAME = "initChange";

    private static final String VALUE_FIELD = "value";
    private static final String WAS_ADDED_FIELD = "added";

    private CtConstructor constructor;

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
        CtField field = new CtField(ObjectType(), VALUE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(booleanType, WAS_ADDED_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor( new CtClass[] {ObservableSetType()}, generatedClass);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context, constructor);

        createBehavior(
            context, generatedClass,
            new CtMethod(voidType, INIT_METHOD_NAME, new CtClass[] {ObjectType(), booleanType}, generatedClass), 3, code -> code
                .aload(0)
                .aload(1)
                .putfield(generatedClass, VALUE_FIELD, ObjectType())
                .aload(0)
                .iload(2)
                .putfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .vreturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(booleanType, "wasAdded", new CtClass[0], generatedClass), 1, code -> code
                .aload(0)
                .getfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .ireturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(booleanType, "wasRemoved", new CtClass[0], generatedClass), 1, code -> code
                .aload(0)
                .getfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .iconst(1)
                .ixor()
                .ireturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(ObjectType(), "getElementAdded", new CtClass[0], generatedClass), 1, code -> code
                .aload(0)
                .getfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .ifeq(
                    code::aconst_null,
                    () -> code
                        .aload(0)
                        .getfield(generatedClass, VALUE_FIELD, ObjectType()))
                .areturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(ObjectType(), "getElementRemoved", new CtClass[0], generatedClass), 1, code -> code
                .aload(0)
                .getfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .iconst(1)
                .ixor()
                .ifeq(
                    code::aconst_null,
                    () -> code
                        .aload(0)
                        .getfield(generatedClass, VALUE_FIELD, ObjectType()))
                .areturn());
    }

    private void emitConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        createBehavior(parentContext, generatedClass, constructor, 2, code -> code
            .aload(0)
            .aload(1)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor(ObservableSetType()))
            .vreturn());
    }

}
