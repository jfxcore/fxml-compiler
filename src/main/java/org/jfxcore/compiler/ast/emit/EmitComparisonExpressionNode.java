// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.text.BinaryOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Emits relational, value-equality, and identity-equality operations in an expression helper method.
 */
public final class EmitComparisonExpressionNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    public enum Strategy {
        NUMERIC_RELATION,
        COMPARABLE_RELATION,
        NUMERIC_EQUALITY,
        BOOLEAN_EQUALITY,
        OBJECT_EQUALITY,
        IDENTITY_EQUALITY
    }

    private record StoredOperand(TypeDeclaration type, Local local, boolean nullable) {}

    private final BinaryOperator operator;
    private final Strategy strategy;
    private final TypeInstance leftSourceType;
    private final TypeInstance rightSourceType;
    private final @Nullable TypeDeclaration leftPrimitive;
    private final @Nullable TypeDeclaration rightPrimitive;
    private final @Nullable TypeDeclaration promotedType;
    private final ResolvedTypeNode type;

    private ValueEmitterNode left;
    private ValueEmitterNode right;

    public EmitComparisonExpressionNode(
            BinaryOperator operator,
            Strategy strategy,
            ValueEmitterNode left,
            ValueEmitterNode right,
            TypeInstance leftSourceType,
            TypeInstance rightSourceType,
            @Nullable TypeDeclaration leftPrimitive,
            @Nullable TypeDeclaration rightPrimitive,
            @Nullable TypeDeclaration promotedType,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.operator = checkNotNull(operator);
        this.strategy = checkNotNull(strategy);
        this.left = checkNotNull(left);
        this.right = checkNotNull(right);
        this.leftSourceType = checkNotNull(leftSourceType);
        this.rightSourceType = checkNotNull(rightSourceType);
        this.leftPrimitive = leftPrimitive;
        this.rightPrimitive = rightPrimitive;
        this.promotedType = promotedType;
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

        switch (strategy) {
            case NUMERIC_RELATION -> emitNumericRelation(context, code);
            case COMPARABLE_RELATION -> emitComparableRelation(context, code);
            case NUMERIC_EQUALITY -> emitNumericEquality(context, code);
            case BOOLEAN_EQUALITY -> emitBooleanEquality(context, code);
            case OBJECT_EQUALITY -> emitObjectEquality(context, code);
            case IDENTITY_EQUALITY -> emitIdentityEquality(context, code);
        }
    }

    private void emitNumericRelation(BytecodeEmitContext context, Bytecode code) {
        StoredOperand leftValue = emitAndStore(context, code, left, leftSourceType, false);
        StoredOperand rightValue = emitAndStore(context, code, right, rightSourceType, false);

        emitFalseOnNull(code, leftValue, rightValue, () -> {
            loadNumeric(code, leftValue, leftPrimitive, promotedType);
            loadNumeric(code, rightValue, rightPrimitive, promotedType);
            emitNumericRelationPredicate(code, promotedType);
        });

        release(code, rightValue, leftValue);
    }

    private void emitComparableRelation(BytecodeEmitContext context, Bytecode code) {
        StoredOperand leftValue = emitAndStore(context, code, left, leftSourceType, false);
        StoredOperand rightValue = emitAndStore(context, code, right, rightSourceType, rightSourceType.isPrimitive());

        emitFalseOnNull(code, leftValue, rightValue, () -> {
            code.load(leftValue.type(), leftValue.local());
            code.load(rightValue.type(), rightValue.local());
            code.invoke(ComparableDecl().requireMethod("compareTo", ObjectDecl()));
            emitRelationPredicate(code);
        });

        release(code, rightValue, leftValue);
    }

    private void emitNumericEquality(BytecodeEmitContext context, Bytecode code) {
        StoredOperand leftValue = emitAndStore(context, code, left, leftSourceType, false);
        StoredOperand rightValue = emitAndStore(context, code, right, rightSourceType, false);

        emitNullSafeEquality(code, leftValue, rightValue, () -> {
            loadNumeric(code, leftValue, leftPrimitive, promotedType);
            loadNumeric(code, rightValue, rightPrimitive, promotedType);
            emitNumericEqualityPredicate(code, promotedType);
        });

        negateValueEqualityIfNeeded(code);
        release(code, rightValue, leftValue);
    }

    private void emitBooleanEquality(BytecodeEmitContext context, Bytecode code) {
        StoredOperand leftValue = emitAndStore(context, code, left, leftSourceType, false);
        StoredOperand rightValue = emitAndStore(context, code, right, rightSourceType, false);

        emitNullSafeEquality(code, leftValue, rightValue, () -> {
            loadBoolean(code, leftValue);
            loadBoolean(code, rightValue);
            code.if_icmpeq(() -> code.iconst(1), () -> code.iconst(0));
        });

        negateValueEqualityIfNeeded(code);
        release(code, rightValue, leftValue);
    }

    private void emitObjectEquality(BytecodeEmitContext context, Bytecode code) {
        StoredOperand leftValue = emitAndStore(context, code, left, leftSourceType, leftSourceType.isPrimitive());
        StoredOperand rightValue = emitAndStore(context, code, right, rightSourceType, rightSourceType.isPrimitive());

        code.load(leftValue.type(), leftValue.local());
        code.load(rightValue.type(), rightValue.local());
        code.invoke(ObjectsDecl().requireMethod("equals", ObjectDecl(), ObjectDecl()));

        negateValueEqualityIfNeeded(code);
        release(code, rightValue, leftValue);
    }

    private void emitIdentityEquality(BytecodeEmitContext context, Bytecode code) {
        StoredOperand leftValue = emitAndStore(context, code, left, leftSourceType, false);
        StoredOperand rightValue = emitAndStore(context, code, right, rightSourceType, false);

        code.load(leftValue.type(), leftValue.local());
        code.load(rightValue.type(), rightValue.local());
        code.if_acmpeq(() -> code.iconst(1), () -> code.iconst(0));

        if (operator == BinaryOperator.IDENTITY_NOT_EQUAL) {
            negate(code);
        }

        release(code, rightValue, leftValue);
    }

    private StoredOperand emitAndStore(
            BytecodeEmitContext context,
            Bytecode code,
            ValueEmitterNode node,
            TypeInstance sourceType,
            boolean boxPrimitive) {
        TypeDeclaration storageType = sourceType.declaration();
        context.emit(node);

        if (boxPrimitive) {
            code.box(storageType);
            storageType = storageType.boxed();
        }

        Local local = code.acquireLocal(storageType);
        code.store(storageType, local);
        return new StoredOperand(storageType, local, !sourceType.isPrimitive());
    }

    private void loadNumeric(
            Bytecode code,
            StoredOperand operand,
            TypeDeclaration primitive,
            TypeDeclaration promotedType) {
        code.load(operand.type(), operand.local());

        if (!operand.type().isPrimitive()) {
            code.unbox(operand.type(), primitive);
        }

        code.primconv(primitive, promotedType);
    }

    private void loadBoolean(Bytecode code, StoredOperand operand) {
        code.load(operand.type(), operand.local());

        if (!operand.type().isPrimitive()) {
            code.unbox(operand.type(), booleanDecl());
        }
    }

    private void emitFalseOnNull(
            Bytecode code,
            StoredOperand leftValue,
            StoredOperand rightValue,
            Runnable nonNull) {
        Runnable rightCheck = rightValue.nullable()
            ? () -> {
                code.load(rightValue.type(), rightValue.local());
                code.ifnull(() -> code.iconst(0), nonNull);
            }
            : nonNull;

        if (leftValue.nullable()) {
            code.load(leftValue.type(), leftValue.local());
            code.ifnull(() -> code.iconst(0), rightCheck);
        } else {
            rightCheck.run();
        }
    }

    private void emitNullSafeEquality(
            Bytecode code,
            StoredOperand leftValue,
            StoredOperand rightValue,
            Runnable nonNullEquality) {
        if (leftValue.nullable()) {
            code.load(leftValue.type(), leftValue.local());
            code.ifnull(
                () -> {
                    if (rightValue.nullable()) {
                        code.load(rightValue.type(), rightValue.local());
                        code.ifnull(() -> code.iconst(1), () -> code.iconst(0));
                    } else {
                        code.iconst(0);
                    }
                },
                () -> emitRightNullCheck(code, rightValue, nonNullEquality));
        } else {
            emitRightNullCheck(code, rightValue, nonNullEquality);
        }
    }

    private void emitRightNullCheck(Bytecode code, StoredOperand rightValue, Runnable nonNullEquality) {
        if (rightValue.nullable()) {
            code.load(rightValue.type(), rightValue.local());
            code.ifnull(() -> code.iconst(0), nonNullEquality);
        } else {
            nonNullEquality.run();
        }
    }

    private void emitNumericRelationPredicate(Bytecode code, TypeDeclaration promotedType) {
        if (promotedType.equals(intDecl())) {
            switch (operator) {
                case LESS_THAN -> code.if_icmplt(() -> code.iconst(1), () -> code.iconst(0));
                case LESS_THAN_OR_EQUAL -> code.if_icmple(() -> code.iconst(1), () -> code.iconst(0));
                case GREATER_THAN -> code.if_icmpgt(() -> code.iconst(1), () -> code.iconst(0));
                case GREATER_THAN_OR_EQUAL -> code.if_icmpge(() -> code.iconst(1), () -> code.iconst(0));
                default -> throw new AssertionError(operator);
            }

            return;
        }

        if (promotedType.equals(longDecl())) {
            code.lcmp();
        } else if (promotedType.equals(floatDecl())) {
            if (operator == BinaryOperator.LESS_THAN || operator == BinaryOperator.LESS_THAN_OR_EQUAL) {
                code.fcmpg();
            } else {
                code.fcmpl();
            }
        } else if (promotedType.equals(doubleDecl())) {
            if (operator == BinaryOperator.LESS_THAN || operator == BinaryOperator.LESS_THAN_OR_EQUAL) {
                code.dcmpg();
            } else {
                code.dcmpl();
            }
        } else {
            throw new AssertionError(promotedType);
        }

        emitRelationPredicate(code);
    }

    private void emitRelationPredicate(Bytecode code) {
        switch (operator) {
            case LESS_THAN -> code.iflt(() -> code.iconst(1), () -> code.iconst(0));
            case LESS_THAN_OR_EQUAL -> code.ifle(() -> code.iconst(1), () -> code.iconst(0));
            case GREATER_THAN -> code.ifgt(() -> code.iconst(1), () -> code.iconst(0));
            case GREATER_THAN_OR_EQUAL -> code.ifge(() -> code.iconst(1), () -> code.iconst(0));
            default -> throw new AssertionError(operator);
        }
    }

    private void emitNumericEqualityPredicate(Bytecode code, TypeDeclaration promotedType) {
        if (promotedType.equals(intDecl())) {
            code.if_icmpeq(() -> code.iconst(1), () -> code.iconst(0));
        } else {
            if (promotedType.equals(longDecl())) {
                code.lcmp();
            } else if (promotedType.equals(floatDecl())) {
                code.fcmpl();
            } else if (promotedType.equals(doubleDecl())) {
                code.dcmpl();
            } else {
                throw new AssertionError(promotedType);
            }

            code.ifeq(() -> code.iconst(1), () -> code.iconst(0));
        }
    }

    private void negateValueEqualityIfNeeded(Bytecode code) {
        if (operator == BinaryOperator.VALUE_NOT_EQUAL) {
            negate(code);
        }
    }

    private void negate(Bytecode code) {
        code.iconst(1).ixor();
    }

    private void release(Bytecode code, StoredOperand... operands) {
        for (StoredOperand operand : operands) {
            code.releaseLocal(operand.local());
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        left = (ValueEmitterNode)left.accept(visitor);
        right = (ValueEmitterNode)right.accept(visitor);
    }

    @Override
    public EmitComparisonExpressionNode deepClone() {
        return new EmitComparisonExpressionNode(
            operator,
            strategy,
            left.deepClone(),
            right.deepClone(),
            leftSourceType,
            rightSourceType,
            leftPrimitive,
            rightPrimitive,
            promotedType,
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof EmitComparisonExpressionNode that
            && operator == that.operator
            && strategy == that.strategy
            && leftSourceType.equals(that.leftSourceType)
            && rightSourceType.equals(that.rightSourceType)
            && Objects.equals(leftPrimitive, that.leftPrimitive)
            && Objects.equals(rightPrimitive, that.rightPrimitive)
            && Objects.equals(promotedType, that.promotedType)
            && left.equals(that.left)
            && right.equals(that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            operator, strategy, leftSourceType, rightSourceType,
            leftPrimitive, rightPrimitive, promotedType, left, right);
    }
}
