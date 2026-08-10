// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Source-level invocation whose target is neutral between a method and a constructor.
 */
public final class InvocationNode extends DerivedTextNode {

    private final List<ValueNode> arguments;
    private final SourceInfo openParenSourceInfo;
    private final SourceInfo closeParenSourceInfo;
    private ValueNode target;

    public InvocationNode(
            ValueNode target,
            Collection<? extends ValueNode> arguments,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.target = checkTarget(target);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    private InvocationNode(
            ValueNode target,
            Collection<? extends ValueNode> arguments,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo,
            TypeNode type,
            SourceInfo sourceInfo) {
        super(type, sourceInfo);
        this.target = checkTarget(target);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    public ValueNode getTarget() {
        return target;
    }

    public List<ValueNode> getArguments() {
        return arguments;
    }

    public SourceInfo getOpenParenSourceInfo() {
        return openParenSourceInfo;
    }

    public SourceInfo getCloseParenSourceInfo() {
        return closeParenSourceInfo;
    }

    @Override
    public String formatText() {
        return formatValue(target) + "(" + arguments.stream()
            .map(TextNode::formatValue)
            .collect(Collectors.joining(",")) + ")";
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        target = checkTarget((ValueNode)target.accept(visitor));
        acceptChildren(arguments, visitor, ValueNode.class);
    }

    @Override
    public InvocationNode deepClone() {
        return new InvocationNode(
            target.deepClone(), deepClone(arguments), openParenSourceInfo, closeParenSourceInfo,
            getType().deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o)
            && target.equals(((InvocationNode)o).target)
            && arguments.equals(((InvocationNode)o).arguments)
            && openParenSourceInfo.equals(((InvocationNode)o).openParenSourceInfo)
            && closeParenSourceInfo.equals(((InvocationNode)o).closeParenSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), target, arguments, openParenSourceInfo, closeParenSourceInfo);
    }

    private static ValueNode checkTarget(ValueNode target) {
        checkNotNull(target);

        if (target instanceof SelectedMemberNode) {
            return target;
        }

        if (target instanceof PathNode path && !path.getSegments().isEmpty()
                && path.getSegments().get(path.getSegments().size() - 1) instanceof TextSegmentNode) {
            return target;
        }

        throw new IllegalArgumentException("target");
    }
}
