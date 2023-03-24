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

    public boolean isBindToSelf() {
        return bindsToSelf(expression);
    }

    private boolean bindsToSelf(Node expression) {
        if (expression instanceof PathExpressionNode pathExpression) {
            return pathExpression.getBindingContext().isSelf();
        } else if (expression instanceof FunctionExpressionNode functionExpression) {
            return functionExpression.getArguments().stream().anyMatch(this::bindsToSelf)
                || bindsToSelf(functionExpression.getPath())
                || bindsToSelf(functionExpression.getInversePath());
        } else {
            return false;
        }
    }

    @Override
    public BindingNode deepClone() {
        return new BindingNode(expression.deepClone(), mode, getSourceInfo());
    }

}
