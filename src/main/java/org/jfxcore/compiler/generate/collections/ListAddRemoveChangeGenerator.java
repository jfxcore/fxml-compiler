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

public class ListAddRemoveChangeGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("ListAddRemoveChange");

    private static final String REMOVED_FIELD = "removed";
    private static final String FROM_FIELD = "from";
    private static final String TO_FIELD = "to";
    private static final String INVALID_FIELD = "invalid";

    private CtConstructor constructor;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return new Resolver(SourceInfo.none()).getTypeInstance(ListChangeListenerChangeType());
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        clazz = context.getNestedClasses().create(getClassName());
        clazz.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.setSuperclass(ListChangeListenerChangeType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(ListType(), REMOVED_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(intType, FROM_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(intType, TO_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(booleanType, INVALID_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor( new CtClass[] {intType, intType, ListType(), ObservableListType()}, clazz);
        clazz.addConstructor(constructor);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context, constructor);

        createBehavior(context, clazz, new CtMethod(voidType, "checkState", new CtClass[0], clazz), 1, code -> {
            code.aload(0)
                .getfield(clazz, INVALID_FIELD, booleanType)
                .ifne(() -> {
                    code.anew(IllegalStateExceptionType())
                        .dup()
                        .ldc("next() must be called before inspecting the change")
                        .invokespecial(IllegalStateExceptionType(), MethodInfo.nameInit, constructor(StringType()))
                        .athrow();
                })
                .vreturn();
        });

        createBehavior(context, clazz, new CtMethod(intType, "getFrom", new CtClass[0], clazz), 1, code -> {
            code.aload(0)
                .invokevirtual(clazz, "checkState", function(voidType))
                .aload(0)
                .getfield(clazz, FROM_FIELD, intType)
                .ireturn();
        });

        createBehavior(context, clazz, new CtMethod(intType, "getTo", new CtClass[0], clazz), 1, code -> {
            code.aload(0)
                .invokevirtual(clazz, "checkState", function(voidType))
                .aload(0)
                .getfield(clazz, TO_FIELD, intType)
                .ireturn();
        });

        CtClass intArrayType = new Resolver(SourceInfo.none()).resolveClass("int[]");
        createBehavior(context, clazz, new CtMethod(intArrayType, "getPermutation", new CtClass[0], clazz), 1, code -> {
            code.aload(0)
                .invokevirtual(clazz, "checkState", function(voidType))
                .newarray(intType, 0)
                .areturn();
        });

        createBehavior(context, clazz, new CtMethod(ListType(), "getRemoved", new CtClass[0], clazz), 1, code -> {
            code.aload(0)
                .invokevirtual(clazz, "checkState", function(voidType))
                .aload(0)
                .getfield(clazz, REMOVED_FIELD, ListType())
                .areturn();
        });

        createBehavior(context, clazz, new CtMethod(voidType, "reset", new CtClass[0], clazz), 1, code -> {
            code.aload(0)
                .iconst(1)
                .putfield(clazz, INVALID_FIELD, booleanType)
                .vreturn();
        });

        createBehavior(context, clazz, new CtMethod(booleanType, "next", new CtClass[0], clazz), 1, code -> {
            code.aload(0)
                .getfield(clazz, INVALID_FIELD, booleanType)
                .ifne(
                    () -> code
                        .aload(0)
                        .iconst(0)
                        .putfield(clazz, INVALID_FIELD, booleanType)
                        .iconst(1),
                    () -> code
                        .iconst(0))
                .ireturn();
        });
    }

    private void emitConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        var context = new BytecodeEmitContext(parentContext, clazz, 5, -1);
        context.getOutput()
            .aload(0)
            .aload(4)
            .invokespecial(clazz.getSuperclass(), MethodInfo.nameInit, constructor(ObservableListType()))
            .aload(0)
            .aload(3)
            .putfield(clazz, REMOVED_FIELD, ListType())
            .aload(0)
            .iload(1)
            .putfield(clazz, FROM_FIELD, intType)
            .aload(0)
            .iload(2)
            .putfield(clazz, TO_FIELD, intType)
            .aload(0)
            .iconst(1)
            .putfield(clazz, INVALID_FIELD, booleanType)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(context.getOutput().toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(clazz.getClassPool());
    }

}
