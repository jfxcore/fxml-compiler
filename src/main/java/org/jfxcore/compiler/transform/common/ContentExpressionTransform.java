// Copyright (c) 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import java.util.List;

import static org.jfxcore.compiler.ast.intrinsic.Intrinsics.*;

/**
 * Transforms {@code fx:once}, {@code fx:bind}, and {@code fx:bindBidirectional} intrinsics
 * that use the content literal form into {@code fx:content}, {@code fx:bindContent}, and
 * {@code fx:bindContentBidirectional} intrinsics.
 */
public class ContentExpressionTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)
                || !objectNode.isIntrinsic(ONCE, BIND, BIND_BIDIRECTIONAL)) {
            return node;
        }

        if (objectNode.isIntrinsic(ONCE)) {
            return parseExpression(context, objectNode, ONCE, CONTENT);
        } else if (objectNode.isIntrinsic(BIND)) {
            return parseExpression(context, objectNode, BIND, BIND_CONTENT);
        } else if (objectNode.isIntrinsic(BIND_BIDIRECTIONAL)) {
            return parseExpression(context, objectNode, BIND_BIDIRECTIONAL, BIND_CONTENT_BIDIRECTIONAL);
        }

        return node;
    }

    private Node parseExpression(TransformContext context,
                                 ObjectNode expression,
                                 Intrinsic sourceIntrinsic,
                                 Intrinsic targetIntrinsic) {
        PropertyNode pathProperty = expression.findProperty(sourceIntrinsic.getDefaultProperty().getName());

        if (!(pathProperty.getSingleValue(context) instanceof CompositeNode compositeNode)) {
            return expression;
        }

        List<ValueNode> values = compositeNode.getValues();

        if (!(values.get(0) instanceof TextNode text1 && text1.getText().equals("["))) {
            return expression;
        }

        if (values.size() < 5
                || !(values.get(1) instanceof TextNode text2 && text2.getText().equals("."))
                || !(values.get(2) instanceof TextNode text3 && text3.getText().equals("."))
                || !(values.get(values.size() - 1) instanceof TextNode text4 && text4.getText().equals("]"))) {
            throw ParserErrors.invalidExpression(compositeNode.getSourceInfo());
        }

        var type = new TypeNode(
            targetIntrinsic.getName(),
            expression.getType().getMarkupName(),
            true,
            expression.getType().getSourceInfo());

        List<ValueNode> newValues = values.subList(3, values.size() - 1);
        pathProperty.getValues().clear();
        pathProperty.getValues().addAll(newValues);

        return new ObjectNode(type, expression.getProperties(), expression.getChildren(), expression.getSourceInfo());
    }
}
