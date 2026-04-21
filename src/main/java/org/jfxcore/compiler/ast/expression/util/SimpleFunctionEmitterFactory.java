// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitMethodCallNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.AccessVerifier;

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

        if (invocationInfo.function().getBehavior() instanceof ConstructorDeclaration constructor) {
            value = EmitObjectNode
                .constructor(
                    new TypeInvoker(functionExpression.getSourceInfo()).invokeType(constructor.declaringType()),
                    constructor,
                    invocationInfo.arguments(),
                    functionExpression.getSourceInfo())
                .create();
        } else {
            value = new EmitMethodCallNode(
                (MethodDeclaration)invocationInfo.function().getBehavior(), invocationInfo.type(),
                invocationInfo.function().getReceiver(), invocationInfo.arguments(),
                functionExpression.getSourceInfo());
        }

        value = functionExpression.getPath().getOperator().toEmitter(value, BindingMode.ONCE);

        return new BindingEmitterInfo(
            value,
            TypeHelper.getTypeInstance(value),
            null,
            ValueSourceKind.NONE,
            ObservableDependencyKind.get(TypeHelper.getTypeDeclaration(value)),
            invocationInfo.function().getBehavior().declaringType(),
            invocationInfo.function().getBehavior().name(),
            true,
            false,
            functionExpression.getSourceInfo());
    }
}
