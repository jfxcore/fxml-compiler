// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

public class ParenthesizedNode extends DerivedTextNode {

    private final SourceInfo openParenSourceInfo;
    private final SourceInfo closeParenSourceInfo;
    private ValueNode operand;

    public ParenthesizedNode(
            ValueNode operand,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operand = checkNotNull(operand);
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    private ParenthesizedNode(
            ValueNode operand,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo,
            TypeNode type,
            SourceInfo sourceInfo) {
        super(type, sourceInfo);
        this.operand = checkNotNull(operand);
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    public ValueNode getOperand() {
        return operand;
    }

    public SourceInfo getOpenParenSourceInfo() {
        return openParenSourceInfo;
    }

    public SourceInfo getCloseParenSourceInfo() {
        return closeParenSourceInfo;
    }

    @Override
    public String formatText() {
        return "(" + formatValue(operand) + ")";
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        operand = (ValueNode)operand.accept(visitor);
    }

    @Override
    public ParenthesizedNode deepClone() {
        return new ParenthesizedNode(
            operand.deepClone(), openParenSourceInfo, closeParenSourceInfo,
            getType().deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ParenthesizedNode that = (ParenthesizedNode)o;
        return operand.equals(that.operand)
            && openParenSourceInfo.equals(that.openParenSourceInfo)
            && closeParenSourceInfo.equals(that.closeParenSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), operand, openParenSourceInfo, closeParenSourceInfo);
    }
}
