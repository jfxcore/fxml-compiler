// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * Explicit leading {@code ..} content-selection syntax.
 */
public final class ContentSelectionNode extends AbstractNode implements SyntaxNode {

    private Node value;

    public ContentSelectionNode(Node value, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.value = checkNotNull(value);
    }

    public Node getValue() {
        return value;
    }

    @Override
    public String format() {
        return ".." + value;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        value = value.accept(visitor);
    }

    @Override
    public ContentSelectionNode deepClone() {
        return new ContentSelectionNode(value.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ContentSelectionNode other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
