// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;

final class SimpleConstructorEmitterFactory extends AbstractFunctionEmitterFactory {

    private final ConstructorExpressionNode expression;
    private final ApplicableInvocationCandidate selected;

    SimpleConstructorEmitterFactory(
            ConstructorExpressionNode expression,
            TypeInstance invokingType,
            ApplicableInvocationCandidate selected) {
        super(invokingType);
        this.expression = expression;
        this.selected = selected;
    }

    ValueEmitterNode newInstance() {
        InvocationInfo invocation = createConstructorInvocation(expression, false, false, selected);

        ValueEmitterNode enclosingInstance = invocation.function().getReceiver().isEmpty()
            ? null : invocation.function().getReceiver().get(0);

        return EmitObjectNode
            .constructor(
                invocation.type(),
                (ConstructorDeclaration)invocation.function().getBehavior(),
                invocation.arguments(),
                expression.getSourceInfo())
            .enclosingInstance(enclosingInstance)
            .create();
    }
}
