// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.IdentifierNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.List;
import java.util.Objects;

/**
 * A restricted attached-property path segment such as {@code (GridPane.rowIndex)}.
 */
public final class AttachedSegmentNode extends PathSegmentNode {

    private final boolean observableSelector;
    private final SourceInfo openParenSourceInfo;
    private final SourceInfo separatorSourceInfo;
    private final SourceInfo closeParenSourceInfo;
    private IdentifierNode declaringType;
    private IdentifierNode propertyName;

    public AttachedSegmentNode(
            boolean observableSelector,
            IdentifierNode declaringType,
            IdentifierNode propertyName,
            @Nullable SourceInfo selectorSourceInfo,
            SourceInfo openParenSourceInfo,
            SourceInfo separatorSourceInfo,
            SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        super(selectorSourceInfo, sourceInfo);
        this.observableSelector = observableSelector;
        this.declaringType = checkNotNull(declaringType);
        this.propertyName = checkNotNull(propertyName);
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.separatorSourceInfo = checkNotNull(separatorSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    @Override
    public boolean isObservableSelector() {
        return observableSelector;
    }

    public IdentifierNode getDeclaringType() {
        return declaringType;
    }

    public IdentifierNode getPropertyName() {
        return propertyName;
    }

    public SourceInfo getOpenParenSourceInfo() {
        return openParenSourceInfo;
    }

    public SourceInfo getSeparatorSourceInfo() {
        return separatorSourceInfo;
    }

    public SourceInfo getCloseParenSourceInfo() {
        return closeParenSourceInfo;
    }

    @Override
    public List<PathNode> getTypeArguments() {
        return List.of();
    }

    @Override
    public String getText() {
        return propertyName.getName();
    }

    @Override
    public String format() {
        return declaringType.format() + "." + propertyName.format();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        declaringType = (IdentifierNode)declaringType.accept(visitor);
        propertyName = (IdentifierNode)propertyName.accept(visitor);
    }

    @Override
    public AttachedSegmentNode deepClone() {
        return new AttachedSegmentNode(
            observableSelector, declaringType.deepClone(), propertyName.deepClone(), getSelectorSourceInfo(),
            openParenSourceInfo, separatorSourceInfo, closeParenSourceInfo, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(String text) { return false; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AttachedSegmentNode other
            && observableSelector == other.observableSelector
            && declaringType.equals(other.declaringType) && propertyName.equals(other.propertyName)
            && Objects.equals(getSelectorSourceInfo(), other.getSelectorSourceInfo());
    }

    @Override
    public int hashCode() {
        return Objects.hash(observableSelector, declaringType, propertyName, getSelectorSourceInfo());
    }
}
