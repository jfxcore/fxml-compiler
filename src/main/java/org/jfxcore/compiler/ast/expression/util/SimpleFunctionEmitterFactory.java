// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import javassist.CtConstructor;
import javassist.CtMethod;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitMethodCallNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;

public class SimpleFunctionEmitterFactory extends AbstractFunctionEmitterFactory implements EmitterFactory {

    private final FunctionExpressionNode functionExpression;

    public SimpleFunctionEmitterFactory(FunctionExpressionNode functionExpression,
                                        TypeInstance invokingType,
                                        @Nullable TypeInstance targetType) {
        super(invokingType, targetType);
        this.functionExpression = functionExpression;
    }

    @Override
    public BindingEmitterInfo newInstance() {
        InvocationInfo invocationInfo = createInvocation(functionExpression, false, false);

        AccessVerifier.verifyAccessible(
            invocationInfo.function().getBehavior(),
            functionExpression.getInvocationContext(),
            functionExpression.getPath().getSourceInfo());

        ValueEmitterNode value;

        if (invocationInfo.function().getBehavior() instanceof CtConstructor constructor) {
            value = EmitObjectNode
                .constructor(
                    new TypeInvoker(functionExpression.getSourceInfo()).invokeType(constructor.getDeclaringClass()),
                    constructor,
                    invocationInfo.arguments(),
                    functionExpression.getSourceInfo())
                .create();
        } else {
            value = new EmitMethodCallNode(
                (CtMethod)invocationInfo.function().getBehavior(), invocationInfo.type(),
                invocationInfo.function().getReceiver(), invocationInfo.arguments(),
                functionExpression.getSourceInfo());
        }

        value = functionExpression.getPath().getOperator().toEmitter(value, BindingMode.ONCE);

        return new BindingEmitterInfo(
            value,
            TypeHelper.getTypeInstance(value),
            null,
            invocationInfo.function().getBehavior().getDeclaringClass(),
            invocationInfo.function().getBehavior().getName(),
            true,
            false,
            functionExpression.getSourceInfo());
    }
}
