// Copyright (c) 2024, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.ContextNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.NameHelper;
import java.util.function.Function;

public class BindingContextTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof PropertyNode propertyNode) || !propertyNode.isIntrinsic(Intrinsics.CONTEXT)) {
            return node;
        }

        Node value = propertyNode.getSingleValue(context);

        Function<TypeInstance, FieldDeclaration> createField = t -> context.getMarkupClass()
            .createField(NameHelper.getMangledFieldName("context"), t.declaration());

        BindingNode bindingNode = new BindingTransform(false).transform(context, value).as(BindingNode.class);
        if (bindingNode != null) {
            if (bindingNode.getMode() != BindingMode.ONCE && bindingNode.getMode() != BindingMode.UNIDIRECTIONAL) {
                throw GeneralErrors.expressionNotApplicable(bindingNode.getSourceInfo(), false);
            }

            var invoker = new TypeInvoker(propertyNode.getSourceInfo());
            var invokingType = invoker.invokeType(context.getCodeBehindOrMarkupClass());
            var emitter = bindingNode.toPathEmitter(invokingType, null);
            var contextNode = new ContextNode(
                createField.apply(emitter.getType()),
                emitter.getType(),
                emitter.getValueType(),
                emitter.getObservableType(),
                emitter.getValue(),
                bindingNode.getSourceInfo());

            propertyNode.getValues().set(0, contextNode);
        } else if (value instanceof ValueNode valueNode) {
            TypeInstance type = TypeHelper.getTypeInstance(valueNode);
            var contextNode = new ContextNode(
                createField.apply(type), type, type, null, valueNode, valueNode.getSourceInfo());
            propertyNode.getValues().set(0, contextNode);
        } else {
            throw ParserErrors.invalidExpression(value.getSourceInfo());
        }

        return node;
    }
}
