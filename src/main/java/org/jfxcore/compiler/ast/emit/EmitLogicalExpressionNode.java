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
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.BooleanConversionHelper;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Objects;

/**
 * Emits boolean coercion and short-circuit control flow in a compiled-expression helper.
 */
public final class EmitLogicalExpressionNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final @Nullable BinaryOperator binaryOperator;
    private final @Nullable UnaryOperator unaryOperator;
    private final ResolvedTypeNode type;

    private ValueEmitterNode left;
    private @Nullable ValueEmitterNode right;

    @SuppressWarnings("NullableProblems")
    public EmitLogicalExpressionNode(
            BinaryOperator operator,
            ValueEmitterNode left,
            ValueEmitterNode right,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (operator != BinaryOperator.LOGICAL_AND && operator != BinaryOperator.LOGICAL_OR) {
            throw new IllegalArgumentException(operator.name());
        }

        this.binaryOperator = operator;
        this.unaryOperator = null;
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.type = new ResolvedTypeNode(TypeInstance.booleanType(), sourceInfo);
    }

    @SuppressWarnings("NullableProblems")
    public EmitLogicalExpressionNode(
            UnaryOperator operator,
            ValueEmitterNode operand,
            SourceInfo sourceInfo) {
        super(sourceInfo);

        if (operator != UnaryOperator.NOT && operator != UnaryOperator.BOOLIFY) {
            throw new IllegalArgumentException(operator.name());
        }

        this.binaryOperator = null;
        this.unaryOperator = operator;
        this.left = checkNotNull(operand);
        this.right = null;
        this.type = new ResolvedTypeNode(TypeInstance.booleanType(), sourceInfo);
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

        if (unaryOperator != null) {
            emitBoolean(context, left, unaryOperator == UnaryOperator.NOT);
        } else if (binaryOperator == BinaryOperator.LOGICAL_AND) {
            emitBoolean(context, left, false);
            code.ifeq(
                () -> code.iconst(0),
                () -> emitBoolean(context, right, false));
        } else {
            emitBoolean(context, left, false);
            code.ifne(
                () -> code.iconst(1),
                () -> emitBoolean(context, right, false));
        }
    }

    private void emitBoolean(BytecodeEmitContext context, ValueEmitterNode operand, boolean invert) {
        context.emit(operand);
        BooleanConversionHelper.emit(
            context.getOutput(), TypeHelper.getTypeDeclaration(operand), invert);
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
    public EmitLogicalExpressionNode deepClone() {
        return binaryOperator != null
            ? new EmitLogicalExpressionNode(binaryOperator, left.deepClone(), right.deepClone(), getSourceInfo()).copy(this)
            : new EmitLogicalExpressionNode(unaryOperator, left.deepClone(), getSourceInfo()).copy(this);

    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof EmitLogicalExpressionNode that
            && binaryOperator == that.binaryOperator
            && unaryOperator == that.unaryOperator
            && left.equals(that.left)
            && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binaryOperator, unaryOperator, left, right);
    }
}
