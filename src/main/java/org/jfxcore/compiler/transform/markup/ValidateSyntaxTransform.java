// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.SyntaxNode;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;

/**
 * Ensures that all parse-time syntax has been resolved before topology transformation and bytecode emission.
 */
public final class ValidateSyntaxTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node instanceof SyntaxNode) {
            throw GeneralErrors.internalError(
                "Unresolved syntax reached emission: " + node.getClass().getName()
                    + " at " + node.getSourceInfo());
        }

        return node;
    }
}
