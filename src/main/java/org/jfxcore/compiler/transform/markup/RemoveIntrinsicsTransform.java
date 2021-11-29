// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.common.ResolveTypeTransform;
import java.util.List;
import java.util.Set;

/**
 * Removes some intrinsics that are only relevant in the code generation phase.
 */
public class RemoveIntrinsicsTransform implements Transform {

    private static final List<Intrinsic> REMOVED_INTRINSICS = List.of(
        Intrinsics.CLASS, Intrinsics.CLASS_MODIFIER, Intrinsics.CLASS_PARAMETERS
    );

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(ResolveTypeTransform.class);
    }

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
