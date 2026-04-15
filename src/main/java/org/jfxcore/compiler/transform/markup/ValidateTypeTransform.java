// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.UnresolvedTypeNode;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;

/**
 * Validates that there are no unresolved types in the AST.
 */
public class ValidateTypeTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node instanceof UnresolvedTypeNode unresolvedTypeNode) {
            throw unresolvedTypeNode.getException();
        }

        return node;
    }
}
