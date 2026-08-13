// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A comma-separated argument sequence owned by inline markup syntax.
 */
public final class InlineArgumentSequenceNode extends AbstractNode implements SyntaxNode {

    private final List<Node> values;

    public InlineArgumentSequenceNode(Collection<? extends Node> values, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.values = new ArrayList<>(checkNotNull(values));
    }

    public List<Node> getValues() {
        return values;
    }

    @Override
    public String format() {
        return values.stream().map(Object::toString).collect(Collectors.joining(","));
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        acceptChildren(values, visitor, Node.class);
    }

    @Override
    public InlineArgumentSequenceNode deepClone() {
        return new InlineArgumentSequenceNode(deepClone(values), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof InlineArgumentSequenceNode other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }
}
