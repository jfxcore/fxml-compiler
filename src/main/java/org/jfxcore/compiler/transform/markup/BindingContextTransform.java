// Copyright (c) 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtField;
import javassist.Modifier;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

public class BindingContextTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof PropertyNode propertyNode) || !propertyNode.isIntrinsic(Intrinsics.CONTEXT)) {
            return node;
        }

        Node value = propertyNode.getSingleValue(context);
        BindingNode bindingNode;
        TypeInstance type;

        try {
            context.setBindingContextEnabled(false);
            bindingNode = new BindingTransform().transform(context, value).as(BindingNode.class);
        } finally {
            context.setBindingContextEnabled(true);
        }

        if (bindingNode != null) {
            if (bindingNode.getMode() != BindingMode.ONCE) {
                throw GeneralErrors.expressionNotApplicable(bindingNode.getSourceInfo(), true);
            }

            var resolver = new Resolver(propertyNode.getSourceInfo());
            var invokingType = resolver.getTypeInstance(context.getBindingContextClass());
            var emitter = bindingNode.toEmitter(invokingType, null);
            propertyNode.getValues().set(0, emitter.getValue());
            type = emitter.getValueType();
        } else if (value instanceof ValueNode valueNode) {
            type = TypeHelper.getTypeInstance(valueNode);
        } else {
            throw ParserErrors.invalidExpression(value.getSourceInfo());
        }

        ExceptionHelper.unchecked(value.getSourceInfo(), () -> {
            var rootField = new CtField(
                type.jvmType(),
                NameHelper.getMangledFieldName("root"),
                context.getMarkupClass());

            rootField.setModifiers(Modifier.PRIVATE);
            context.getMarkupClass().addField(rootField);
        });

        return node;
    }
}
