// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.TypeInstance;

public final class SimpleConstructorEmitterFactory
        extends AbstractFunctionEmitterFactory implements EmitterFactory {

    private final ConstructorExpressionNode expression;

    public SimpleConstructorEmitterFactory(
            ConstructorExpressionNode expression, TypeInstance invokingType) {
        super(invokingType, null);
        this.expression = expression;
    }

    @Override
    public BindingEmitterInfo newInstance() {
        InvocationInfo invocation = createConstructorInvocation(expression, false, false);

        ValueEmitterNode enclosingInstance = invocation.function().getReceiver().isEmpty()
            ? null : invocation.function().getReceiver().get(0);

        ValueEmitterNode value = EmitObjectNode
            .constructor(
                invocation.type(),
                (ConstructorDeclaration)invocation.function().getBehavior(),
                invocation.arguments(),
                expression.getSourceInfo())
            .enclosingInstance(enclosingInstance)
            .create();

        return new BindingEmitterInfo(
            value,
            invocation.type(),
            null,
            ValueSourceKind.NONE,
            ObservableDependencyKind.get(invocation.type().declaration()),
            invocation.type().declaration(),
            invocation.type().declaration().simpleName(),
            true,
            false,
            expression.getSourceInfo());
    }
}
