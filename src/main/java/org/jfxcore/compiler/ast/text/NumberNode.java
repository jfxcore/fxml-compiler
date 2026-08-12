// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * Numeric source syntax. Its runtime type is assigned during expression lowering.
 */
public final class NumberNode extends AbstractSyntaxNode {

    private final String text;

    public NumberNode(String text, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.text = checkNotNull(text);
    }

    public String getText() {
        return text;
    }

    @Override
    public String format() {
        return text;
    }

    @Override
    public NumberNode deepClone() {
        return new NumberNode(text, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NumberNode other && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}
