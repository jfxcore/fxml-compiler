// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtField;
import javassist.Modifier;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.List;

/**
 * A static constant can be referenced in FXML with the following syntax:
 * <pre>{code
 *     <TableView fx:constant="CONSTRAINED_RESIZE_POLICY"/>
 * }</pre>
 * Note that the type of this element is actually {@code Callback}, and not {@code TableView}.
 * The {@code TableView} class merely contains the static {@code CONSTRAINED_RESIZE_POLICY} field.
 * <p>
 * This transform creates a new {@link ObjectNode} with the correct type.
 */
public class ConstantTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        PropertyNode constantProperty = objectNode.findIntrinsicProperty(Intrinsics.CONSTANT);
        if (constantProperty == null) {
            return node;
        }

        String fieldName = constantProperty.getTextValueNotEmpty(context);
        SourceInfo sourceInfo = SourceInfo.span(constantProperty.getValues());
        Resolver resolver = new Resolver(sourceInfo);
        CtField field = resolver.resolveField(TypeHelper.getJvmType(objectNode), fieldName, false);
        AccessVerifier.verifyAccessible(field, context.getMarkupClass(), sourceInfo);

        if (!Modifier.isStatic(field.getModifiers())) {
            throw SymbolResolutionErrors.memberNotAccessible(sourceInfo, field);
        }

        if (objectNode.getChildren().size() > 0) {
            throw ObjectInitializationErrors.objectCannotHaveContent(
                objectNode.getSourceInfo(), TypeHelper.getJvmType(objectNode), constantProperty.getMarkupName());
        }

        String conflictingProperty = objectNode.getProperties().stream()
            .filter(p -> !p.isIntrinsic(Intrinsics.CONSTANT) && !p.isIntrinsic(Intrinsics.ID))
            .findFirst()
            .map(PropertyNode::getMarkupName)
            .orElse(null);

        if (conflictingProperty != null) {
            throw ObjectInitializationErrors.conflictingProperties(
                objectNode.getSourceInfo(), constantProperty.getMarkupName(), conflictingProperty);
        }

        ObjectNode result = new ObjectNode(
            new ResolvedTypeNode(resolver.getTypeInstance(field, List.of()), objectNode.getSourceInfo()),
            objectNode.getProperties(), List.of(), objectNode.getSourceInfo());

        result.setNodeData(NodeDataKey.CONSTANT_DECLARING_TYPE, TypeHelper.getJvmType(objectNode));
        return result;
    }

}
