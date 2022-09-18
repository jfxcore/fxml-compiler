// Copyright (c) 2021, 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.RootNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.ReferenceableNode;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;

/**
 * Moves {@link ReferenceableNode} nodes that need to be constructed in the <i>preamble</i> phase of component
 * initialization to the preamble block, and replaces the original nodes with references to the preamble block.
 *
 * <p>The purpose of this mechanism is to make sure that runtime objects referenced by other runtime
 * objects (for example in bindings) are created before being accessed. In this scenario, the runtime
 * object will be created in the <i>preamble</i> phase, while the replacement node loads the runtime
 * object later during property assignments, bindings, etc.
 */
public class TopologyTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ReferenceableNode refNode)) {
            return node;
        }

        if (refNode.isEmitInPreamble()) {
            return convertPreamble(context, refNode);
        }

        return node;
    }

    private Node convertPreamble(TransformContext context, ReferenceableNode node) {
        context.findParent(RootNode.class).getPreamble().add(node);
        return node.convertToLocalReference();
    }

}
