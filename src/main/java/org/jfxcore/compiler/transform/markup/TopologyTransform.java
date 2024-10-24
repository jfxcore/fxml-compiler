// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.RootNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.ReferenceableNode;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.TransformVisitor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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

    private final Deque<List<Node>> scopes = new ArrayDeque<>();

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ReferenceableNode refNode)) {
            return node;
        }

        if (refNode.isEmitInPreamble()) {
            List<Node> preamble = scopes.peek() != null
                ? scopes.peek()
                : context.findParent(RootNode.class).getPreamble();

            // The node might contain children (for example, constructor arguments) that need to be
            // constructed before the node. Since we moved the node to the preamble, we need to run
            // the topology transform on its children, in order to recursively apply this transform.
            // This creates an ordering of initialization in the preamble that will ensure that all
            // dependencies are initialized before they are accessed.
            scopes.push(new ArrayList<>());
            refNode.acceptChildren(new TransformVisitor(this, context));
            preamble.addAll(scopes.pop()); // the children need to be added before the node
            preamble.add(refNode);

            return refNode.convertToLocalReference();
        }

        return node;
    }
}
