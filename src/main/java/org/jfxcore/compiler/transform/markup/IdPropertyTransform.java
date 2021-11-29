// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.bytecode.annotation.Annotation;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.common.ResolveTypeTransform;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Set;

/**
 * If a node specifies the fx:id intrinsic property, but not the 'id' property as indicated by the IDProperty
 * annotation, this transform adds the 'id' property to the node and assigns the value of fx:id.
 */
public class IdPropertyTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(ResolveTypeTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || ((ObjectNode)node).getType().isIntrinsic()) {
            return node;
        }

        ObjectNode objectNode = (ObjectNode)node;
        PropertyNode idNode = objectNode.findIntrinsicProperty(Intrinsics.ID);

        if (idNode != null) {
            Annotation idAnnotation = new Resolver(idNode.getSourceInfo()).tryResolveClassAnnotation(
                TypeHelper.getJvmType(objectNode), "com.sun.javafx.beans.IDProperty");

            if (idAnnotation != null) {
                String value = TypeHelper.getAnnotationString(idAnnotation, "value");
                PropertyNode idProperty = objectNode.findProperty(value);

                if (idProperty == null) {
                    objectNode.getProperties().add(
                        new PropertyNode(
                            new String[] {value}, value, idNode.getValues(), false, idNode.getSourceInfo()));
                }
            }
        }

        return node;
    }

}
