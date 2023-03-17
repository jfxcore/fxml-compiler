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

    private CtConstructor constructor;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return new Resolver(SourceInfo.none()).getTypeInstance(MapChangeListenerChangeType());
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.setSuperclass(MapChangeListenerChangeType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(ObjectType(), KEY_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(ObjectType(), ADDED_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(ObjectType(), REMOVED_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(booleanType, WAS_ADDED_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(booleanType, WAS_REMOVED_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor( new CtClass[] {ObservableMapType()}, generatedClass);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context, constructor);

        createBehavior(
            context, generatedClass,
            new CtMethod(voidType, INIT_ADD_METHOD_NAME, new CtClass[] {ObjectType(), ObjectType()}, generatedClass),
            3, code -> code
                .aload(0)
                .aload(1)
                .putfield(generatedClass, KEY_FIELD, ObjectType())
                .aload(0)
                .aload(2)
                .putfield(generatedClass, ADDED_FIELD, ObjectType())
                .aload(0)
                .aconst_null()
                .putfield(generatedClass, REMOVED_FIELD, ObjectType())
                .aload(0)
                .iconst(1)
                .putfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .aload(0)
                .iconst(0)
                .putfield(generatedClass, WAS_REMOVED_FIELD, booleanType)
                .vreturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(voidType, INIT_REMOVE_METHOD_NAME, new CtClass[] {ObjectType(), ObjectType()}, generatedClass),
            3, code -> code
                .aload(0)
                .aload(1)
                .putfield(generatedClass, KEY_FIELD, ObjectType())
                .aload(0)
                .aconst_null()
                .putfield(generatedClass, ADDED_FIELD, ObjectType())
                .aload(0)
                .aload(2)
                .putfield(generatedClass, REMOVED_FIELD, ObjectType())
                .aload(0)
                .iconst(0)
                .putfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .aload(0)
                .iconst(1)
                .putfield(generatedClass, WAS_REMOVED_FIELD, booleanType)
                .vreturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(voidType, INIT_REPLACE_METHOD_NAME, new CtClass[] {ObjectType(), ObjectType(), ObjectType()}, generatedClass),
            4, code -> code
                .aload(0)
                .aload(1)
                .putfield(generatedClass, KEY_FIELD, ObjectType())
                .aload(0)
                .aload(2)
                .putfield(generatedClass, REMOVED_FIELD, ObjectType())
                .aload(0)
                .aload(3)
                .putfield(generatedClass, ADDED_FIELD, ObjectType())
                .aload(0)
                .iconst(1)
                .putfield(generatedClass, WAS_ADDED_FIELD, booleanType)
                .aload(0)
                .iconst(1)
                .putfield(generatedClass, WAS_REMOVED_FIELD, booleanType)
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
                .getfield(generatedClass, WAS_REMOVED_FIELD, booleanType)
                .ireturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(ObjectType(), "getKey", new CtClass[0], generatedClass), 1, code -> code
                .aload(0)
                .getfield(generatedClass, KEY_FIELD, ObjectType())
                .areturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(ObjectType(), "getValueAdded", new CtClass[0], generatedClass), 1, code -> code
                .aload(0)
                .getfield(generatedClass, ADDED_FIELD, ObjectType())
                .areturn());

        createBehavior(
            context, generatedClass,
            new CtMethod(ObjectType(), "getValueRemoved", new CtClass[0], generatedClass), 1, code -> code
                .aload(0)
                .getfield(generatedClass, REMOVED_FIELD, ObjectType())
                .areturn());
    }

    private void emitConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        createBehavior(parentContext, generatedClass, constructor, 2, code -> code
            .aload(0)
            .aload(1)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor(ObservableMapType()))
            .vreturn());
    }

}
