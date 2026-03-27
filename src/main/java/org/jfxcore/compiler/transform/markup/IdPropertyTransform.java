// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.AnnotationDeclaration;
import org.jfxcore.compiler.type.TypeHelper;

import static org.jfxcore.compiler.type.TypeSymbols.*;

/**
 * If a node specifies the fx:id intrinsic property, but not the 'id' property as indicated by the IDProperty
 * annotation, this transform adds the 'id' property to the node and assigns the value of fx:id.
 */
public class IdPropertyTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || ((ObjectNode)node).getType().isIntrinsic()) {
            return node;
        }

        ObjectNode objectNode = (ObjectNode)node;
        PropertyNode idNode = objectNode.findIntrinsicProperty(Intrinsics.ID);

        if (idNode != null) {
            AnnotationDeclaration idAnnotation = TypeHelper.getTypeDeclaration(objectNode)
                .annotation(IDPropertyAnnotationName)
                .orElse(null);

            if (idAnnotation != null) {
                String value = idAnnotation.getString("value");
                PropertyNode idProperty = objectNode.findProperty(value);

                if (idProperty == null) {
                    objectNode.getProperties().add(
                        new PropertyNode(new String[] {value}, value, idNode.getValues(),
                                         false, false, idNode.getSourceInfo()));
                }
            }
        }

        return node;
    }
}
