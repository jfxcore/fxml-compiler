// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitLogicalExpressionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.UnaryOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * A boolean-coercion or short-circuit operation within a compiled-expression island.
 */
public final class LogicalExpressionNode extends AbstractNode implements AnalyzedExpressionNode {

    private final @Nullable BinaryOperator binaryOperator;
    private final @Nullable UnaryOperator unaryOperator;
    private final SourceInfo operatorSourceInfo;

    private AnalyzedExpressionNode left;
    private @Nullable AnalyzedExpressionNode right;

    @SuppressWarnings("NullableProblems")
    public LogicalExpressionNode(
            BinaryOperator operator,
            AnalyzedExpressionNode left,
            AnalyzedExpressionNode right,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (operator != BinaryOperator.LOGICAL_AND && operator != BinaryOperator.LOGICAL_OR) {
            throw new IllegalArgumentException(operator.name());
        }

        this.binaryOperator = operator;
        this.unaryOperator = null;
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    @SuppressWarnings("NullableProblems")
    public LogicalExpressionNode(
            UnaryOperator operator,
            AnalyzedExpressionNode operand,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (operator != UnaryOperator.NOT && operator != UnaryOperator.BOOLIFY) {
            throw new IllegalArgumentException(operator.name());
        }

        this.binaryOperator = null;
        this.unaryOperator = operator;
        this.left = checkNotNull(operand);
        this.right = null;
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    public AnalyzedExpressionNode getLeft() {
        return left;
    }

    public @Nullable AnalyzedExpressionNode getRight() {
        return right;
    }

    @Override
    public TypeInstance analyze(ExpressionAnalysisContext context) {
        TypeInstance leftType = context.analyze(left);
        if (binaryOperator != null && !isBoolean(leftType)) {
            throw GeneralErrors.invalidLogicalOperand(
                left.getSourceInfo(), binaryOperator.getSymbol(), leftType);
        }

        if (right != null) {
            TypeInstance rightType = context.analyze(right);
            if (!isBoolean(rightType)) {
                throw GeneralErrors.invalidLogicalOperand(
                    right.getSourceInfo(), binaryOperator.getSymbol(), rightType);
            }
        }

        return TypeInstance.booleanType();
    }

    @Override
    public ValueEmitterNode toEmitter(ExpressionAnalysisContext context) {
        if (unaryOperator != null) {
            return new EmitLogicalExpressionNode(
                unaryOperator, left.toEmitter(context), getSourceInfo());
        }

        return new EmitLogicalExpressionNode(
            binaryOperator,
            left.toEmitter(context),
            right.toEmitter(context),
            getSourceInfo());
    }

    private boolean isBoolean(TypeInstance type) {
        return !type.isArray() && (type.equals(booleanDecl()) || type.equals(BooleanDecl()));
    }

    @Override
    public int getBindingDistance() {
        int result = left.getBindingDistance();
        return right != null ? Math.min(result, right.getBindingDistance()) : result;
    }

    @Override
    public SourceInfo getFirstOperatorSourceInfo() {
        SourceInfo result = operatorSourceInfo;
        result = earlier(result, left.getFirstOperatorSourceInfo());

        if (right != null) {
            result = earlier(result, right.getFirstOperatorSourceInfo());
        }

        return result;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        left = (AnalyzedExpressionNode)left.accept(visitor);
        if (right != null) {
            right = (AnalyzedExpressionNode)right.accept(visitor);
        }
    }

    @Override
    public LogicalExpressionNode deepClone() {
        if (binaryOperator != null) {
            return new LogicalExpressionNode(
                binaryOperator,
                left.deepClone(),
                right.deepClone(),
                operatorSourceInfo,
                getSourceInfo()).copy(this);
        }

        return new LogicalExpressionNode(
            unaryOperator, left.deepClone(), operatorSourceInfo, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof LogicalExpressionNode that
            && binaryOperator == that.binaryOperator
            && unaryOperator == that.unaryOperator
            && operatorSourceInfo.equals(that.operatorSourceInfo)
            && left.equals(that.left)
            && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binaryOperator, unaryOperator, operatorSourceInfo, left, right);
    }

    private static SourceInfo earlier(SourceInfo first, @Nullable SourceInfo second) {
        if (second == null) {
            return first;
        }

        return first.getStart().compareTo(second.getStart()) <= 0 ? first : second;
    }
}
