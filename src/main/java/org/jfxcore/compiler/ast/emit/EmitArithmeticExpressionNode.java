// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.ast.text.UnaryOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Objects;

/**
 * Emits a numeric unary or binary operation in a compiled-expression helper.
 */
public final class EmitArithmeticExpressionNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final @Nullable BinaryOperator binaryOperator;
    private final @Nullable UnaryOperator unaryOperator;
    private final TypeDeclaration leftType;
    private final @Nullable TypeDeclaration rightType;
    private final ResolvedTypeNode type;

    private ValueEmitterNode left;
    private @Nullable ValueEmitterNode right;

    @SuppressWarnings("NullableProblems")
    public EmitArithmeticExpressionNode(
            BinaryOperator operator,
            ValueEmitterNode left,
            ValueEmitterNode right,
            TypeDeclaration leftType,
            TypeDeclaration rightType,
            TypeDeclaration resultType,
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
        this.leftType = checkNotNull(leftType);
        this.rightType = checkNotNull(rightType);
        this.type = new ResolvedTypeNode(TypeInstance.of(checkNotNull(resultType)), sourceInfo);
    }

    @SuppressWarnings("NullableProblems")
    public EmitArithmeticExpressionNode(
            UnaryOperator operator,
            ValueEmitterNode operand,
            TypeDeclaration operandType,
            TypeDeclaration resultType,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (operator != UnaryOperator.PLUS && operator != UnaryOperator.MINUS) {
            throw new IllegalArgumentException(operator.name());
        }

        this.binaryOperator = null;
        this.unaryOperator = operator;
        this.left = checkNotNull(operand);
        this.right = null;
        this.leftType = checkNotNull(operandType);
        this.rightType = null;
        this.type = new ResolvedTypeNode(TypeInstance.of(checkNotNull(resultType)), sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        TypeDeclaration resultType = type.getTypeDeclaration();

        context.emit(left);
        code.primconv(leftType, resultType);

        if (binaryOperator != null) {
            context.emit(right);
            code.primconv(rightType, resultType);

            switch (binaryOperator) {
                case ADD -> code.add(resultType);
                case SUBTRACT -> code.sub(resultType);
                case MULTIPLY -> code.mul(resultType);
                case DIVIDE -> code.div(resultType);
                default -> throw new AssertionError(binaryOperator);
            }
        } else if (unaryOperator == UnaryOperator.MINUS) {
            code.neg(resultType);
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        left = (ValueEmitterNode)left.accept(visitor);
        if (right != null) {
            right = (ValueEmitterNode)right.accept(visitor);
        }
    }

    @Override
    public EmitArithmeticExpressionNode deepClone() {
        return binaryOperator != null
            ? new EmitArithmeticExpressionNode(
                binaryOperator,
                left.deepClone(),
                right.deepClone(),
                leftType,
                rightType,
                type.getTypeDeclaration(),
                getSourceInfo()).copy(this)
            : new EmitArithmeticExpressionNode(
                unaryOperator,
                left.deepClone(),
                leftType,
                type.getTypeDeclaration(),
                getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof EmitArithmeticExpressionNode that
            && binaryOperator == that.binaryOperator
            && unaryOperator == that.unaryOperator
            && leftType.equals(that.leftType)
            && Objects.equals(rightType, that.rightType)
            && type.equals(that.type)
            && left.equals(that.left)
            && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binaryOperator, unaryOperator, leftType, rightType, type, left, right);
    }
}
