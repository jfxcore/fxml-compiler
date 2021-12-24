// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.StringHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ListNode extends TextNode {

    private final List<ValueNode> values;

    public ListNode(Collection<? extends ValueNode> values, SourceInfo sourceInfo) {
        super(format(values), sourceInfo);
        this.values = new ArrayList<>(AbstractNode.checkNotNull(values));
    }

    private ListNode(Collection<? extends ValueNode> values, TypeNode type, SourceInfo sourceInfo) {
        super(format(values), false, type, sourceInfo);
        this.values = new ArrayList<>(AbstractNode.checkNotNull(values));
    }

    public List<ValueNode> getValues() {
        return values;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        AbstractNode.acceptChildren(values, visitor);
    }

    @Override
    public ListNode deepClone() {
        return new ListNode(deepClone(values), getType(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ListNode listNode = (ListNode) o;
        return Objects.equals(values, listNode.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), values);
    }

    private static String format(Collection<? extends ValueNode> arguments) {
        return StringHelper.concatValues(
            arguments.stream().map(node -> {
                if (node instanceof TextNode) {
                    return ((TextNode)node).getText();
                }

                return node.getType().getMarkupName();
            }).collect(Collectors.toList()));
    }

}
