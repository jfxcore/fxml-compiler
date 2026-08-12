// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;

/**
 * Base class for source syntax that has no runtime type before semantic lowering.
 */
public abstract class AbstractSyntaxNode extends AbstractNode implements SyntaxNode {

    protected AbstractSyntaxNode(SourceInfo sourceInfo) {
        super(sourceInfo);
    }

    @Override
    public final String toString() {
        return format();
    }

    protected static String format(Node node) {
        if (node instanceof SyntaxNode syntaxNode) {
            return syntaxNode.format();
        }

        if (node instanceof LiteralValueNode literalValueNode) {
            return literalValueNode.getText();
        }

        return node.toString();
    }
}
