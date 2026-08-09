// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

/**
 * A transparent parenthesized semantic expression.
 */
public final class GroupExpressionNode extends AbstractNode implements AnalyzedExpressionNode {

    private AnalyzedExpressionNode operand;

    public GroupExpressionNode(AnalyzedExpressionNode operand, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operand = checkNotNull(operand);
    }

    public AnalyzedExpressionNode getOperand() {
        return operand;
    }

    @Override
    public TypeInstance analyze(ExpressionAnalysisContext context) {
        TypeInstance type = context.analyze(operand);
        context.alias(this, operand);
        return type;
    }

    @Override
    public ValueEmitterNode toEmitter(ExpressionAnalysisContext context) {
        return operand.toEmitter(context);
    }

    @Override
    public int getBindingDistance() {
        return operand.getBindingDistance();
    }

    @Override
    public @Nullable SourceInfo getFirstOperatorSourceInfo() {
        return operand.getFirstOperatorSourceInfo();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        operand = (AnalyzedExpressionNode)operand.accept(visitor);
    }

    @Override
    public GroupExpressionNode deepClone() {
        return new GroupExpressionNode(operand.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof GroupExpressionNode that && operand.equals(that.operand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operand);
    }
}
