// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.TypeSymbols.*;

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

    private TypeDeclaration parentArrayType;
    private ConstructorDeclaration constructor;
    private MethodDeclaration getTargetNameMethod;
    private MethodDeclaration getTargetTypeMethod;
    private MethodDeclaration getTargetBeanMethod;
    private MethodDeclaration getRootMethod;
    private MethodDeclaration pushParentMethod;
    private MethodDeclaration popParentMethod;
    private MethodDeclaration getAncestorMethod;
    private MethodDeclaration getAncestorCountMethod;
    private MethodDeclaration setTargetInfoMethod;

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
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        parentArrayType = new Resolver(SourceInfo.none()).resolveClass(ObjectDecl().name() + "[]");

        TypeDeclaration generatedClass = super.emitClass(context)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

        if (markupContextSupport) {
            generatedClass.addInterface(Markup.MarkupContextDecl());
        }

        return generatedClass;
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(ANCESTORS_FIELD, parentArrayType).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        createField(INDEX_FIELD, intDecl()).setModifiers(Modifier.PRIVATE);

        if (markupContextSupport) {
            createField(TARGET_BEAN_FIELD, ObjectDecl()).setModifiers(Modifier.PRIVATE);
            createField(TARGET_TYPE_FIELD, ClassDecl()).setModifiers(Modifier.PRIVATE);
            createField(TARGET_NAME_FIELD, StringDecl()).setModifiers(Modifier.PRIVATE);
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(intDecl());

        pushParentMethod = createMethod(PUSH_PARENT_METHOD, voidDecl(), ObjectDecl())
            .setModifiers(Modifier.FINAL);

        popParentMethod = createMethod(POP_PARENT_METHOD, voidDecl())
            .setModifiers(Modifier.FINAL);

        getAncestorMethod = createMethod(GET_ANCESTOR_METHOD, ObjectDecl(), intDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        getRootMethod = createMethod(GET_ROOT_METHOD, ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        if (markupContextSupport) {
            setTargetInfoMethod = createMethod(SET_TARGET_INFO, voidDecl(), ClassDecl(), ObjectDecl(), StringDecl())
                .setModifiers(Modifier.FINAL);

            getTargetTypeMethod = createMethod("getTargetType", ClassDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            getTargetNameMethod = createMethod("getTargetName", StringDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            getTargetBeanMethod = createMethod("getTargetBean", ObjectDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            getAncestorCountMethod = createMethod(GET_ANCESTOR_COUNT_METHOD, intDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor();
        emitPushParentMethod();
        emitPopParentMethod();
        emitGetRootMethod();
        emitGetParentMethod();

        if (markupContextSupport) {
            emitSetPropertyInfoMethod();
            emitGetTargetTypeMethod();
            emitGetTargetNameMethod();
            emitGetTargetBeanMethod();
            emitGetParentCountMethod();
        }
    }

    private void emitConstructor() {
        Bytecode code = new Bytecode(constructor);

        // this.super()
        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor());

        // this.parents = new Object[$1]
        code.aload(0)
            .iload(1)
            .newarray(ObjectDecl())
            .putfield(requireDeclaredField(ANCESTORS_FIELD))
            .vreturn();

        constructor.setCode(code);
    }

    private void emitPushParentMethod() {
        Bytecode code = new Bytecode(pushParentMethod);

        code.aload(0)
            .getfield(requireDeclaredField(ANCESTORS_FIELD))
            .aload(0)
            .dup()
            .getfield(requireDeclaredField(INDEX_FIELD))
            .dup_x1()
            .iconst(1)
            .iadd()
            .putfield(requireDeclaredField(INDEX_FIELD))
            .aload(1)
            .arraystore(ObjectDecl())
            .vreturn();

        pushParentMethod.setCode(code);
    }

    private void emitPopParentMethod() {
        Bytecode code = new Bytecode(popParentMethod);

        code.aload(0)
            .getfield(requireDeclaredField(ANCESTORS_FIELD))
            .aload(0)
            .dup()
            .getfield(requireDeclaredField(INDEX_FIELD))
            .iconst(1)
            .isub()
            .dup_x1()
            .putfield(requireDeclaredField(INDEX_FIELD))
            .aconst_null()
            .arraystore(ObjectDecl())
            .vreturn();

        popParentMethod.setCode(code);
    }

    private void emitGetParentMethod() {
        Bytecode code = new Bytecode(getAncestorMethod);

        code.aload(0)
            .getfield(requireDeclaredField(ANCESTORS_FIELD))
            .aload(0)
            .getfield(requireDeclaredField(INDEX_FIELD))
            .iload(1)
            .isub()
            .iconst(1)
            .isub()
            .arrayload(ObjectDecl())
            .areturn();

        getAncestorMethod.setCode(code);
    }

    private void emitGetParentCountMethod() {
        Bytecode code = new Bytecode(getAncestorCountMethod);

        code.aload(0)
            .getfield(requireDeclaredField(INDEX_FIELD))
            .ireturn();

        getAncestorCountMethod.setCode(code);
    }

    private void emitSetPropertyInfoMethod() {
        Bytecode code = new Bytecode(setTargetInfoMethod);

        code.aload(0)
            .aload(1)
            .putfield(requireDeclaredField(TARGET_TYPE_FIELD))
            .aload(0)
            .aload(2)
            .putfield(requireDeclaredField(TARGET_BEAN_FIELD))
            .aload(0)
            .aload(3)
            .putfield(requireDeclaredField(TARGET_NAME_FIELD))
            .vreturn();

        setTargetInfoMethod.setCode(code);
    }

    private void emitGetTargetTypeMethod() {
        Bytecode code = new Bytecode(getTargetTypeMethod);

        code.aload(0)
            .getfield(requireDeclaredField(TARGET_TYPE_FIELD))
            .ret(ClassDecl());

        getTargetTypeMethod.setCode(code);
    }

    private void emitGetTargetBeanMethod() {
        Bytecode code = new Bytecode(getTargetBeanMethod);

        code.aload(0)
            .getfield(requireDeclaredField(TARGET_BEAN_FIELD))
            .areturn();

        getTargetBeanMethod.setCode(code);
    }

    private void emitGetTargetNameMethod() {
        Bytecode code = new Bytecode(getTargetNameMethod);

        code.aload(0)
            .getfield(requireDeclaredField(TARGET_NAME_FIELD))
            .areturn();

        getTargetNameMethod.setCode(code);
    }

    private void emitGetRootMethod() {
        Bytecode code = new Bytecode(getRootMethod);

        code.aload(0)
            .getfield(requireDeclaredField(ANCESTORS_FIELD))
            .iconst(0)
            .arrayload(ObjectDecl())
            .areturn();

        getRootMethod.setCode(code);
    }
}
