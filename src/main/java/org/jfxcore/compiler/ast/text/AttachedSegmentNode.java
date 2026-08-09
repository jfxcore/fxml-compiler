// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * A restricted attached-property path segment such as {@code (GridPane.rowIndex)}.
 */
public class AttachedSegmentNode extends PathSegmentNode {

    private final boolean observableSelector;
    private final SourceInfo openParenSourceInfo;
    private final SourceInfo separatorSourceInfo;
    private final SourceInfo closeParenSourceInfo;
    private TextNode declaringType;
    private TextNode propertyName;

    public AttachedSegmentNode(
            boolean observableSelector,
            TextNode declaringType,
            TextNode propertyName,
            @Nullable SourceInfo selectorSourceInfo,
            SourceInfo openParenSourceInfo,
            SourceInfo separatorSourceInfo,
            SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        this(observableSelector, declaringType, propertyName, selectorSourceInfo,
             openParenSourceInfo, separatorSourceInfo, closeParenSourceInfo,
             null, sourceInfo);
    }

    private AttachedSegmentNode(
            boolean observableSelector,
            TextNode declaringType,
            TextNode propertyName,
            @Nullable SourceInfo selectorSourceInfo,
            SourceInfo openParenSourceInfo,
            SourceInfo separatorSourceInfo,
            SourceInfo closeParenSourceInfo,
            @Nullable TypeNode type,
            SourceInfo sourceInfo) {
        super(selectorSourceInfo, type != null ? type : new TypeNode(StringName, sourceInfo), sourceInfo);
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

    public TextNode getDeclaringType() {
        return declaringType;
    }

    public TextNode getPropertyName() {
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
    public List<PathNode> getWitnesses() {
        return List.of();
    }

    @Override
    public String getText() {
        return propertyName.getText();
    }

    @Override
    public String formatText() {
        return declaringType.formatText() + "." + propertyName.formatText();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        declaringType = (TextNode)declaringType.accept(visitor);
        propertyName = (TextNode)propertyName.accept(visitor);
    }

    @Override
    public AttachedSegmentNode deepClone() {
        return new AttachedSegmentNode(
            observableSelector,
            declaringType.deepClone(),
            propertyName.deepClone(),
            getSelectorSourceInfo(),
            openParenSourceInfo,
            separatorSourceInfo,
            closeParenSourceInfo,
            getType().deepClone(),
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(String text) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        AttachedSegmentNode that = (AttachedSegmentNode)o;
        return observableSelector == that.observableSelector
            && declaringType.equals(that.declaringType)
            && propertyName.equals(that.propertyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), observableSelector, declaringType, propertyName);
    }
}
