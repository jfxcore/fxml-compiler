// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * A named member selected from an arbitrary postfix receiver. Without an enclosing
 * {@link InvocationNode}, this node has ordinary property-path semantics.
 */
public final class SelectedMemberNode extends DerivedTextNode {

    private ValueNode receiver;
    private TextSegmentNode member;

    public SelectedMemberNode(ValueNode receiver, TextSegmentNode member, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.receiver = checkNotNull(receiver);
        this.member = checkNotNull(member);

        if (member.getSelectorSourceInfo() == null) {
            throw new IllegalArgumentException("member.selectorSourceInfo");
        }
    }

    private SelectedMemberNode(ValueNode receiver, TextSegmentNode member, TypeNode type, SourceInfo sourceInfo) {
        super(type, sourceInfo);
        this.receiver = checkNotNull(receiver);
        this.member = checkNotNull(member);
    }

    public ValueNode getReceiver() {
        return receiver;
    }

    public TextSegmentNode getMember() {
        return member;
    }

    @Override
    public String formatText() {
        return formatValue(receiver)
            + (member.isObservableSelector() ? "::" : ".")
            + member.formatText();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        receiver = (ValueNode)receiver.accept(visitor);
        member = (TextSegmentNode)member.accept(visitor);
    }

    @Override
    public SelectedMemberNode deepClone() {
        return new SelectedMemberNode(
            receiver.deepClone(), member.deepClone(), getType().deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o)
            && receiver.equals(((SelectedMemberNode)o).receiver)
            && member.equals(((SelectedMemberNode)o).member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), receiver, member);
    }
}
