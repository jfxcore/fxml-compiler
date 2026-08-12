// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Property-path selection rooted in an arbitrary expression result.
 */
public final class MemberAccessExpressionNode extends AbstractNode implements ExpressionNode {

    private final BindingOperator operator;
    private final List<PathSegmentNode> segments;
    private ExpressionNode receiver;

    public MemberAccessExpressionNode(
            BindingOperator operator,
            ExpressionNode receiver,
            Collection<? extends PathSegmentNode> segments,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.receiver = checkNotNull(receiver);
        this.segments = new ArrayList<>(checkNotNull(segments));

        if (this.segments.isEmpty()) {
            throw new IllegalArgumentException("segments");
        }
    }

    public BindingOperator getOperator() {
        return operator;
    }

    public ExpressionNode getReceiver() {
        return receiver;
    }

    public List<PathSegmentNode> getSegments() {
        return segments;
    }

    @Override
    public int getBindingDistance() {
        return receiver.getBindingDistance();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        receiver = (ExpressionNode)receiver.accept(visitor);
        acceptChildren(segments, visitor, PathSegmentNode.class);
    }

    @Override
    public MemberAccessExpressionNode deepClone() {
        return new MemberAccessExpressionNode(
            operator, receiver.deepClone(), deepClone(segments), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof MemberAccessExpressionNode that
            && operator == that.operator
            && receiver.equals(that.receiver)
            && segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, receiver, segments);
    }

}
