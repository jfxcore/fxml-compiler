// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.Objects;

/**
 * Owns analysis and one ordered-input helper for a complete compiled-expression island.
 */
public final class CompiledExpressionNode extends AbstractNode implements ExpressionNode {

    private final TypeDeclaration invocationContext;
    private final String sourceName;

    private AnalyzedExpressionNode root;

    public CompiledExpressionNode(
            TypeDeclaration invocationContext,
            String sourceName,
            AnalyzedExpressionNode root,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.invocationContext = checkNotNull(invocationContext);
        this.sourceName = checkNotNull(sourceName);
        this.root = checkNotNull(root);
    }

    public AnalyzedExpressionNode getRoot() {
        return root;
    }

    public TypeDeclaration getInvocationContext() {
        return invocationContext;
    }

    public String getSourceName() {
        return sourceName;
    }

    public SourceInfo getFirstOperatorSourceInfo() {
        SourceInfo sourceInfo = root.getFirstOperatorSourceInfo();
        return sourceInfo != null ? sourceInfo : getSourceInfo();
    }

    @Override
    public int getBindingDistance() {
        return root.getBindingDistance();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        root = (AnalyzedExpressionNode)root.accept(visitor);
    }

    @Override
    public CompiledExpressionNode deepClone() {
        return new CompiledExpressionNode(invocationContext, sourceName, root.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof CompiledExpressionNode that
            && invocationContext.equals(that.invocationContext)
            && sourceName.equals(that.sourceName)
            && root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invocationContext, sourceName, root);
    }
}
