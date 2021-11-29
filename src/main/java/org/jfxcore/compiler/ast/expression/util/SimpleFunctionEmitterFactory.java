// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import javassist.CtConstructor;
import javassist.CtMethod;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.emit.EmitMethodCallNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Collections;

public class SimpleFunctionEmitterFactory extends AbstractFunctionEmitterFactory implements EmitterFactory {

    private final FunctionExpressionNode functionExpression;

    public SimpleFunctionEmitterFactory(FunctionExpressionNode functionExpression, TypeInstance invokingType) {
        super(invokingType);
        this.functionExpression = functionExpression;
    }

    @Override
    public BindingEmitterInfo newInstance() {
        MethodInvocationInfo invocationInfo = createMethodInvocation(functionExpression, false, false);
        ValueEmitterNode value;

        if (invocationInfo.method instanceof CtConstructor constructor) {
            value = new EmitObjectNode(
                null,
                new Resolver(functionExpression.getSourceInfo()).getTypeInstance(constructor.getDeclaringClass()),
                constructor,
                invocationInfo.arguments,
                Collections.emptyList(),
                EmitObjectNode.CreateKind.CONSTRUCTOR,
                functionExpression.getSourceInfo());
        } else {
            BindingContextNode bindingSource = functionExpression.getPath().getSource();

            ValueEmitterNode emitter = bindingSource.toSegment().toValueEmitter(bindingSource.getSourceInfo());

            value = new EmitMethodCallNode(
                (CtMethod)invocationInfo.method, emitter, invocationInfo.arguments, functionExpression.getSourceInfo());
        }

        value = functionExpression.getPath().getOperator().toEmitter(value, BindingMode.ONCE);

        return new BindingEmitterInfo(
            value,
            TypeHelper.getTypeInstance(value),
            null,
            invocationInfo.method.getDeclaringClass(),
            invocationInfo.method.getName(),
            functionExpression.getSourceInfo());
    }

}
