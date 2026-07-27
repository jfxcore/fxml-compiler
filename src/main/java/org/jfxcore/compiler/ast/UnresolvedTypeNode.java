// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents an unresolved type.
 */
public final class UnresolvedTypeNode extends TypeNode {

    private final MarkupException exception;
    private final List<String> arguments;

    public UnresolvedTypeNode(TypeNode typeNode, List<String> arguments, MarkupException exception) {
        this(typeNode.getName(), typeNode.getMarkupName(), typeNode.getSourceInfo(), arguments, exception);
    }

    private UnresolvedTypeNode(String name, String markupName, SourceInfo sourceInfo,
                               Collection<String> arguments, MarkupException exception) {
        super(name, markupName, sourceInfo);
        this.exception = checkNotNull(exception);
        this.arguments = new ArrayList<>(checkNotNull(arguments));
    }

    public MarkupException getException() {
        return exception;
    }

    public List<String> getArguments() {
        return arguments;
    }

    @Override
    public UnresolvedTypeNode deepClone() {
        return new UnresolvedTypeNode(getName(), getMarkupName(), getSourceInfo(), arguments, exception).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        UnresolvedTypeNode that = (UnresolvedTypeNode)o;
        return super.equals(that);
    }

    @Override
    public int hashCode() {
        return getMarkupName().hashCode();
    }
}
