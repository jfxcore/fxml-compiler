// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

/**
 * Represents an invariant, intrinsically typed literal in a compiled expression.
 */
public final class LiteralExpressionNode
        extends AbstractNode
        implements ExpressionNode, AnalyzedExpressionNode {

    private final TypeInstance literalType;
    private final Object literal;

    public static LiteralExpressionNode ofNull(SourceInfo sourceInfo) {
        return new LiteralExpressionNode(TypeInstance.nullType(), null, sourceInfo);
    }

    public static LiteralExpressionNode ofBoolean(boolean value, SourceInfo sourceInfo) {
        return new LiteralExpressionNode(TypeInstance.booleanType(), value, sourceInfo);
    }

    public static LiteralExpressionNode ofString(String value, SourceInfo sourceInfo) {
        return new LiteralExpressionNode(TypeInstance.StringType(), checkNotNull(value), sourceInfo);
    }

    public static LiteralExpressionNode ofNumber(Number value, SourceInfo sourceInfo) {
        checkNotNull(value);
        TypeInstance type;

        if (value instanceof Byte) {
            type = TypeInstance.byteType();
        } else if (value instanceof Short) {
            type = TypeInstance.shortType();
        } else if (value instanceof Integer) {
            type = TypeInstance.intType();
        } else if (value instanceof Long) {
            type = TypeInstance.longType();
        } else if (value instanceof Float) {
            type = TypeInstance.floatType();
        } else if (value instanceof Double) {
            type = TypeInstance.doubleType();
        } else {
            throw new IllegalArgumentException(value.getClass().getName());
        }

        return new LiteralExpressionNode(type, value, sourceInfo);
    }

    private LiteralExpressionNode(TypeInstance literalType, @Nullable Object literal, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.literalType = checkNotNull(literalType);
        this.literal = literal;
    }

    public @Nullable Object getLiteral() {
        return literal;
    }

    public TypeInstance getLiteralType() {
        return literalType;
    }

    @Override
    public TypeInstance analyze(ExpressionAnalysisContext context) {
        return literalType;
    }

    @Override
    public ValueEmitterNode toEmitter(ExpressionAnalysisContext context) {
        return new EmitLiteralNode(literalType, literal, getSourceInfo());
    }

    @Override
    public int getBindingDistance() {
        return NO_BINDING_DISTANCE;
    }

    @Override
    public LiteralExpressionNode deepClone() {
        return new LiteralExpressionNode(literalType, literal, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof LiteralExpressionNode that
            && literalType.equals(that.literalType)
            && Objects.equals(literal, that.literal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(literalType, literal);
    }
}
