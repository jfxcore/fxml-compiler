// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;

/**
 * Emits a resolved invocation whose receiver or arguments are observable.
 */
final class ObservableInvocationEmitterFactory {

    private final InvocationExpressionNode expression;
    private final TypeInstance invokingType;
    private final ApplicableInvocationCandidate selected;

    ObservableInvocationEmitterFactory(
            InvocationExpressionNode expression,
            TypeInstance invokingType,
            ApplicableInvocationCandidate selected) {
        this.expression = expression;
        this.invokingType = invokingType;
        this.selected = selected;
    }

    @Nullable ValueEmitterNode newInstance(boolean bidirectional) {
        InvocationResolver resolver = new InvocationResolver(expression, invokingType, null);
        InvocationResolver.ResolvedInvocation resolved = resolver.emitSelected(selected, bidirectional, true);
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

        value = expression.getOperator().toEmitter(
            value, bidirectional ? BindingMode.BIDIRECTIONAL : BindingMode.UNIDIRECTIONAL);

        return value;
    }
}
