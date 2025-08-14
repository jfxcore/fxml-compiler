// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.util.Classes.*;

public class RuntimeContextGenerator extends ClassGenerator {

    public static final String SET_TARGET_INFO = "set";
    public static final String PUSH_PARENT_METHOD = "push";
    public static final String POP_PARENT_METHOD = "pop";
    public static final String GET_ROOT_METHOD = "getRoot";
    public static final String GET_ANCESTOR_METHOD = "getAncestor";
    public static final String GET_ANCESTOR_COUNT_METHOD = "getAncestorCount";

    private static final String ANCESTORS_FIELD = "ancestors";
    private static final String INDEX_FIELD = "index";
    private static final String TARGET_BEAN_FIELD = "targetBean";
    private static final String TARGET_NAME_FIELD = "targetName";
    private static final String TARGET_TYPE_FIELD = "targetType";

    private final boolean markupContextSupport;
    private CtClass parentArrayType;
    private CtConstructor constructor;
    private CtMethod getTargetNameMethod;
    private CtMethod getTargetTypeMethod;
    private CtMethod getTargetBeanMethod;
    private CtMethod getRootMethod;
    private CtMethod pushParentMethod;
    private CtMethod popParentMethod;
    private CtMethod getAncestorMethod;
    private CtMethod getAncestorCountMethod;
    private CtMethod setTargetInfoMethod;

    public RuntimeContextGenerator(boolean markupContextSupport) {
        this.markupContextSupport = markupContextSupport && Markup.isAvailable();
    }

    @Override
    public TypeInstance getTypeInstance() {
        return TypeInstance.ObjectType();
    }

    @Override
    public String getClassName() {
        return NameHelper.getMangledClassName("RuntimeContext");
    }

    @Override
    public void emitClass(BytecodeEmitContext context) {
        parentArrayType = new Resolver(SourceInfo.none()).resolveClass(ObjectType().getName() + "[]");
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);

        if (markupContextSupport) {
            generatedClass.addInterface(Markup.MarkupContextType());
        }
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(parentArrayType, ANCESTORS_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        field = new CtField(CtClass.intType, INDEX_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        if (markupContextSupport) {
            field = new CtField(ObjectType(), TARGET_BEAN_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);

            field = new CtField(ClassType(), TARGET_TYPE_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);

            field = new CtField(StringType(), TARGET_NAME_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        constructor = new CtConstructor(new CtClass[] {CtClass.intType}, generatedClass);
        generatedClass.addConstructor(constructor);

        pushParentMethod = new CtMethod(CtClass.voidType, PUSH_PARENT_METHOD, new CtClass[] {ObjectType()}, generatedClass);
        pushParentMethod.setModifiers(Modifier.FINAL);
        generatedClass.addMethod(pushParentMethod);

        popParentMethod = new CtMethod(CtClass.voidType, POP_PARENT_METHOD, new CtClass[0], generatedClass);
        popParentMethod.setModifiers(Modifier.FINAL);
        generatedClass.addMethod(popParentMethod);

        getAncestorMethod = new CtMethod(ObjectType(), GET_ANCESTOR_METHOD, new CtClass[] { CtClass.intType }, generatedClass);
        getAncestorMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(getAncestorMethod);

        getRootMethod = new CtMethod(ObjectType(), GET_ROOT_METHOD, new CtClass[0], generatedClass);
        getRootMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(getRootMethod);

        if (markupContextSupport) {
            setTargetInfoMethod = new CtMethod(CtClass.voidType, SET_TARGET_INFO,
                new CtClass[] {ClassType(), ObjectType(), StringType()}, generatedClass);
            setTargetInfoMethod.setModifiers(Modifier.FINAL);
            generatedClass.addMethod(setTargetInfoMethod);

            getTargetTypeMethod = new CtMethod(ClassType(), "getTargetType", new CtClass[0], generatedClass);
            getTargetTypeMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(getTargetTypeMethod);

            getTargetNameMethod = new CtMethod(StringType(), "getTargetName", new CtClass[0], generatedClass);
            getTargetNameMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(getTargetNameMethod);

            getTargetBeanMethod = new CtMethod(ObjectType(), "getTargetBean", new CtClass[0], generatedClass);
            getTargetBeanMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(getTargetBeanMethod);

            getAncestorCountMethod = new CtMethod(CtClass.intType, GET_ANCESTOR_COUNT_METHOD, new CtClass[0], generatedClass);
            getAncestorCountMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(getAncestorCountMethod);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(context);
        emitPushParentMethod(context);
        emitPopParentMethod(context);
        emitGetRootMethod(context);
        emitGetParentMethod(context);

        if (markupContextSupport) {
            emitSetPropertyInfoMethod(context);
            emitGetTargetTypeMethod(context);
            emitGetTargetNameMethod(context);
            emitGetTargetBeanMethod(context);
            emitGetParentCountMethod(context);
        }
    }

    private void emitConstructor(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        Bytecode code = context.getOutput();

        // this.super()
        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, "()V");

        // this.parents = new Object[$1]
        code.aload(0)
            .iload(1)
            .newarray(ObjectType())
            .putfield(generatedClass, ANCESTORS_FIELD, parentArrayType)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitPushParentMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, ANCESTORS_FIELD, parentArrayType)
            .aload(0)
            .dup()
            .getfield(generatedClass, INDEX_FIELD, CtClass.intType)
            .dup_x1()
            .iconst(1)
            .iadd()
            .putfield(generatedClass, INDEX_FIELD, CtClass.intType)
            .aload(1)
            .ext_arraystore(ObjectType())
            .vreturn();

        pushParentMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        pushParentMethod.getMethodInfo().rebuildStackMap(pushParentMethod.getDeclaringClass().getClassPool());
    }

    private void emitPopParentMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, ANCESTORS_FIELD, parentArrayType)
            .aload(0)
            .dup()
            .getfield(generatedClass, INDEX_FIELD, CtClass.intType)
            .iconst(1)
            .isub()
            .dup_x1()
            .putfield(generatedClass, INDEX_FIELD, CtClass.intType)
            .aconst_null()
            .ext_arraystore(ObjectType())
            .vreturn();

        popParentMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        popParentMethod.getMethodInfo().rebuildStackMap(popParentMethod.getDeclaringClass().getClassPool());
    }

    private void emitGetParentMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, ANCESTORS_FIELD, parentArrayType)
            .aload(0)
            .getfield(generatedClass, INDEX_FIELD, CtClass.intType)
            .iload(1)
            .isub()
            .iconst(1)
            .isub()
            .ext_arrayload(ObjectType())
            .areturn();

        getAncestorMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getAncestorMethod.getMethodInfo().rebuildStackMap(getAncestorMethod.getDeclaringClass().getClassPool());
    }

    private void emitGetParentCountMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, INDEX_FIELD, CtClass.intType)
            .ireturn();

        getAncestorCountMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getAncestorCountMethod.getMethodInfo().rebuildStackMap(getAncestorCountMethod.getDeclaringClass().getClassPool());
    }

    private void emitSetPropertyInfoMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 4, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .aload(1)
            .putfield(generatedClass, TARGET_TYPE_FIELD, ClassType())
            .aload(0)
            .aload(2)
            .putfield(generatedClass, TARGET_BEAN_FIELD, ObjectType())
            .aload(0)
            .aload(3)
            .putfield(generatedClass, TARGET_NAME_FIELD, StringType())
            .vreturn();

        setTargetInfoMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        setTargetInfoMethod.getMethodInfo().rebuildStackMap(
            setTargetInfoMethod.getDeclaringClass().getClassPool());
    }

    private void emitGetTargetTypeMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, TARGET_TYPE_FIELD, ClassType())
            .ext_return(ClassType());

        getTargetTypeMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getTargetTypeMethod.getMethodInfo().rebuildStackMap(
        getTargetTypeMethod.getDeclaringClass().getClassPool());
    }

    private void emitGetTargetBeanMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, TARGET_BEAN_FIELD, ObjectType())
            .areturn();

        getTargetBeanMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getTargetBeanMethod.getMethodInfo().rebuildStackMap(getTargetBeanMethod.getDeclaringClass().getClassPool());
    }

    private void emitGetTargetNameMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, TARGET_NAME_FIELD, StringType())
            .areturn();

        getTargetNameMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getTargetNameMethod.getMethodInfo().rebuildStackMap(
            getTargetNameMethod.getDeclaringClass().getClassPool());
    }

    private void emitGetRootMethod(BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, ANCESTORS_FIELD, parentArrayType)
            .iconst(0)
            .ext_arrayload(ObjectType())
            .areturn();

        getRootMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        getRootMethod.getMethodInfo().rebuildStackMap(getRootMethod.getDeclaringClass().getClassPool());
    }
}
