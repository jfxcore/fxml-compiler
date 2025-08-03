// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.NameHelper;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ensures that intrinsics are well-formed and converts property intrinsics in element notation
 * into their PropertyNode representation if the intrinsic kind is {@link Intrinsic.Kind#PROPERTY}.
 * <p>
 * For example: In the following FXML document, {@code <fx:define>} is parsed as an ObjectNode, but
 * must be converted into a PropertyNode because {@code fx:define} is only applicable as a property.
 *
 * <pre>{@code
 *     <Button>
 *         <fx:define>
 *             <String fx:id="foo">Hello!</String>
 *         </fx:define>
 *     </Button>
 * }</pre>
 */
public class IntrinsicsTransform implements Transform {

    private static final List<Set<Intrinsic>> CONFLICTING_INTRINSICS = List.of(
        Set.of(Intrinsics.VALUE, Intrinsics.CONSTANT, Intrinsics.FACTORY),
        Set.of(Intrinsics.TYPE_ARGUMENTS, Intrinsics.CONSTANT));

    private static final Map<Intrinsic, Set<String>> CONFLICTING_PROPERTIES = Map.of(
        Intrinsics.BIND_BIDIRECTIONAL, Set.of("converter", "format", "inverseMethod"));

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node.typeEquals(ObjectNode.class)) {
            if (((ObjectNode)node).getType().isIntrinsic()) {
                return processIntrinsicObject(context, (ObjectNode)node);
            }

            validateConflictingIntrinsics((ObjectNode)node);
        }

        if (node.typeEquals(PropertyNode.class) && ((PropertyNode)node).isIntrinsic()) {
            return processIntrinsicProperty(context, (PropertyNode)node);
        }

        return node;
    }

    /**
     * Ensures that conflicting intrinsics cannot be used at the same time.
     */
    private void validateConflictingIntrinsics(ObjectNode objectNode) {
        for (Set<Intrinsic> conflictSet : CONFLICTING_INTRINSICS) {
            PropertyNode existingIntrinsic = null;

            for (PropertyNode propertyNode : objectNode.getProperties()) {
                Intrinsic intrinsic = propertyNode.isIntrinsic() ? Intrinsics.find(propertyNode.getName()) : null;
                if (intrinsic == null || !conflictSet.contains(intrinsic)) {
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
    }

    /**
     * Ensures that an intrinsic in element notation is valid, and all of its properties are valid.
     */
    private Node processIntrinsicObject(TransformContext context, ObjectNode objectNode) {
        Intrinsic intrinsic = Intrinsics.find(objectNode.getType().getName());
        if (intrinsic == null) {
            throw GeneralErrors.unknownIntrinsic(objectNode.getSourceInfo(), objectNode.getType().getMarkupName());
        }

        if (intrinsic.getKind() == Intrinsic.Kind.PROPERTY) {
            if (!(context.getParent() instanceof ObjectNode)) {
                throw GeneralErrors.unexpectedIntrinsic(
                    objectNode.getSourceInfo(), objectNode.getType().getMarkupName());
            }

            if (!objectNode.getProperties().isEmpty()) {
                throw ParserErrors.unexpectedToken(objectNode.getProperties().get(0).getSourceInfo());
            }

            return new PropertyNode(
                new String[] {objectNode.getType().getName()},
                objectNode.getType().getMarkupName(),
                objectNode.getChildren(),
                true,
                false,
                objectNode.getSourceInfo());
        }

        for (PropertyNode propertyNode : objectNode.getProperties()) {
            if (intrinsic.findProperty(propertyNode.getName()) == null) {
                throw SymbolResolutionErrors.propertyNotFound(
                    propertyNode.getSourceInfo(),
                    objectNode.getType().getMarkupName(), propertyNode.getMarkupName());
            }
        }

        validateConflictingProperties(intrinsic, objectNode.getProperties());

        return objectNode;
    }

    /**
     * Ensures that conflicting intrinsic properties cannot be used at the same time.
     */
    private void validateConflictingProperties(Intrinsic intrinsic, List<PropertyNode> properties) {
        if (properties.size() < 2) {
            return;
        }

        Set<String> conflictSet = CONFLICTING_PROPERTIES.get(intrinsic);
        String existingProperty = null;

        for (PropertyNode propertyNode : properties) {
            if (conflictSet.contains(propertyNode.getName())) {
                if (existingProperty == null) {
                    existingProperty = propertyNode.getMarkupName();
                } else {
                    throw ObjectInitializationErrors.conflictingProperties(
                        propertyNode.getSourceInfo(), propertyNode.getMarkupName(), existingProperty);
                }
            }
        }
    }

    /**
     * Ensures that an intrinsic in attribute notation is valid.
     */
    private PropertyNode processIntrinsicProperty(TransformContext context, PropertyNode propertyNode) {
        Intrinsic intrinsic = Intrinsics.find(propertyNode.getName());
        if (intrinsic == null) {
            throw GeneralErrors.unknownIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
        }

        if (intrinsic.getKind() == Intrinsic.Kind.OBJECT) {
            throw GeneralErrors.unexpectedIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
        }

        if (context.getParent(1) instanceof DocumentNode) {
            if (intrinsic.getPlacement() == Intrinsic.Placement.NOT_ROOT) {
                throw GeneralErrors.unexpectedIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
            }
        } else {
            if (intrinsic.getPlacement() == Intrinsic.Placement.ROOT) {
                throw GeneralErrors.unexpectedIntrinsic(propertyNode.getSourceInfo(), propertyNode.getMarkupName());
            }
        }

        if (propertyNode.isIntrinsic(Intrinsics.ID)) {
            String value = propertyNode.getNonEmptyTrimmedText(context);
            if (!NameHelper.isJavaIdentifier(value)) {
                throw GeneralErrors.invalidId(propertyNode.getTrimmedTextSourceInfo(context), value);
            }
        }

        return propertyNode;
    }

}
