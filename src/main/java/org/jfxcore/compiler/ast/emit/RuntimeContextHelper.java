// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.Visitor;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public class RuntimeContextHelper {

    public static boolean needsRuntimeContext(Node node) {
        boolean[] result = new boolean[1];

        Visitor.visit(node, new Visitor() {
            @Override
            protected Node onVisited(Node node) {
                if (node instanceof EmitApplyMarkupExtensionNode || needsParentStack(node)) {
                    result[0] = true;
                    return Visitor.STOP;
                }

                return node;
            }
        });

        return result[0];
    }

    private static final WeakHashMap<Node, Boolean> needsParentStackCache = new WeakHashMap<>();

    public static boolean needsParentStack(Node node) {
        Boolean value = needsParentStackCache.get(node);
        if (value != null) {
            return value;
        }

        Visitor.visit(node, new Visitor() {
            final List<Node> parents = new ArrayList<>();

            @Override
            protected Node onVisited(Node node) {
                if (node instanceof ParentStackInfo && ((ParentStackInfo)node).needsParentStack()) {
                    needsParentStackCache.put(node, true);

                    for (Node parent : parents) {
                        needsParentStackCache.put(parent, true);
                    }

                    return Visitor.STOP;
                }

                return node;
            }

            @Override
            protected void push(Node node) {
                parents.add(node);
            }

            @Override
            protected void pop() {
                parents.remove(parents.size() - 1);
            }
        });

        value = needsParentStackCache.get(node);
        return value != null ? value : false;
    }
}
