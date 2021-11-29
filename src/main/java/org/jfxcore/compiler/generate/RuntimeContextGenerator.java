// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;

public class RuntimeContextGenerator extends GeneratorBase {

    public static final String PUSH_PARENT_METHOD = "push";
    public static final String POP_PARENT_METHOD = "pop";
    public static final String GET_RESOURCE_METHOD = "getResource";

    public static final String PARENTS_FIELD = "$0";
    public static final String TARGET_TYPE_FIELD = "$1";
    private static final String INDEX_FIELD = "$2";

    private final boolean resourceSupport;
    private CtClass parentArrayType;
    private CtConstructor constructor;
    private CtMethod getTargetTypeMethod;
    private CtMethod getParentsMethod;
    private CtMethod pushParentMethod;
    private CtMethod popParentMethod;
    private CtMethod getResourceMethod;

    public static CtClass getParentArrayType() {
        return new Resolver(SourceInfo.none()).resolveClass(Classes.ObjectType().getName() + "[]");
    }

    public RuntimeContextGenerator(boolean resourceSupport) {
        this.resourceSupport = resourceSupport;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return new TypeInstance(Classes.ObjectType());
    }

    @Override
    public String getClassName() {
        return "RuntimeContext";
    }

    @Override
    public void emitClass(BytecodeEmitContext context) {
        parentArrayType = getParentArrayType();
        clazz = context.getMarkupClass().makeNestedClass(getClassName(), true);
        clazz.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        context.getNestedClasses().add(clazz);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(parentArrayType, PARENTS_FIELD, clazz);
        field.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addField(field);

        field = new CtField(Classes.ClassType(), TARGET_TYPE_FIELD, clazz);
        field.setModifiers(Modifier.PUBLIC);
        clazz.addField(field);

        field = new CtField(CtClass.intType, INDEX_FIELD, clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor(new CtClass[] {Classes.ObjectType(), CtClass.intType}, clazz);
        clazz.addConstructor(constructor);

        getTargetTypeMethod = new CtMethod(Classes.ClassType(), "getTargetType", new CtClass[0], clazz);
        getTargetTypeMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(getTargetTypeMethod);

        getParentsMethod = new CtMethod(parentArrayType, "getParents", new CtClass[0], clazz);
        getParentsMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(getParentsMethod);

        pushParentMethod = new CtMethod(CtClass.voidType, PUSH_PARENT_METHOD, new CtClass[] {Classes.ObjectType()}, clazz);
        pushParentMethod.setModifiers(Modifier.FINAL);
        clazz.addMethod(pushParentMethod);

        popParentMethod = new CtMethod(CtClass.voidType, POP_PARENT_METHOD, new CtClass[0], clazz);
        popParentMethod.setModifiers(Modifier.FINAL);
        clazz.addMethod(popParentMethod);

        if (resourceSupport) {
            getResourceMethod = new CtMethod(Classes.URLType(), GET_RESOURCE_METHOD, new CtClass[] {Classes.StringType()}, clazz);
            getResourceMethod.setModifiers(Modifier.FINAL);
            clazz.addMethod(getResourceMethod);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context);
        emitGetPropertyTypeMethod(context);
        emitGetParentsMethod(context);
        emitPushParentMethod(context);
        emitPopParentMethod(context);

        if (getResourceMethod != null) {
            emitGetResourceMethod(context);
        }
    }

    private void emitConstructor(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 3, -1);
        Bytecode code = context.getOutput();

        // this.super()
        code.aload(0)
            .invokespecial(clazz.getSuperclass(), MethodInfo.nameInit, "()V");

        // this.parents = new Object[$2]
        code.aload(0)
            .iload(2)
            .newarray(Classes.ObjectType())
            .putfield(clazz, PARENTS_FIELD, parentArrayType)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitGetPropertyTypeMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(clazz, TARGET_TYPE_FIELD, Classes.ClassType())
            .areturn();

        getTargetTypeMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getTargetTypeMethod.getMethodInfo().rebuildStackMap(getTargetTypeMethod.getDeclaringClass().getClassPool());
    }

    private void emitGetParentsMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(clazz, PARENTS_FIELD, parentArrayType)
            .areturn();

        getParentsMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getParentsMethod.getMethodInfo().rebuildStackMap(getParentsMethod.getDeclaringClass().getClassPool());
    }

    private void emitPushParentMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 2, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(clazz, PARENTS_FIELD, parentArrayType)
            .aload(0)
            .dup()
            .getfield(clazz, INDEX_FIELD, CtClass.intType)
            .dup_x1()
            .iconst(1)
            .iadd()
            .putfield(clazz, INDEX_FIELD, CtClass.intType)
            .aload(1)
            .ext_arraystore(Classes.ObjectType())
            .vreturn();

        pushParentMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        pushParentMethod.getMethodInfo().rebuildStackMap(pushParentMethod.getDeclaringClass().getClassPool());
    }

    private void emitPopParentMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(clazz, PARENTS_FIELD, parentArrayType)
            .aload(0)
            .dup()
            .getfield(clazz, INDEX_FIELD, CtClass.intType)
            .iconst(1)
            .isub()
            .dup_x1()
            .putfield(clazz, INDEX_FIELD, CtClass.intType)
            .aconst_null()
            .ext_arraystore(Classes.ObjectType())
            .vreturn();

        popParentMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        popParentMethod.getMethodInfo().rebuildStackMap(popParentMethod.getDeclaringClass().getClassPool());
    }

    @SuppressWarnings("CodeBlock2Expr")
    private void emitGetResourceMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 2, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(clazz, PARENTS_FIELD, parentArrayType)
            .iconst(0)
            .ext_arrayload(Classes.ParentType())
            .invokevirtual(Classes.ObjectType(), "getClass", Descriptors.function(Classes.ClassType()))
            .aload(1)
            .invokevirtual(Classes.ClassType(), "getResource", Descriptors.function(Classes.URLType(), Classes.StringType()));

        Local local = code.acquireLocal(false);

        code.astore(local)
            .aload(local)
            .ifnull(() -> {
                code.anew(Classes.RuntimeExceptionType())
                    .dup()
                    .ldc("Resource not found: ")
                    .aload(1)
                    .invokevirtual(Classes.StringType(), "concat", Descriptors.function(Classes.StringType(), Classes.StringType()))
                    .invokespecial(Classes.RuntimeExceptionType(), MethodInfo.nameInit, Descriptors.constructor(Classes.StringType()))
                    .athrow();
            });

        code.aload(local)
            .areturn()
            .releaseLocal(local);

        getResourceMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getResourceMethod.getMethodInfo().rebuildStackMap(getResourceMethod.getDeclaringClass().getClassPool());
    }

}