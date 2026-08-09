// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

public class UnaryOperatorNode extends DerivedTextNode {

    private final UnaryOperator operator;
    private final SourceInfo operatorSourceInfo;
    private ValueNode operand;

    public UnaryOperatorNode(
            UnaryOperator operator,
            ValueNode operand,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.operand = checkNotNull(operand);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    private UnaryOperatorNode(
            UnaryOperator operator,
            ValueNode operand,
            SourceInfo operatorSourceInfo,
            TypeNode type,
            SourceInfo sourceInfo) {
        super(type, sourceInfo);
        this.operator = checkNotNull(operator);
        this.operand = checkNotNull(operand);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    public UnaryOperator getOperator() {
        return operator;
    }

    public ValueNode getOperand() {
        return operand;
    }

    public SourceInfo getOperatorSourceInfo() {
        return operatorSourceInfo;
    }

    @Override
    public String formatText() {
        return operator.getSymbol() + formatValue(operand);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        operand = (ValueNode)operand.accept(visitor);
    }

    @Override
    public UnaryOperatorNode deepClone() {
        return new UnaryOperatorNode(
            operator, operand.deepClone(), operatorSourceInfo,
            getType().deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        UnaryOperatorNode that = (UnaryOperatorNode)o;
        return operator == that.operator
            && operand.equals(that.operand)
            && operatorSourceInfo.equals(that.operatorSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), operator, operand, operatorSourceInfo);
    }
}
