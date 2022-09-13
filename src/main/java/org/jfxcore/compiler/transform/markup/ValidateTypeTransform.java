// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Resolver;
import java.util.Set;

/**
 * Validates that there are no unresolved types in the AST.
 */
public class ValidateTypeTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(ObjectToPropertyTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node.typeEquals(TypeNode.class)) {
            // The following line will produce a diagnostic, since we're resolving a type
            // for which we already know that it is unresolvable.
            new Resolver(node.getSourceInfo()).resolveClassAgainstImports(((TypeNode)node).getName());
        }

        return node;
    }

}
