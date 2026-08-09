// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitExpressionInputNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

/**
 * A source-level path, invocation, constructor, or markup object passed to the helper as one input.
 */
public final class ExternalExpressionNode extends AbstractNode implements AnalyzedExpressionNode {

    private Node expression;

    public ExternalExpressionNode(Node expression, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.expression = checkNotNull(expression);
    }

    public Node getExpression() {
        return expression;
    }

    @Override
    public TypeInstance analyze(ExpressionAnalysisContext context) {
        return context.addInput(this, expression);
    }

    @Override
    public ValueEmitterNode toEmitter(ExpressionAnalysisContext context) {
        ExpressionAnalysisContext.Input input = context.getInput(this);
        return new EmitExpressionInputNode(TypeInstance.of(input.parameterType()), input.localIndex(), getSourceInfo());
    }

    @Override
    public int getBindingDistance() {
        return ExpressionNode.bindingDistance(expression);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        expression = checkNotNull(expression.accept(visitor));
    }

    @Override
    public ExternalExpressionNode deepClone() {
        return new ExternalExpressionNode(expression.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ExternalExpressionNode that
            && expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }
}
