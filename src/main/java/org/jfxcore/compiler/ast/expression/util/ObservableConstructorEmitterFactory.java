// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ApplicableInvocationCandidate;

final class ObservableConstructorEmitterFactory extends AbstractFunctionEmitterFactory {

    private final ConstructorExpressionNode expression;
    private final ApplicableInvocationCandidate selected;

    ObservableConstructorEmitterFactory(
            ConstructorExpressionNode expression,
            TypeInstance invokingType,
            ApplicableInvocationCandidate selected) {
        super(invokingType);
        this.expression = expression;
        this.selected = selected;
    }

    @Nullable ValueEmitterNode newInstance(boolean bidirectional) {
        InvocationInfo invocation = createConstructorInvocation(expression, true, bidirectional, selected);
        if (!invocation.observable()) {
            return null;
        }

        return new EmitObservableFunctionNode(
            new Resolver(expression.getSourceInfo()).getObservableClass(invocation.type()),
            invocation.function(),
            invocation.inverseFunction(),
            invocation.arguments(),
            expression.getInvocationContext(),
            expression.getSourceInfo());
    }
}
