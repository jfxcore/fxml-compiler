// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class BindingNode extends AbstractNode {

    private final BindingMode mode;
    private ExpressionNode path;
    private ExpressionNode converter;
    private ExpressionNode format;

    public BindingNode(BindingMode mode,
                       ExpressionNode path,
                       @Nullable ExpressionNode converter,
                       @Nullable ExpressionNode format,
                       SourceInfo sourceInfo) {
        super(sourceInfo);
        this.mode = checkNotNull(mode);
        this.path = checkNotNull(path);
        this.converter = converter;
        this.format = format;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        path = (ExpressionNode)path.accept(visitor);

        if (converter != null) {
            converter = (ExpressionNode)converter.accept(visitor);
        }

        if (format != null) {
            format = (ExpressionNode)format.accept(visitor);
        }
    }

    public BindingMode getMode() {
        return mode;
    }

    public BindingEmitterInfo toPathEmitter(TypeInstance invokingType, TypeInstance targetType) {
        return path.toEmitter(mode, invokingType, targetType);
    }

    public @Nullable BindingEmitterInfo toConverterEmitter(TypeInstance invokingType) {
        return converter != null
            ? converter.toEmitter(BindingMode.ONCE, invokingType, TypeInstance.of(Classes.StringConverterType()))
            : null;
    }

    public @Nullable BindingEmitterInfo toFormatEmitter(TypeInstance invokingType) {
        return format != null
            ? format.toEmitter(BindingMode.ONCE, invokingType, TypeInstance.of(Classes.FormatType()))
            : null;
    }

    /**
     * Gets the smallest binding distance within the binding expression,
     * where bind to self == 0, bind to first parent == 1, etc.
     */
    public int getBindingDistance() {
        return getBindingDistance(path);
    }

    private int getBindingDistance(Node expression) {
        if (expression instanceof PathExpressionNode pathExpression) {
            return pathExpression.getBindingContext().getBindingDistance();
        }

        if (expression instanceof FunctionExpressionNode functionExpression) {
            int min = getBindingDistance(functionExpression.getPath());
            min = Math.min(min, getBindingDistance(functionExpression.getInversePath()));

            for (var argument : functionExpression.getArguments()) {
                min = Math.min(min, getBindingDistance(argument));
            }

            return min;
        }

        return 0;
    }

    @Override
    public BindingNode deepClone() {
        return new BindingNode(
            mode,
            path.deepClone(),
            format != null ? format.deepClone() : null,
            converter != null ? converter.deepClone() : null,
            getSourceInfo());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BindingNode other
            && mode == other.mode
            && path.equals(other.path)
            && Objects.equals(format, other.format)
            && Objects.equals(converter, other.converter);
    }
}
