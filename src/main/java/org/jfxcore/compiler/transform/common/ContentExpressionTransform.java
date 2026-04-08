// Copyright (c) 2024, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.text.CompositeNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import java.util.List;

import static org.jfxcore.compiler.ast.intrinsic.Intrinsics.*;

/**
 * Detects the content literal form in {@code fx:once}, {@code fx:observe}, and {@code fx:sync}
 * intrinsics and adds {@link NodeDataKey#CONTENT_EXPRESSION} to the node.
 */
public class ContentExpressionTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)
                || !objectNode.isIntrinsic(EVALUATE, OBSERVE, SYNCHRONIZE)) {
            return node;
        }

        if (objectNode.isIntrinsic(EVALUATE)) {
            parseExpression(context, objectNode, EVALUATE);
        } else if (objectNode.isIntrinsic(OBSERVE)) {
            parseExpression(context, objectNode, OBSERVE);
        } else if (objectNode.isIntrinsic(SYNCHRONIZE)) {
            parseExpression(context, objectNode, SYNCHRONIZE);
        }

        return node;
    }

    private void parseExpression(TransformContext context, ObjectNode expression, Intrinsic sourceIntrinsic) {
        PropertyNode pathProperty = expression.findProperty(sourceIntrinsic.getDefaultProperty().getName());
        if (pathProperty == null) {
            throw PropertyAssignmentErrors.propertyMustBeSpecified(
                expression.getSourceInfo(), expression.getType().getMarkupName(),
                sourceIntrinsic.getDefaultProperty().getName());
        }

        if (!(pathProperty.getSingleValue(context) instanceof CompositeNode compositeNode)) {
            return;
        }

        List<ValueNode> values = compositeNode.getValues();

        if (values.size() < 3
                || !(values.get(0) instanceof TextNode text2 && text2.getText().equals("."))
                || !(values.get(1) instanceof TextNode text3 && text3.getText().equals("."))) {
            return;
        }

        List<ValueNode> newValues = values.subList(2, values.size());
        if (newValues.size() != 1) {
            throw ParserErrors.invalidExpression(compositeNode.getSourceInfo());
        }

        for (PropertyNode property : expression.getProperties()) {
            if (property != pathProperty) {
                throw ObjectInitializationErrors.conflictingProperties(
                    property.getSourceInfo(), "Content expansion", property.getMarkupName());
            }
        }

        pathProperty.getValues().clear();
        pathProperty.getValues().addAll(newValues);
        expression.setNodeData(NodeDataKey.CONTENT_EXPRESSION, true);
    }
}
