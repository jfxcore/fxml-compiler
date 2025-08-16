// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

public class ObservableFunctionEmitterFactory
        extends AbstractFunctionEmitterFactory implements ObservableEmitterFactory {

    private final FunctionExpressionNode functionExpression;
    private final Resolver resolver;

    public ObservableFunctionEmitterFactory(FunctionExpressionNode functionExpression,
                                            TypeInstance invokingType,
                                            @Nullable TypeInstance targetType) {
        super(invokingType, targetType);
        this.functionExpression = functionExpression;
        this.resolver = new Resolver(functionExpression.getSourceInfo());
    }

    @Override
    public BindingEmitterInfo newInstance() {
        return newInstance(false);
    }

    @Override
    public BindingEmitterInfo newInstance(boolean bidirectional) {
        InvocationInfo invocationInfo = createInvocation(functionExpression, bidirectional, true);
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

        Operator operator = functionExpression.getPath().getOperator();
        if (bidirectional && !operator.isInvertible(valueType)) {
            throw BindingSourceErrors.expressionNotInvertible(value.getSourceInfo());
        }

        value = operator.toEmitter(value, bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);
        valueType = operator.evaluateType(valueType);

        return new BindingEmitterInfo(
            value,
            valueType,
            TypeHelper.getTypeInstance(value),
            invocationInfo.function().getBehavior().getDeclaringClass(),
            invocationInfo.function().getBehavior().getName(),
            functionExpression.getSourceInfo());
    }
}
