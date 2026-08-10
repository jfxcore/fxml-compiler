// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;

public final class ObservableConstructorEmitterFactory
        extends AbstractFunctionEmitterFactory implements ObservableEmitterFactory {

    private final ConstructorExpressionNode expression;

    public ObservableConstructorEmitterFactory(
            ConstructorExpressionNode expression, TypeInstance invokingType) {
        super(invokingType, null);
        this.expression = expression;
    }

    @Override
    public BindingEmitterInfo newInstance() {
        return newInstance(false);
    }

    @Override
    public BindingEmitterInfo newInstance(boolean bidirectional) {
        InvocationInfo invocation = createConstructorInvocation(expression, true, bidirectional);
        if (!invocation.observable()) {
            return null;
        }

        ValueEmitterNode value = new EmitObservableFunctionNode(
            new Resolver(expression.getSourceInfo()).getObservableClass(invocation.type()),
            invocation.function(),
            invocation.inverseFunction(),
            invocation.arguments(),
            expression.getInvocationContext(),
            expression.getSourceInfo());

        return new BindingEmitterInfo(
            value,
            invocation.type(),
            TypeHelper.getTypeInstance(value),
            ValueSourceKind.get(TypeHelper.getTypeDeclaration(value)),
            ObservableDependencyKind.get(TypeHelper.getTypeDeclaration(value)),
            invocation.type().declaration(),
            invocation.type().declaration().simpleName(),
            true,
            false,
            expression.getSourceInfo());
    }
}
