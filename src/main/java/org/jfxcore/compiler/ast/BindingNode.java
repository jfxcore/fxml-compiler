// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
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

    @Override
    public BindingNode deepClone() {
        return new BindingNode(expression.deepClone(), mode, getSourceInfo());
    }

}
