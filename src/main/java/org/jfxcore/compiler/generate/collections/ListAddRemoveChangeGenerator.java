// Copyright (c) 2023, 2025, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.util.TypeInvoker;

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
        return new TypeInvoker(SourceInfo.none()).invokeType(ListChangeListenerChangeType());
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.setSuperclass(ListChangeListenerChangeType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(ListType(), REMOVED_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(intType, FROM_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(intType, TO_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(booleanType, INVALID_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor( new CtClass[] {intType, intType, ListType(), ObservableListType()}, generatedClass);
        generatedClass.addConstructor(constructor);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context, constructor);

        createBehavior(context, generatedClass, new CtMethod(voidType, "checkState", new CtClass[0], generatedClass), 1, code -> {
            code.aload(0)
                .getfield(generatedClass, INVALID_FIELD, booleanType)
                .ifne(() -> {
                    code.anew(IllegalStateExceptionType())
                        .dup()
                        .ldc("next() must be called before inspecting the change")
                        .invokespecial(IllegalStateExceptionType(), MethodInfo.nameInit, constructor(StringType()))
                        .athrow();
                })
                .vreturn();
        });

        createBehavior(context, generatedClass, new CtMethod(intType, "getFrom", new CtClass[0], generatedClass), 1, code -> {
            code.aload(0)
                .invokevirtual(generatedClass, "checkState", function(voidType))
                .aload(0)
                .getfield(generatedClass, FROM_FIELD, intType)
                .ireturn();
        });

        createBehavior(context, generatedClass, new CtMethod(intType, "getTo", new CtClass[0], generatedClass), 1, code -> {
            code.aload(0)
                .invokevirtual(generatedClass, "checkState", function(voidType))
                .aload(0)
                .getfield(generatedClass, TO_FIELD, intType)
                .ireturn();
        });

        CtClass intArrayType = new Resolver(SourceInfo.none()).resolveClass("int[]");
        createBehavior(context, generatedClass, new CtMethod(intArrayType, "getPermutation", new CtClass[0], generatedClass), 1, code -> {
            code.aload(0)
                .invokevirtual(generatedClass, "checkState", function(voidType))
                .newarray(intType, 0)
                .areturn();
        });

        createBehavior(context, generatedClass, new CtMethod(ListType(), "getRemoved", new CtClass[0], generatedClass), 1, code -> {
            code.aload(0)
                .invokevirtual(generatedClass, "checkState", function(voidType))
                .aload(0)
                .getfield(generatedClass, REMOVED_FIELD, ListType())
                .areturn();
        });

        createBehavior(context, generatedClass, new CtMethod(voidType, "reset", new CtClass[0], generatedClass), 1, code -> {
            code.aload(0)
                .iconst(1)
                .putfield(generatedClass, INVALID_FIELD, booleanType)
                .vreturn();
        });

        createBehavior(context, generatedClass, new CtMethod(booleanType, "next", new CtClass[0], generatedClass), 1, code -> {
            code.aload(0)
                .getfield(generatedClass, INVALID_FIELD, booleanType)
                .ifne(
                    () -> code
                        .aload(0)
                        .iconst(0)
                        .putfield(generatedClass, INVALID_FIELD, booleanType)
                        .iconst(1),
                    () -> code
                        .iconst(0))
                .ireturn();
        });
    }

    private void emitConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 5, -1);
        context.getOutput()
            .aload(0)
            .aload(4)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor(ObservableListType()))
            .aload(0)
            .aload(3)
            .putfield(generatedClass, REMOVED_FIELD, ListType())
            .aload(0)
            .iload(1)
            .putfield(generatedClass, FROM_FIELD, intType)
            .aload(0)
            .iload(2)
            .putfield(generatedClass, TO_FIELD, intType)
            .aload(0)
            .iconst(1)
            .putfield(generatedClass, INVALID_FIELD, booleanType)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(context.getOutput().toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }
}
