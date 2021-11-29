// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.RootNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.ObjectType;
import static org.jfxcore.compiler.util.Descriptors.constructor;

/**
 * Emits the bytecodes that load and initialize all scene graph nodes of a root node.
 */
public class EmitInitializeRootNode extends AbstractNode implements RootNode, EmitterNode {

    private final boolean templateRoot;
    private final List<Node> preamble;
    private Node root;

    public EmitInitializeRootNode(Node root, boolean templateRoot, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.templateRoot = templateRoot;
        this.preamble = new ArrayList<>();
        this.root = checkNotNull(root);
    }

    private EmitInitializeRootNode(
            Node root, Collection<? extends Node> preamble, boolean templateRoot, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.templateRoot = templateRoot;
        this.preamble = new ArrayList<>(checkNotNull(preamble));
        this.root = checkNotNull(root);
    }

    public boolean isTemplateRoot() {
        return templateRoot;
    }

    @Override
    public List<Node> getPreamble() {
        return preamble;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        boolean needsContext = ContextHelper.needsContext(this);

        if (needsContext) {
            GetMaxDepthVisitor maxDepthVisitor = new GetMaxDepthVisitor();
            Visitor.visit(root, maxDepthVisitor);

            code.anew(context.getRuntimeContextClass())
                .dup()
                .aload(0)
                .iconst(maxDepthVisitor.getMaxDepth())
                .invokespecial(
                    context.getRuntimeContextClass(),
                    MethodInfo.nameInit,
                    constructor(ObjectType(), CtClass.intType))
                .astore(context.getRuntimeContextLocal());
        }

        for (Node child : this.preamble) {
            context.emit(child);
        }

        context.emit(root);

        code.vreturn();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        root = root.accept(visitor);
    }

    @Override
    public EmitInitializeRootNode deepClone() {
        return new EmitInitializeRootNode(root.deepClone(), deepClone(preamble), templateRoot, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitInitializeRootNode that = (EmitInitializeRootNode)o;
        return preamble.equals(that.preamble) && root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preamble, root);
    }

}

