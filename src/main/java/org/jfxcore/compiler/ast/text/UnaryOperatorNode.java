// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

public final class UnaryOperatorNode extends AbstractSyntaxNode {

    private final UnaryOperator operator;
    private final SourceInfo operatorSourceInfo;
    private SyntaxNode operand;

    public UnaryOperatorNode(
            UnaryOperator operator,
            SyntaxNode operand,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.operand = checkNotNull(operand);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    public UnaryOperator getOperator() {
        return operator;
    }

    public SyntaxNode getOperand() {
        return operand;
    }

    public SourceInfo getOperatorSourceInfo() {
        return operatorSourceInfo;
    }

    @Override
    public String format() {
        return operator.getSymbol() + format(operand);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        operand = (SyntaxNode)operand.accept(visitor);
    }

    @Override
    public UnaryOperatorNode deepClone() {
        return new UnaryOperatorNode(
            operator, operand.deepClone(), operatorSourceInfo, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof UnaryOperatorNode other
            && operator == other.operator && operand.equals(other.operand)
            && operatorSourceInfo.equals(other.operatorSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, operand, operatorSourceInfo);
    }
}
