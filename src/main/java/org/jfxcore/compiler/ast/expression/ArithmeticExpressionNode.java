// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitArithmeticExpressionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.UnaryOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

/**
 * A numeric unary or binary operation within a compiled-expression island.
 */
public final class ArithmeticExpressionNode extends AbstractNode implements AnalyzedExpressionNode {

    private record Analysis(
        TypeDeclaration leftType,
        @Nullable TypeDeclaration rightType,
        TypeDeclaration resultType) {}

    private final @Nullable BinaryOperator binaryOperator;
    private final @Nullable UnaryOperator unaryOperator;
    private final SourceInfo operatorSourceInfo;

    private AnalyzedExpressionNode left;
    private @Nullable AnalyzedExpressionNode right;

    @SuppressWarnings("NullableProblems")
    public ArithmeticExpressionNode(
            BinaryOperator operator,
            AnalyzedExpressionNode left,
            AnalyzedExpressionNode right,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (operator != BinaryOperator.ADD
                && operator != BinaryOperator.SUBTRACT
                && operator != BinaryOperator.MULTIPLY
                && operator != BinaryOperator.DIVIDE) {
            throw new IllegalArgumentException(operator.name());
        }

        this.binaryOperator = operator;
        this.unaryOperator = null;
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    @SuppressWarnings("NullableProblems")
    public ArithmeticExpressionNode(
            UnaryOperator operator,
            AnalyzedExpressionNode operand,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (operator != UnaryOperator.PLUS && operator != UnaryOperator.MINUS) {
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
    @SuppressWarnings("DataFlowIssue")
    public TypeInstance analyze(ExpressionAnalysisContext context) {
        context.analyze(left);

        if (binaryOperator != null) {
            context.analyze(right);
            TypeDeclaration leftType = context.requireNumeric(left, left.getSourceInfo(), binaryOperator.getSymbol());
            TypeDeclaration rightType = context.requireNumeric(right, right.getSourceInfo(), binaryOperator.getSymbol());
            TypeDeclaration resultType = binaryPromotion(leftType, rightType);
            context.putAnalysisData(this, new Analysis(leftType, rightType, resultType));
            return TypeInstance.of(resultType);
        }

        TypeDeclaration operandType = context.requireNumeric(left, left.getSourceInfo(), unaryOperator.getSymbol());
        TypeDeclaration resultType = unaryPromotion(operandType);
        context.putAnalysisData(this, new Analysis(operandType, null, resultType));
        return TypeInstance.of(resultType);
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public ValueEmitterNode toEmitter(ExpressionAnalysisContext context) {
        Analysis analysis = context.getAnalysisData(this, Analysis.class);

        return binaryOperator != null
            ? new EmitArithmeticExpressionNode(
                binaryOperator,
                left.toEmitter(context),
                right.toEmitter(context),
                analysis.leftType(),
                analysis.rightType(),
                analysis.resultType(),
                getSourceInfo())
            : new EmitArithmeticExpressionNode(
                unaryOperator,
                left.toEmitter(context),
                analysis.leftType(),
                analysis.resultType(),
                getSourceInfo());

    }

    @Override
    public int getBindingDistance() {
        int result = left.getBindingDistance();
        if (right != null) {
            result = Math.min(result, right.getBindingDistance());
        }

        return result;
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
    @SuppressWarnings("DataFlowIssue")
    public ArithmeticExpressionNode deepClone() {
        return binaryOperator != null
            ? new ArithmeticExpressionNode(
                binaryOperator,
                left.deepClone(),
                right.deepClone(),
                operatorSourceInfo,
                getSourceInfo()).copy(this)
            : new ArithmeticExpressionNode(
                unaryOperator,
                left.deepClone(),
                operatorSourceInfo,
                getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ArithmeticExpressionNode that
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

    public static TypeDeclaration unaryPromotion(TypeDeclaration type) {
        return TypeHelper.promoteNumeric(type);
    }

    public static TypeDeclaration binaryPromotion(TypeDeclaration left, TypeDeclaration right) {
        return TypeHelper.promoteNumeric(left, right);
    }

    private static SourceInfo earlier(SourceInfo first, @Nullable SourceInfo second) {
        if (second == null) {
            return first;
        }

        return first.getStart().compareTo(second.getStart()) <= 0 ? first : second;
    }
}
