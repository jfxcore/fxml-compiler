// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.ArrayStack;

class GetMaxDepthVisitor extends Visitor {

    private final ArrayStack<Node> parents = new ArrayStack<>();
    private int depth;
    private int max;

    public int getMaxDepth() {
        return max;
    }

    @Override
    protected Node onVisited(Node node) {
        return node;
    }

    @Override
    protected void push(Node node) {
        if (node instanceof EmitObjectNode && ((EmitObjectNode)node).addsToParentStack()) {
            depth++;
            max = Math.max(depth, max);
        }

        parents.add(node);
    }

    @Override
    protected void pop() {
        Node node = parents.pop();

        if (node instanceof EmitObjectNode && ((EmitObjectNode)node).addsToParentStack()) {
            depth--;
        }
    }

}
