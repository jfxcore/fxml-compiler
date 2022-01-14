// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.NameHelper;
import java.util.Set;

/**
 * Ensures that intrinsics are well-formed.
 */
public class ValidateIntrinsicsTransform implements Transform {

    private static final Set<Intrinsic> CONFLICTING_INTRINSICS = Set.of(
        Intrinsics.VALUE, Intrinsics.CONSTANT, Intrinsics.FACTORY);

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node.typeEquals(ObjectNode.class)) {
            if (((ObjectNode)node).getType().isIntrinsic()) {
                return processIntrinsicElement((ObjectNode)node);
            }

            validateConflictingIntrinsics((ObjectNode)node);
        }

        if (node.typeEquals(PropertyNode.class)) {
            return processIntrinsicAttribute(context, (PropertyNode)node);
        }

        return node;
    }

    /**
     * Ensures that conflicting intrinsics cannot be used at the same time.
     */
    private void validateConflictingIntrinsics(ObjectNode objectNode) {
        PropertyNode existingIntrinsic = null;

        for (PropertyNode propertyNode : objectNode.getProperties()) {
            Intrinsic intrinsic = propertyNode.isIntrinsic() ? Intrinsics.find(propertyNode.getName()) : null;
            if (intrinsic == null || !CONFLICTING_INTRINSICS.contains(intrinsic)) {
                continue;
            }

            if (existingIntrinsic == null) {
                existingIntrinsic = propertyNode;
            } else {
                throw ObjectInitializationErrors.conflictingProperties(
                    propertyNode.getSourceInfo(), propertyNode.getMarkupName(), existingIntrinsic.getMarkupName());
            }
        }
    }

    /**
     * Ensures that an intrinsic in element notation is valid, and all of its properties are valid.
     */
    private ObjectNode processIntrinsicElement(ObjectNode objectNode) {
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
    private PropertyNode processIntrinsicAttribute(TransformContext context, PropertyNode propertyNode) {
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

        if (propertyNode.isIntrinsic(Intrinsics.ID)) {
            String value = propertyNode.getTextValueNotEmpty(context);
            if (!NameHelper.isJavaIdentifier(value)) {
                throw GeneralErrors.invalidId(propertyNode.getSingleValue(context).getSourceInfo(), value);
            }
        }

        return propertyNode;
    }

}
