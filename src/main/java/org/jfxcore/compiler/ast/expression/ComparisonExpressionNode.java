// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitComparisonExpressionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * A relational, value-equality, or identity-equality operation within a compiled-expression island.
 */
public final class ComparisonExpressionNode extends AbstractNode implements AnalyzedExpressionNode {

    private enum Strategy {
        NUMERIC_RELATION,
        COMPARABLE_RELATION,
        NUMERIC_EQUALITY,
        BOOLEAN_EQUALITY,
        OBJECT_EQUALITY,
        IDENTITY_EQUALITY
    }

    private record Analysis(
        Strategy strategy,
        TypeInstance leftType,
        TypeInstance rightType,
        @Nullable TypeDeclaration leftPrimitive,
        @Nullable TypeDeclaration rightPrimitive,
        @Nullable TypeDeclaration promotedType) {}

    private final BinaryOperator operator;
    private final SourceInfo operatorSourceInfo;
    private AnalyzedExpressionNode left;
    private AnalyzedExpressionNode right;

    public ComparisonExpressionNode(
            BinaryOperator operator,
            AnalyzedExpressionNode left,
            AnalyzedExpressionNode right,
            SourceInfo operatorSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        switch (operator) {
            case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
                 VALUE_EQUAL, VALUE_NOT_EQUAL, IDENTITY_EQUAL, IDENTITY_NOT_EQUAL -> {}
            default -> throw new IllegalArgumentException(operator.name());
        }

        this.operator = operator;
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.operatorSourceInfo = checkNotNull(operatorSourceInfo);
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public AnalyzedExpressionNode getLeft() {
        return left;
    }

    public AnalyzedExpressionNode getRight() {
        return right;
    }

    public boolean isRelational() {
        return switch (operator) {
            case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL -> true;
            default -> false;
        };
    }

    @Override
    public TypeInstance analyze(ExpressionAnalysisContext context) {
        if (isRelational() && isRelationalResult(left)) {
            throw GeneralErrors.chainedRelation(operatorSourceInfo);
        }

        TypeInstance leftType = context.analyze(left);
        TypeInstance rightType = context.analyze(right);
        Analysis analysis;

        if (isRelational()) {
            analysis = analyzeRelation(leftType, rightType);
        } else if (operator == BinaryOperator.VALUE_EQUAL
                || operator == BinaryOperator.VALUE_NOT_EQUAL) {
            analysis = analyzeValueEquality(leftType, rightType);
        } else {
            analysis = analyzeIdentityEquality(leftType, rightType);
        }

        context.putAnalysisData(this, analysis);
        return TypeInstance.booleanType();
    }

    private Analysis analyzeRelation(TypeInstance leftType, TypeInstance rightType) {
        if (isNull(leftType)) {
            throw GeneralErrors.invalidRelationalOperands(
                left.getSourceInfo(), operator.getSymbol(), leftType, rightType);
        }

        if (isNull(rightType)) {
            throw GeneralErrors.invalidRelationalOperands(
                right.getSourceInfo(), operator.getSymbol(), leftType, rightType);
        }

        TypeDeclaration leftPrimitive = TypeHelper.getExactNumericPrimitive(leftType);
        TypeDeclaration rightPrimitive = TypeHelper.getExactNumericPrimitive(rightType);

        if (leftPrimitive != null && rightPrimitive != null) {
            return new Analysis(
                Strategy.NUMERIC_RELATION,
                leftType,
                rightType,
                leftPrimitive,
                rightPrimitive,
                TypeHelper.promoteNumeric(leftPrimitive, rightPrimitive));
        }

        TypeInstance comparable = !leftType.isPrimitive()
            ? TypeHelper.findSuperType(leftType, ComparableDecl())
            : null;

        if (comparable == null) {
            throw GeneralErrors.invalidRelationalOperands(
                getSourceInfo(), operator.getSymbol(), leftType, rightType);
        }

        if (comparable.isRaw() || comparable.arguments().size() != 1) {
            throw GeneralErrors.rawComparableOperand(
                left.getSourceInfo(), operator.getSymbol(), leftType);
        }

        TypeInstance parameterType = comparable.arguments().get(0);

        if (parameterType.wildcardType() == TypeInstance.WildcardType.ANY
                || parameterType.wildcardType() == TypeInstance.WildcardType.UPPER
                || !parameterType.isAssignableFrom(rightType)) {
            throw GeneralErrors.invalidRelationalOperands(
                right.getSourceInfo(), operator.getSymbol(), leftType, rightType);
        }

        return new Analysis(Strategy.COMPARABLE_RELATION, leftType, rightType, null, null, null);
    }

    private Analysis analyzeValueEquality(TypeInstance leftType, TypeInstance rightType) {
        TypeDeclaration leftPrimitive = TypeHelper.getExactNumericPrimitive(leftType);
        TypeDeclaration rightPrimitive = TypeHelper.getExactNumericPrimitive(rightType);

        if (leftPrimitive != null && rightPrimitive != null) {
            return new Analysis(
                Strategy.NUMERIC_EQUALITY,
                leftType,
                rightType,
                leftPrimitive,
                rightPrimitive,
                TypeHelper.promoteNumeric(leftPrimitive, rightPrimitive));
        }

        if (isBoolean(leftType) && isBoolean(rightType)) {
            return new Analysis(
                Strategy.BOOLEAN_EQUALITY,
                leftType,
                rightType,
                booleanDecl(),
                booleanDecl(),
                booleanDecl());
        }

        return new Analysis(Strategy.OBJECT_EQUALITY, leftType, rightType, null, null, null);
    }

    private Analysis analyzeIdentityEquality(TypeInstance leftType, TypeInstance rightType) {
        if (leftType.isPrimitive() || rightType.isPrimitive()
                || !TypeHelper.areReferenceIdentityComparable(leftType, rightType)) {
            SourceInfo sourceInfo = leftType.isPrimitive()
                ? left.getSourceInfo()
                : rightType.isPrimitive() ? right.getSourceInfo() : getSourceInfo();

            throw GeneralErrors.invalidIdentityOperands(sourceInfo, operator.getSymbol(), leftType, rightType);
        }

        return new Analysis(Strategy.IDENTITY_EQUALITY, leftType, rightType, null, null, null);
    }

    @Override
    public ValueEmitterNode toEmitter(ExpressionAnalysisContext context) {
        Analysis analysis = context.getAnalysisData(this, Analysis.class);

        EmitComparisonExpressionNode.Strategy strategy = switch (analysis.strategy()) {
            case NUMERIC_RELATION -> EmitComparisonExpressionNode.Strategy.NUMERIC_RELATION;
            case COMPARABLE_RELATION -> EmitComparisonExpressionNode.Strategy.COMPARABLE_RELATION;
            case NUMERIC_EQUALITY -> EmitComparisonExpressionNode.Strategy.NUMERIC_EQUALITY;
            case BOOLEAN_EQUALITY -> EmitComparisonExpressionNode.Strategy.BOOLEAN_EQUALITY;
            case OBJECT_EQUALITY -> EmitComparisonExpressionNode.Strategy.OBJECT_EQUALITY;
            case IDENTITY_EQUALITY -> EmitComparisonExpressionNode.Strategy.IDENTITY_EQUALITY;
        };

        return new EmitComparisonExpressionNode(
            operator,
            strategy,
            left.toEmitter(context),
            right.toEmitter(context),
            analysis.leftType(),
            analysis.rightType(),
            analysis.leftPrimitive(),
            analysis.rightPrimitive(),
            analysis.promotedType(),
            getSourceInfo());
    }

    private boolean isRelationalResult(AnalyzedExpressionNode node) {
        while (node instanceof GroupExpressionNode group) {
            node = group.getOperand();
        }

        return node instanceof ComparisonExpressionNode comparison && comparison.isRelational();
    }

    private boolean isBoolean(TypeInstance type) {
        return !type.isArray()
            && (type.equals(booleanDecl()) || type.equals(BooleanDecl()));
    }

    private boolean isNull(TypeInstance type) {
        return type.equals(TypeInstance.nullType());
    }

    @Override
    public int getBindingDistance() {
        return Math.min(left.getBindingDistance(), right.getBindingDistance());
    }

    @Override
    public SourceInfo getFirstOperatorSourceInfo() {
        SourceInfo result = operatorSourceInfo;
        result = earlier(result, left.getFirstOperatorSourceInfo());
        result = earlier(result, right.getFirstOperatorSourceInfo());
        return result;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        left = (AnalyzedExpressionNode)left.accept(visitor);
        right = (AnalyzedExpressionNode)right.accept(visitor);
    }

    @Override
    public ComparisonExpressionNode deepClone() {
        return new ComparisonExpressionNode(
            operator,
            left.deepClone(),
            right.deepClone(),
            operatorSourceInfo,
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ComparisonExpressionNode that
            && operator == that.operator
            && operatorSourceInfo.equals(that.operatorSourceInfo)
            && left.equals(that.left)
            && right.equals(that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, operatorSourceInfo, left, right);
    }

    private static SourceInfo earlier(SourceInfo first, @Nullable SourceInfo second) {
        if (second == null) {
            return first;
        }

        return first.getStart().compareTo(second.getStart()) <= 0 ? first : second;
    }
}
