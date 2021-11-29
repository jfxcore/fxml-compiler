// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;

public class TransformVisitor extends Visitor {

    private final Transform transform;
    private final TransformContext context;

    public TransformVisitor(Transform transform, TransformContext context) {
        this.transform = transform;
        this.context = context;
    }

    public Node onVisited(Node node) {
        return transform.transform(context, node);
    }

    public void push(Node node) {
        context.push(node);
    }

    public void pop() {
        context.pop();
    }

}
