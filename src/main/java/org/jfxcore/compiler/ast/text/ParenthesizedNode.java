// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

public final class ParenthesizedNode extends AbstractSyntaxNode {

    private final SourceInfo openParenSourceInfo;
    private final SourceInfo closeParenSourceInfo;
    private SyntaxNode operand;

    public ParenthesizedNode(
            SyntaxNode operand, SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operand = checkNotNull(operand);
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    public SyntaxNode getOperand() { return operand; }
    public SourceInfo getOpenParenSourceInfo() { return openParenSourceInfo; }
    public SourceInfo getCloseParenSourceInfo() { return closeParenSourceInfo; }
    @Override
    public String format() {
        return "(" + format(operand) + ")";
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        operand = (SyntaxNode)operand.accept(visitor);
    }

    @Override
    public ParenthesizedNode deepClone() {
        return new ParenthesizedNode(
            operand.deepClone(), openParenSourceInfo, closeParenSourceInfo, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ParenthesizedNode other
            && operand.equals(other.operand)
            && openParenSourceInfo.equals(other.openParenSourceInfo)
            && closeParenSourceInfo.equals(other.closeParenSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operand, openParenSourceInfo, closeParenSourceInfo);
    }
}
