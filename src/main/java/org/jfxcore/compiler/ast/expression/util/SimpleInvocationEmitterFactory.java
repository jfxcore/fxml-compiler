// Copyright (c) 2026, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.ast.expression.InvocationExpressionNode;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.AccessVerifier;

/**
 * Emits a resolved neutral invocation for non-observable evaluation.
 */
public final class SimpleInvocationEmitterFactory implements EmitterFactory {

    private final InvocationExpressionNode expression;
    private final TypeInstance invokingType;
    private final @Nullable TypeInstance targetType;
    private final BindingMode bindingMode;

    public SimpleInvocationEmitterFactory(
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
    public BindingEmitterInfo newInstance() {
        InvocationResolver.ResolvedInvocation resolved = new InvocationResolver(
            expression, invokingType, targetType).resolve(false, false);

        if (resolved.construction() && bindingMode.isContent()) {
            throw GeneralErrors.expressionNotApplicable(expression.getSourceInfo(), false);
        }

        if (resolved.construction() && bindingMode.isReverse()) {
            throw BindingSourceErrors.expressionNotInvertible(expression.getSourceInfo());
        }

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
            AccessVerifier.verifyAccessible(
                invocation.function().getBehavior(),
                expression.getInvocationContext(),
                expression.getTerminalSegment().getSourceInfo());

            value = new EmitMethodCallNode(
                (MethodDeclaration)invocation.function().getBehavior(),
                invocation.type(),
                invocation.function().getReceiver(),
                invocation.arguments(),
                expression.getSourceInfo());
        }

        value = expression.getOperator().toEmitter(value, BindingMode.ONCE);
        TypeInstance valueType = TypeHelper.getTypeInstance(value);

        return new BindingEmitterInfo(
            value,
            valueType,
            null,
            ValueSourceKind.NONE,
            ObservableDependencyKind.get(valueType.declaration()),
            resolved.construction()
                ? invocation.type().declaration()
                : invocation.function().getBehavior().declaringType(),
            resolved.construction()
                ? invocation.type().declaration().simpleName()
                : invocation.function().getBehavior().name(),
            true,
            false,
            expression.getSourceInfo());
    }
}
