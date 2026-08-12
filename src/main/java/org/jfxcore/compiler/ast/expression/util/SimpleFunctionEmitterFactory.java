// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitMethodCallNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;

final class SimpleFunctionEmitterFactory extends AbstractFunctionEmitterFactory {

    private final FunctionExpressionNode functionExpression;
    private final ApplicableInvocationCandidate selected;
    private final boolean preferObservable;

    SimpleFunctionEmitterFactory(
            FunctionExpressionNode functionExpression,
            TypeInstance invokingType,
            ApplicableInvocationCandidate selected,
            boolean preferObservable) {
        super(invokingType);
        this.functionExpression = functionExpression;
        this.selected = selected;
        this.preferObservable = preferObservable;
    }

    ValueEmitterNode newInstance() {
        InvocationInfo invocationInfo = createInvocation(functionExpression, false, preferObservable, selected);

        ValueEmitterNode value = new EmitMethodCallNode(
            (MethodDeclaration)invocationInfo.function().getBehavior(), invocationInfo.type(),
            invocationInfo.function().getReceiver(), invocationInfo.arguments(),
            functionExpression.getSourceInfo());

        value = functionExpression.getPath().getOperator().toEmitter(value, BindingMode.ONCE);

        return value;
    }
}
