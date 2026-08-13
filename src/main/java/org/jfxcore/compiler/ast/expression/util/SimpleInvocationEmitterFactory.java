// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitMethodCallNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;

/**
 * Emits a resolved invocation for non-observable evaluation.
 */
final class SimpleInvocationEmitterFactory {

    private final InvocationExpressionNode expression;
    private final TypeInstance invokingType;
    private final ApplicableInvocationCandidate selected;

    SimpleInvocationEmitterFactory(
            InvocationExpressionNode expression,
            TypeInstance invokingType,
            ApplicableInvocationCandidate selected) {
        this.expression = expression;
        this.invokingType = invokingType;
        this.selected = selected;
    }

    ValueEmitterNode newInstance() {
        InvocationResolver resolver = new InvocationResolver(expression, invokingType, null);
        InvocationResolver.ResolvedInvocation resolved = resolver.emitSelected(selected, false, false);

        AbstractFunctionEmitterFactory.InvocationInfo invocation = resolved.invocation();
        ValueEmitterNode value;

        if (resolved.construction()) {
            ValueEmitterNode enclosingInstance = invocation.function().getReceiver().isEmpty()
                ? null : invocation.function().getReceiver().get(0);

            value = EmitObjectNode
                .constructor(
                    invocation.type(),
                    (ConstructorDeclaration)invocation.function().getBehavior(),
                    invocation.arguments(),
                    expression.getSourceInfo())
                .enclosingInstance(enclosingInstance)
                .create();
        } else {
            value = new EmitMethodCallNode(
                (MethodDeclaration)invocation.function().getBehavior(),
                invocation.type(),
                invocation.function().getReceiver(),
                invocation.arguments(),
                expression.getSourceInfo());
        }

        value = expression.getOperator().toEmitter(value, BindingMode.ONCE);
        return value;
    }
}
