// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.IdentifierNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TextSegmentNode extends PathSegmentNode {

    private final boolean observableSelector;
    private final List<PathNode> typeArguments;
    private final @Nullable SourceInfo typeArgumentsSourceInfo;
    private IdentifierNode value;

    public TextSegmentNode(
            boolean observableSelector, IdentifierNode value,
            Collection<? extends PathNode> typeArguments, SourceInfo sourceInfo) {
        this(observableSelector, value, typeArguments, null, null, sourceInfo);
    }

    public TextSegmentNode(
            boolean observableSelector, IdentifierNode value,
            Collection<? extends PathNode> typeArguments,
            @Nullable SourceInfo selectorSourceInfo, SourceInfo sourceInfo) {
        this(observableSelector, value, typeArguments, selectorSourceInfo, null, sourceInfo);
    }

    public TextSegmentNode(
            boolean observableSelector, IdentifierNode value,
            Collection<? extends PathNode> typeArguments,
            @Nullable SourceInfo selectorSourceInfo,
            @Nullable SourceInfo typeArgumentsSourceInfo,
            SourceInfo sourceInfo) {
        super(selectorSourceInfo, sourceInfo);
        this.observableSelector = observableSelector;
        this.value = checkNotNull(value);
        this.typeArguments = new ArrayList<>(checkNotNull(typeArguments));
        this.typeArgumentsSourceInfo = typeArgumentsSourceInfo;
    }

    @Override
    public boolean isObservableSelector() {
        return observableSelector;
    }

    public IdentifierNode getValue() {
        return value;
    }

    @Override
    public List<PathNode> getTypeArguments() {
        return typeArguments;
    }

    public @Nullable SourceInfo getTypeArgumentsSourceInfo() {
        return typeArgumentsSourceInfo;
    }

    @Override
    public String getText() {
        return value.getName();
    }

    @Override
    public String format() {
        return typeArguments.isEmpty()
            ? value.format()
            : value.format() + "<" + typeArguments.stream()
                .map(path -> path.format())
                .collect(Collectors.joining(",")) + ">";
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        value = (IdentifierNode)value.accept(visitor);
        acceptChildren(typeArguments, visitor, PathNode.class);
    }

    @Override
    public TextSegmentNode deepClone() {
        return new TextSegmentNode(
            observableSelector, value.deepClone(), deepClone(typeArguments), getSelectorSourceInfo(),
            typeArgumentsSourceInfo, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(String text) { return value.getName().equals(text); }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TextSegmentNode other
            && observableSelector == other.observableSelector
            && value.equals(other.value) && typeArguments.equals(other.typeArguments)
            && Objects.equals(getSelectorSourceInfo(), other.getSelectorSourceInfo())
            && Objects.equals(typeArgumentsSourceInfo, other.typeArgumentsSourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(observableSelector, value, typeArguments,
            getSelectorSourceInfo(), typeArgumentsSourceInfo);
    }
}
