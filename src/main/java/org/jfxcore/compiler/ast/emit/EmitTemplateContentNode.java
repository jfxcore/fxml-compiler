// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.RootNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EmitTemplateContentNode extends AbstractNode implements ValueEmitterNode, RootNode {

    private final List<Node> preamble;
    private final TypeInstance itemType;
    private final TypeInstance bindingContextClass;
    private EmitInitializeRootNode content;
    private ResolvedTypeNode type;

    public EmitTemplateContentNode(
            TypeInstance type,
            TypeInstance itemType,
            TypeInstance bindingContextClass,
            EmitObjectNode content,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.itemType = checkNotNull(itemType);
        this.bindingContextClass = checkNotNull(bindingContextClass);
        this.content = new EmitInitializeRootNode(checkNotNull(content), true, sourceInfo);
        this.preamble = new ArrayList<>();
    }

    private EmitTemplateContentNode(
            TypeInstance type,
            TypeInstance itemType,
            TypeInstance bindingContextClass,
            EmitInitializeRootNode content,
            Collection<? extends Node> preamble,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.itemType = checkNotNull(itemType);
        this.bindingContextClass = checkNotNull(bindingContextClass);
        this.content = checkNotNull(content);
        this.preamble = new ArrayList<>(checkNotNull(preamble));
    }

    @Override
    public List<Node> getPreamble() {
        return preamble;
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        content = (EmitInitializeRootNode)content.accept(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
    }

    @Override
    public EmitTemplateContentNode deepClone() {
        return new EmitTemplateContentNode(
            type.getTypeInstance(), itemType, bindingContextClass, content, preamble, getSourceInfo());
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        CtClass oldContextClass = context.getBindingContextClass();
        CtClass newContextClass = bindingContextClass.jvmType();

        try {
            context.getNestedClasses().add(newContextClass);
            context.setBindingContextClass(newContextClass);

            CtClass factoryClass = unchecked(() -> {
                emitTemplateContentClass(context, newContextClass);
                return emitFactoryClass(context, newContextClass);
            });

            context.getOutput()
                .anew(factoryClass)
                .dup()
                .invokespecial(factoryClass, MethodInfo.nameInit, "()V");
        } finally {
            context.setBindingContextClass(oldContextClass);
        }
    }

    private CtClass emitFactoryClass(BytecodeEmitContext context, CtClass contentClass) throws Exception {
        String className = NameHelper.getUniqueName("TemplateContentFactory", this);
        CtClass clazz = context.getMarkupClass().makeNestedClass(className, true);
        clazz.setModifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL);
        clazz.addInterface(Classes.Core.TemplateContentType());
        context.getNestedClasses().add(clazz);

        emitForceInitMethod(context, clazz);
        emitNewInstanceMethod(context, contentClass, clazz);
        clazz.addConstructor(CtNewConstructor.defaultConstructor(clazz));
        return clazz;
    }

    private void emitNewInstanceMethod(
            BytecodeEmitContext parentContext, CtClass contentClass, CtClass declaringClass) throws Exception {
        var localContext = new BytecodeEmitContext(parentContext, declaringClass, 2, -1);
        localContext.getOutput()
            .anew(contentClass)
            .dup()
            .aload(1)
            .checkcast(itemType.jvmType())
            .invokespecial(contentClass, MethodInfo.nameInit, Descriptors.constructor(itemType.jvmType()))
            .areturn();

        CtMethod newInstanceMethod = new CtMethod(
            Classes.NodeType(), "newInstance", new CtClass[] {Classes.ObjectType()}, declaringClass);
        newInstanceMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        newInstanceMethod.getMethodInfo().setCodeAttribute(localContext.getOutput().toCodeAttribute());
        newInstanceMethod.getMethodInfo().rebuildStackMap(declaringClass.getClassPool());
        declaringClass.addMethod(newInstanceMethod);
    }

    private void emitTemplateContentClass(BytecodeEmitContext context, CtClass contentClass) throws Exception {
        emitForceInitMethod(context, contentClass);
        emitConstructor(context, contentClass);
    }

    private void emitForceInitMethod(BytecodeEmitContext parentContext, CtClass declaringClass) throws Exception {
        var context = new BytecodeEmitContext(parentContext, declaringClass, 1, -1);
        Bytecode code = context.getOutput().vreturn();

        CtMethod forceInitMethod = new CtMethod(CtClass.voidType, "forceInit", new CtClass[0], declaringClass);
        forceInitMethod.setModifiers(Modifier.STATIC | Modifier.FINAL);
        forceInitMethod.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        forceInitMethod.getMethodInfo().rebuildStackMap(declaringClass.getClassPool());
        declaringClass.addMethod(forceInitMethod);
    }

    private void emitConstructor(BytecodeEmitContext parentContext, CtClass declaringClass) throws Exception {
        boolean needsContext = RuntimeContextHelper.needsRuntimeContext(content);
        int occupiedLocals = TypeHelper.getSlots(itemType.jvmType()) + (needsContext ? 2 : 1);

        var context = new BytecodeEmitContext(
            parentContext, declaringClass, content, occupiedLocals, needsContext ? occupiedLocals - 1 : -1);

        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(declaringClass.getSuperclass(), MethodInfo.nameInit, "()V");

        context.emitRootNode();

        code.vreturn();

        CtConstructor constructor = new CtConstructor(new CtClass[] {itemType.jvmType()}, declaringClass);
        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(declaringClass.getClassPool());
        declaringClass.addConstructor(constructor);
    }

}
