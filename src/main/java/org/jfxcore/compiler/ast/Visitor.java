// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;

public abstract class Visitor {

    private static class VisitorStoppedException extends RuntimeException {}

    public static final Node STOP = new AbstractNode(SourceInfo.none()) {
        @Override
        public Node deepClone() {
            return this;
        }
    };

    public static final Node STOP_SUBTREE = new AbstractNode(SourceInfo.none()) {
        @Override
        public Node deepClone() {
            return this;
        }
    };

    public static Node visit(Node node, Visitor visitor) {
        try {
            return node.accept(visitor);
        } catch (VisitorStoppedException ignored) {
            return node;
        }
    }

    final Node visit(Node node) {
        node = onVisited(node);

        if (node == STOP) {
            throw new VisitorStoppedException();
        }

        return node;
    }

    protected abstract Node onVisited(Node node);

    protected void push(Node node) {}

    protected void pop() {}

}
