// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.TypeInstance;

public class BindingNode extends AbstractNode {

    private final BindingMode mode;
    private ExpressionNode expression;

    public BindingNode(ExpressionNode expression, BindingMode mode, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.expression = checkNotNull(expression);
        this.mode = checkNotNull(mode);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        expression = (ExpressionNode)expression.accept(visitor);
    }

    public BindingEmitterInfo toEmitter(TypeInstance invokingType) {
        return expression.toEmitter(mode, invokingType);
    }

    public BindingMode getMode() {
        return mode;
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
        return new BindingNode(expression.deepClone(), mode, getSourceInfo());
    }

}
