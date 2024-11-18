// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class BindingNode extends AbstractNode {

    private final BindingMode mode;
    private ExpressionNode expression;
    private PropertyNode converter;
    private PropertyNode format;

    public BindingNode(ExpressionNode expression,
                       BindingMode mode,
                       @Nullable PropertyNode converter,
                       @Nullable PropertyNode format,
                       SourceInfo sourceInfo) {
        super(sourceInfo);
        this.expression = checkNotNull(expression);
        this.mode = checkNotNull(mode);
        this.converter = converter;
        this.format = format;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        expression = (ExpressionNode)expression.accept(visitor);

        if (converter != null) {
            converter = (PropertyNode)converter.accept(visitor);
        }

        if (format != null) {
            format = (PropertyNode)format.accept(visitor);
        }
    }

    public BindingEmitterInfo toEmitter(TypeInstance invokingType) {
        return expression.toEmitter(mode, invokingType);
    }

    public BindingMode getMode() {
        return mode;
    }

    public @Nullable PropertyNode getConverter() {
        return converter;
    }

    public @Nullable PropertyNode getFormat() {
        return format;
    }

    /**
     * Gets the smallest binding distance within the binding expression,
     * where bind to self == 0, bind to first parent == 1, etc.
     */
    public int getBindingDistance() {
        return getBindingDistance(expression);
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
            expression.deepClone(),
            mode,
            format != null ? format.deepClone() : null,
            converter == null ? null : converter.deepClone(),
            getSourceInfo());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BindingNode other
            && mode == other.mode
            && expression.equals(other.expression)
            && Objects.equals(format, other.format)
            && Objects.equals(converter, other.converter);
    }
}
