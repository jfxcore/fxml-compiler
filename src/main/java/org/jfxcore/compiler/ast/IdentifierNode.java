// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * An identifier used as a source-level name or path segment.
 */
public final class IdentifierNode extends AbstractSyntaxNode {

    private final String name;

    public IdentifierNode(String name, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.name = checkNotNull(name);
    }

    public String getName() {
        return name;
    }

    @Override
    public String format() {
        return name;
    }

    @Override
    public IdentifierNode deepClone() {
        return new IdentifierNode(name, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IdentifierNode other && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
