// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;

/**
 * Emits a resolved neutral invocation whose receiver or arguments are observable.
 */
public final class ObservableInvocationEmitterFactory implements ObservableEmitterFactory {

    private final InvocationExpressionNode expression;
    private final TypeInstance invokingType;
    private final @Nullable TypeInstance targetType;
    private final BindingMode bindingMode;

    public ObservableInvocationEmitterFactory(
            InvocationExpressionNode expression,
            TypeInstance invokingType,
            @Nullable TypeInstance targetType,
            BindingMode bindingMode) {
        this.expression = expression;
        this.invokingType = invokingType;
        this.targetType = targetType;
        this.bindingMode = bindingMode;
    }

    @Override
    public @Nullable BindingEmitterInfo newInstance() {
        return newInstance(false);
    }

    @Override
    public @Nullable BindingEmitterInfo newInstance(boolean bidirectional) {
        InvocationResolver.ResolvedInvocation resolved = new InvocationResolver(
            expression, invokingType, targetType).resolve(bidirectional, true);

        if (resolved.construction() && bindingMode.isContent()) {
            throw GeneralErrors.expressionNotApplicable(expression.getSourceInfo(), false);
        }

        if (resolved.construction() && bindingMode.isReverse()) {
            throw BindingSourceErrors.expressionNotInvertible(expression.getSourceInfo());
        }

        AbstractFunctionEmitterFactory.InvocationInfo invocation = resolved.invocation();

        if (!invocation.observable()) {
            return null;
        }

        TypeInstance resultType = invocation.type();

        ValueEmitterNode value = new EmitObservableFunctionNode(
            new Resolver(expression.getSourceInfo()).getObservableClass(resultType),
            invocation.function(),
            invocation.inverseFunction(),
            invocation.arguments(),
            expression.getInvocationContext(),
            expression.getSourceInfo());

        if (bidirectional && !expression.getOperator().isInvertible(resultType)) {
            throw BindingSourceErrors.expressionNotInvertible(value.getSourceInfo());
        }

        value = expression.getOperator().toEmitter(
            value, bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);

        TypeInstance valueSourceType = TypeHelper.getTypeInstance(value);

        return new BindingEmitterInfo(
            value,
            expression.getOperator().evaluateType(resultType),
            valueSourceType,
            ValueSourceKind.get(valueSourceType.declaration()),
            ObservableDependencyKind.get(valueSourceType.declaration()),
            resolved.construction()
                ? resultType.declaration()
                : invocation.function().getBehavior().declaringType(),
            resolved.construction()
                ? resultType.declaration().simpleName()
                : invocation.function().getBehavior().name(),
            true,
            false,
            expression.getSourceInfo());
    }
}
