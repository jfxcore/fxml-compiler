// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * A decoded literal together with optional comma/newline-separated parts used for structural coercion.
 */
public final class LiteralValueNode extends AbstractNode implements ValueNode {

    private final String text;
    private final List<LiteralValueNode> coercionParts;
    private TypeNode type;

    public LiteralValueNode(String text, SourceInfo sourceInfo) {
        this(text, List.of(), new TypeNode(StringName, sourceInfo), sourceInfo);
    }

    public LiteralValueNode(
            String text,
            Collection<? extends LiteralValueNode> coercionParts,
            SourceInfo sourceInfo) {
        this(text, coercionParts, new TypeNode(StringName, sourceInfo), sourceInfo);
    }

    private LiteralValueNode(
            String text,
            Collection<? extends LiteralValueNode> coercionParts,
            TypeNode type,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.text = checkNotNull(text);
        this.coercionParts = new ArrayList<>(checkNotNull(coercionParts));
        this.type = checkNotNull(type);
    }

    public String getText() {
        return text;
    }

    /**
     * Returns the compatibility coercion parts. An empty list means that this is a scalar literal.
     */
    public List<LiteralValueNode> getCoercionParts() {
        return coercionParts;
    }

    public boolean hasCoercionParts() {
        return !coercionParts.isEmpty();
    }

    @Override
    public TypeNode getType() {
        return type;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        type = (TypeNode)type.accept(visitor);
        acceptChildren(coercionParts, visitor, LiteralValueNode.class);
    }

    @Override
    public LiteralValueNode deepClone() {
        return new LiteralValueNode(
            text, deepClone(coercionParts), type.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LiteralValueNode other
            && text.equals(other.text)
            && coercionParts.equals(other.coercionParts)
            && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, coercionParts, type);
    }
}
