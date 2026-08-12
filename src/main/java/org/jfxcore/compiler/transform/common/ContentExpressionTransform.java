// Copyright (c) 2024, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ContentSelectionNode;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;

import static org.jfxcore.compiler.ast.intrinsic.Intrinsics.*;

/**
 * Detects the content literal form in {@code fx:Evaluate}, {@code fx:Observe}, {@code fx:Push},
 * and {@code fx:Synchronize} intrinsics and adds {@link NodeDataKey#CONTENT_EXPRESSION} to the node.
 */
public class ContentExpressionTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)
                || !objectNode.isIntrinsic(EVALUATE, OBSERVE, PUSH, SYNCHRONIZE)) {
            return node;
        }

        if (objectNode.isIntrinsic(EVALUATE)) {
            parseExpression(context, objectNode, EVALUATE);
        } else if (objectNode.isIntrinsic(OBSERVE)) {
            parseExpression(context, objectNode, OBSERVE);
        } else if (objectNode.isIntrinsic(PUSH)) {
            parseExpression(context, objectNode, PUSH);
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

        if (!(pathProperty.getSingleValue(context) instanceof ContentSelectionNode contentSelection)) {
            return;
        }

        for (PropertyNode property : expression.getProperties()) {
            if (property != pathProperty) {
                throw ObjectInitializationErrors.conflictingProperties(
                    property.getSourceInfo(), "Content expansion", property.getMarkupName());
            }
        }

        pathProperty.getValues().clear();
        pathProperty.getValues().add(contentSelection.getValue());
        expression.setNodeData(NodeDataKey.CONTENT_EXPRESSION, true);
    }
}
