// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

public final class BinaryOperatorNode extends AbstractSyntaxNode {

    private final BinaryOperator operator;
    private final SourceInfo operatorSourceInfo;
    private SyntaxNode left;
    private SyntaxNode right;

    public BinaryOperatorNode(
            BinaryOperator operator, SyntaxNode left, SyntaxNode right,
            SourceInfo operatorSourceInfo, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public SyntaxNode getLeft() {
        return left;
    }

    public SyntaxNode getRight() {
        return right;
    }

    public SourceInfo getOperatorSourceInfo() {
        return operatorSourceInfo;
    }

    @Override
    public String format() {
        return format(left) + operator.getSymbol() + format(right);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        left = (SyntaxNode)left.accept(visitor);
        right = (SyntaxNode)right.accept(visitor);
    }

    @Override
    public BinaryOperatorNode deepClone() {
        return new BinaryOperatorNode(
            operator, left.deepClone(), right.deepClone(), operatorSourceInfo, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BinaryOperatorNode other
            && operator == other.operator
            && left.equals(other.left)
            && right.equals(other.right)
            && operatorSourceInfo.equals(other.operatorSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, left, right, operatorSourceInfo);
    }
}
