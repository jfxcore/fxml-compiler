// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

public class BinaryOperatorNode extends DerivedTextNode {

    private final BinaryOperator operator;
    private final SourceInfo operatorSourceInfo;
    private ValueNode left;
    private ValueNode right;

    public BinaryOperatorNode(
            BinaryOperator operator,
            ValueNode left,
            ValueNode right,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    private BinaryOperatorNode(
            BinaryOperator operator,
            ValueNode left,
            ValueNode right,
            SourceInfo operatorSourceInfo,
            TypeNode type,
            SourceInfo sourceInfo) {
        super(type, sourceInfo);
        this.operator = checkNotNull(operator);
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public ValueNode getLeft() {
        return left;
    }

    public ValueNode getRight() {
        return right;
    }

    public SourceInfo getOperatorSourceInfo() {
        return operatorSourceInfo;
    }

    @Override
    public String formatText() {
        return formatValue(left) + operator.getSymbol() + formatValue(right);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        left = (ValueNode)left.accept(visitor);
        right = (ValueNode)right.accept(visitor);
    }

    @Override
    public BinaryOperatorNode deepClone() {
        return new BinaryOperatorNode(
            operator, left.deepClone(), right.deepClone(), operatorSourceInfo,
            getType().deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BinaryOperatorNode that = (BinaryOperatorNode)o;
        return operator == that.operator
            && left.equals(that.left)
            && right.equals(that.right)
            && operatorSourceInfo.equals(that.operatorSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), operator, left, right, operatorSourceInfo);
    }

}
