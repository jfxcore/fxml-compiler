// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * Base class for mutable compound syntax nodes whose text is derived from their children.
 */
abstract class DerivedTextNode extends TextNode {

    protected DerivedTextNode(SourceInfo sourceInfo) {
        super("", sourceInfo);
    }

    protected DerivedTextNode(TypeNode type, SourceInfo sourceInfo) {
        super("", false, type, sourceInfo);
    }

    @Override
    public String getText() {
        return formatText();
    }

    @Override
    public abstract String formatText();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DerivedTextNode that = (DerivedTextNode)o;
        return Objects.equals(getType(), that.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType());
    }
}
