// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import java.util.List;

/**
 * Removes some intrinsics that are only relevant in the code generation phase.
 */
public class RemoveIntrinsicsTransform implements Transform {

    private static final List<Intrinsic> REMOVED_INTRINSICS = List.of(
        Intrinsics.CLASS, Intrinsics.CLASS_PARAMETERS, Intrinsics.CLASS_MODIFIER, Intrinsics.MARKUP_CLASS_NAME);

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(PropertyNode.class)) {
            return node;
        }

        PropertyNode propertyNode = (PropertyNode)node;

        if (REMOVED_INTRINSICS.stream().anyMatch(propertyNode::isIntrinsic)) {
            ObjectNode parent = context.getParent().as(ObjectNode.class);

            if (context.getDocument().getRoot() == parent) {
                node.remove();
            }
        }

        return node;
    }

}
