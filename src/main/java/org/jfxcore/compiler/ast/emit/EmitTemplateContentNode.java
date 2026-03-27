// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.RootNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.Types;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jfxcore.compiler.type.Types.*;

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
        TypeDeclaration oldContextClass = context.getBindingContextClass();
        TypeDeclaration newContextClass = bindingContextClass.declaration();

        try {
            context.getNestedClasses().add(newContextClass);
            context.setBindingContextClass(newContextClass);

            TypeDeclaration factoryClass = unchecked(() -> {
                emitTemplateContentClass(context, newContextClass);
                return emitFactoryClass(context, newContextClass);
            });

            context.getOutput()
                .anew(factoryClass)
                .dup()
                .invoke(factoryClass.requireConstructor());
        } finally {
            context.setBindingContextClass(oldContextClass);
        }
    }

    private TypeDeclaration emitFactoryClass(BytecodeEmitContext context, TypeDeclaration contentClass) throws Exception {
        TypeDeclaration type = context.getMarkupClass().createNestedClass(
            NameHelper.getUniqueName("TemplateContentFactory", this));

        type.setModifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL)
            .addInterface(Types.Core.TemplateContentDecl())
            .createDefaultConstructor();

        context.getNestedClasses().add(type);

        emitForceInitMethod(context, type);
        emitNewInstanceMethod(context, contentClass, type);
        return type;
    }

    private void emitNewInstanceMethod(
            BytecodeEmitContext parentContext, TypeDeclaration contentClass, TypeDeclaration declaringClass) throws Exception {
        var localContext = new BytecodeEmitContext(parentContext, declaringClass, 2, -1);
        localContext.getOutput()
            .anew(contentClass)
            .dup()
            .aload(1)
            .checkcast(itemType.declaration())
            .invoke(contentClass.requireConstructor(itemType.declaration()))
            .areturn();

        declaringClass.createMethod("newInstance", NodeDecl(), ObjectDecl())
                      .setModifiers(Modifier.PUBLIC | Modifier.FINAL)
                      .setCode(localContext.getOutput());
    }

    private void emitTemplateContentClass(BytecodeEmitContext context, TypeDeclaration contentClass) throws Exception {
        emitForceInitMethod(context, contentClass);
        emitConstructor(context, contentClass);
    }

    private void emitForceInitMethod(BytecodeEmitContext parentContext, TypeDeclaration declaringClass) throws Exception {
        var context = new BytecodeEmitContext(parentContext, declaringClass, 1, -1);
        Bytecode code = context.getOutput().vreturn();

        declaringClass.createMethod("forceInit", voidDecl())
                      .setModifiers(Modifier.STATIC | Modifier.FINAL)
                      .setCode(code);
    }

    private void emitConstructor(BytecodeEmitContext parentContext, TypeDeclaration declaringClass) throws Exception {
        boolean needsContext = RuntimeContextHelper.needsRuntimeContext(content);
        int occupiedLocals = itemType.declaration().slots() + (needsContext ? 2 : 1);

        var context = new BytecodeEmitContext(
            parentContext, declaringClass, content, occupiedLocals, needsContext ? occupiedLocals - 1 : -1);

        Bytecode code = context.getOutput();

        code.aload(0)
            .invoke(declaringClass.requireSuperClass().requireConstructor());

        context.emitRootNode();

        code.vreturn();

        declaringClass.createConstructor(itemType.declaration())
                      .setCode(code);
    }
}
