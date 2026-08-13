// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Semantic invocation whose target has not yet been resolved as either a method or a constructor.
 */
public final class InvocationExpressionNode extends AbstractNode implements ExpressionNode {

    private final TypeDeclaration invocationContext;
    private final BindingOperator operator;
    private final List<Node> arguments;
    private @Nullable PathExpressionNode pathTarget;
    private @Nullable ExpressionNode receiver;
    private @Nullable TextSegmentNode selectedTarget;
    private @Nullable PathExpressionNode inversePath;

    public InvocationExpressionNode(
            TypeDeclaration invocationContext,
            BindingOperator operator,
            PathExpressionNode pathTarget,
            Collection<? extends Node> arguments,
            @Nullable PathExpressionNode inversePath,
            SourceInfo sourceInfo) {
        this(invocationContext, operator, pathTarget, null, null, arguments, inversePath, sourceInfo);
    }

    public InvocationExpressionNode(
            TypeDeclaration invocationContext,
            BindingOperator operator,
            ExpressionNode receiver,
            TextSegmentNode selectedTarget,
            Collection<? extends Node> arguments,
            @Nullable PathExpressionNode inversePath,
            SourceInfo sourceInfo) {
        this(invocationContext, operator, null, receiver, selectedTarget, arguments, inversePath, sourceInfo);
    }

    private InvocationExpressionNode(
            TypeDeclaration invocationContext,
            BindingOperator operator,
            @Nullable PathExpressionNode pathTarget,
            @Nullable ExpressionNode receiver,
            @Nullable TextSegmentNode selectedTarget,
            Collection<? extends Node> arguments,
            @Nullable PathExpressionNode inversePath,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.invocationContext = checkNotNull(invocationContext);
        this.operator = checkNotNull(operator);
        this.pathTarget = pathTarget;
        this.receiver = receiver;
        this.selectedTarget = selectedTarget;
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.inversePath = inversePath;

        if ((pathTarget == null) == (receiver == null || selectedTarget == null)) {
            throw new IllegalArgumentException("target");
        }
    }

    public TypeDeclaration getInvocationContext() {
        return invocationContext;
    }

    public BindingOperator getOperator() {
        return operator;
    }

    public @Nullable PathExpressionNode getPathTarget() {
        return pathTarget;
    }

    public @Nullable ExpressionNode getReceiver() {
        return receiver;
    }

    public @Nullable TextSegmentNode getSelectedTarget() {
        return selectedTarget;
    }

    public List<Node> getArguments() {
        return arguments;
    }

    public @Nullable PathExpressionNode getInversePath() {
        return inversePath;
    }

    public TextSegmentNode getTerminalSegment() {
        if (selectedTarget != null) {
            return selectedTarget;
        }

        var segments = pathTarget.getSegments();
        return (TextSegmentNode)segments.get(segments.size() - 1);
    }

    @Override
    public int getBindingDistance() {
        int result = pathTarget != null
            ? pathTarget.getBindingDistance()
            : receiver.getBindingDistance();

        if (inversePath != null) {
            result = Math.min(result, inversePath.getBindingDistance());
        }

        for (Node argument : arguments) {
            result = Math.min(result, ExpressionNode.bindingDistance(argument));
        }

        return result;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);

        if (pathTarget != null) {
            pathTarget = (PathExpressionNode)pathTarget.accept(visitor);
        }

        if (receiver != null) {
            receiver = (ExpressionNode)receiver.accept(visitor);
            selectedTarget = (TextSegmentNode)selectedTarget.accept(visitor);
        }

        acceptChildren(arguments, visitor, Node.class);

        if (inversePath != null) {
            inversePath = (PathExpressionNode)inversePath.accept(visitor);
        }
    }

    @Override
    public InvocationExpressionNode deepClone() {
        return new InvocationExpressionNode(
            invocationContext,
            operator,
            pathTarget != null ? pathTarget.deepClone() : null,
            receiver != null ? receiver.deepClone() : null,
            selectedTarget != null ? selectedTarget.deepClone() : null,
            deepClone(arguments),
            inversePath != null ? inversePath.deepClone() : null,
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof InvocationExpressionNode that
            && invocationContext.equals(that.invocationContext)
            && operator == that.operator
            && Objects.equals(pathTarget, that.pathTarget)
            && Objects.equals(receiver, that.receiver)
            && Objects.equals(selectedTarget, that.selectedTarget)
            && arguments.equals(that.arguments)
            && Objects.equals(inversePath, that.inversePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            invocationContext, operator, pathTarget, receiver,
            selectedTarget, arguments, inversePath);
    }
}
