// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Source-level invocation whose target has not yet been resolved as either a method or a constructor.
 */
public final class InvocationNode extends AbstractSyntaxNode {

    private final List<Node> arguments;
    private final SourceInfo openParenSourceInfo;
    private final SourceInfo closeParenSourceInfo;
    private SyntaxNode target;

    public InvocationNode(
            SyntaxNode target,
            Collection<? extends Node> arguments,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.target = checkTarget(target);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    public SyntaxNode getTarget() {
        return target;
    }

    public List<Node> getArguments() {
        return arguments;
    }

    public SourceInfo getOpenParenSourceInfo() {
        return openParenSourceInfo;
    }

    public SourceInfo getCloseParenSourceInfo() {
        return closeParenSourceInfo;
    }

    @Override
    public String format() {
        return format(target) + "(" + arguments.stream()
            .map(AbstractSyntaxNode::format)
            .collect(Collectors.joining(",")) + ")";
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        target = checkTarget((SyntaxNode)target.accept(visitor));
        acceptChildren(arguments, visitor, Node.class);
    }

    @Override
    public InvocationNode deepClone() {
        return new InvocationNode(
            target.deepClone(), deepClone(arguments), openParenSourceInfo,
            closeParenSourceInfo, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof InvocationNode other && target.equals(other.target)
            && arguments.equals(other.arguments)
            && openParenSourceInfo.equals(other.openParenSourceInfo)
            && closeParenSourceInfo.equals(other.closeParenSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, arguments, openParenSourceInfo, closeParenSourceInfo);
    }

    private static SyntaxNode checkTarget(SyntaxNode target) {
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
