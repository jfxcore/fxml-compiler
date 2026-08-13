// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.BindingOperator;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;

final class ObservableFunctionEmitterFactory extends AbstractFunctionEmitterFactory {

    private final FunctionExpressionNode functionExpression;
    private final Resolver resolver;
    private final ApplicableInvocationCandidate selected;

    ObservableFunctionEmitterFactory(
            FunctionExpressionNode functionExpression,
            TypeInstance invokingType,
            ApplicableInvocationCandidate selected) {
        super(invokingType);
        this.functionExpression = functionExpression;
        this.resolver = new Resolver(functionExpression.getSourceInfo());
        this.selected = selected;
    }

    @Nullable ValueEmitterNode newInstance(boolean bidirectional) {
        InvocationInfo invocationInfo = createInvocation(functionExpression, bidirectional, true, selected);
        if (!invocationInfo.observable()) {
            return null;
        }

        TypeInstance valueType = invocationInfo.type();

        ValueEmitterNode value = new EmitObservableFunctionNode(
            resolver.getObservableClass(valueType),
            invocationInfo.function(),
            invocationInfo.inverseFunction(),
            invocationInfo.arguments(),
            functionExpression.getInvocationContext(),
            functionExpression.getSourceInfo());

        BindingOperator operator = functionExpression.getPath().getOperator();
        value = operator.toEmitter(value, bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);
        return value;
    }
}
