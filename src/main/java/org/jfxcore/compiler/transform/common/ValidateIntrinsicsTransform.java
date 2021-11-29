// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.NameHelper;

/**
 * Ensures that intrinsics are well-formed.
 */
public class ValidateIntrinsicsTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node.typeEquals(ObjectNode.class)) {
            return processObjectNode((ObjectNode)node);
        }

        if (node.typeEquals(PropertyNode.class)) {
            return processPropertyNode(context, (PropertyNode)node);
        }

        return node;
    }

    /**
     * Ensures that an intrinsic in element notation is valid, and all of its properties are valid.
     */
    private ObjectNode processObjectNode(ObjectNode objectNode) {
        if (objectNode.getType().isIntrinsic()) {
            Intrinsic intrinsic = Intrinsics.find(objectNode.getType().getName());
            if (intrinsic == null) {
                throw GeneralErrors.unknownIntrinsic(objectNode.getSourceInfo(), objectNode.getType().getMarkupName());
            }

            if (!intrinsic.getUsage().isElement()) {
                throw GeneralErrors.unexpectedIntrinsic(objectNode.getSourceInfo(), objectNode.getType().getMarkupName());
            }

            for (PropertyNode propertyNode : objectNode.getProperties()) {
                if (intrinsic.findProperty(propertyNode.getName()) == null) {
                    throw SymbolResolutionErrors.propertyNotFound(
                        propertyNode.getSourceInfo(),
                        objectNode.getType().getMarkupName(), propertyNode.getMarkupName());
                }
            }
        }

        return objectNode;
    }

    /**
     * Ensures that an intrinsic in attribute notation is valid.
     */
    private PropertyNode processPropertyNode(TransformContext context, PropertyNode propertyNode) {
        if (propertyNode.isIntrinsic()) {
            Intrinsic intrinsic = Intrinsics.find(propertyNode.getName());
            if (intrinsic == null) {
                throw GeneralErrors.unknownIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
            }

            if (!intrinsic.getUsage().isAttribute()) {
                throw GeneralErrors.unexpectedIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
            }

            if (context.getParent(1) instanceof DocumentNode) {
                if (!intrinsic.getUsage().isRootAttribute()) {
                    throw GeneralErrors.unexpectedIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
                }
            } else {
                if (!intrinsic.getUsage().isChildAttribute()) {
                    throw GeneralErrors.unexpectedIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
                }
            }
        }

        if (propertyNode.isIntrinsic(Intrinsics.ID) &&
                !NameHelper.isJavaIdentifier(propertyNode.getTextValueNotEmpty(context))) {
            throw GeneralErrors.invalidId(propertyNode.getSourceInfo(), propertyNode.getTextValueNotEmpty(context));
        }

        return propertyNode;
    }

}
