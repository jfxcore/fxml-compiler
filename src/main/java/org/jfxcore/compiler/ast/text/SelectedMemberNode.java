// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * A named member selected from an arbitrary postfix receiver.
 */
public final class SelectedMemberNode extends AbstractSyntaxNode {

    private SyntaxNode receiver;
    private TextSegmentNode member;

    public SelectedMemberNode(SyntaxNode receiver, TextSegmentNode member, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.receiver = checkNotNull(receiver);
        this.member = checkNotNull(member);

        if (member.getSelectorSourceInfo() == null) {
            throw new IllegalArgumentException("member.selectorSourceInfo");
        }
    }

    public SyntaxNode getReceiver() {
        return receiver;
    }

    public TextSegmentNode getMember() {
        return member;
    }

    @Override
    public String format() {
        return format(receiver) + (member.isObservableSelector() ? "::" : ".") + member.format();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        receiver = (SyntaxNode)receiver.accept(visitor);
        member = (TextSegmentNode)member.accept(visitor);
    }

    @Override
    public SelectedMemberNode deepClone() {
        return new SelectedMemberNode(receiver.deepClone(), member.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SelectedMemberNode other
            && receiver.equals(other.receiver) && member.equals(other.member);
    }

    @Override
    public int hashCode() { return Objects.hash(receiver, member); }
}
