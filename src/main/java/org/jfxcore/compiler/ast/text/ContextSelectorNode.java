// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.ast.IdentifierNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

public final class ContextSelectorNode extends AbstractSyntaxNode {

    private final @Nullable SourceInfo colonSourceInfo;
    private final SourceInfo selectorSourceInfo;
    private final @Nullable SourceInfo openAngleSourceInfo;
    private final @Nullable SourceInfo closeAngleSourceInfo;
    private final @Nullable SourceInfo openParenSourceInfo;
    private final @Nullable SourceInfo closeParenSourceInfo;
    private final ContextSelector selector;
    private IdentifierNode searchType;
    private NumberNode level;

    public ContextSelectorNode(
            ContextSelector selector, @Nullable IdentifierNode searchType, @Nullable NumberNode level,
            @Nullable SourceInfo colonSourceInfo, SourceInfo selectorSourceInfo,
            @Nullable SourceInfo openAngleSourceInfo, @Nullable SourceInfo closeAngleSourceInfo,
            @Nullable SourceInfo openParenSourceInfo, @Nullable SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.selector = checkNotNull(selector);
        this.searchType = searchType;
        this.level = level;
        this.colonSourceInfo = colonSourceInfo;
        this.selectorSourceInfo = checkNotNull(selectorSourceInfo);
        this.openAngleSourceInfo = openAngleSourceInfo;
        this.closeAngleSourceInfo = closeAngleSourceInfo;
        this.openParenSourceInfo = openParenSourceInfo;
        this.closeParenSourceInfo = closeParenSourceInfo;
    }

    public ContextSelector getSelector() {
        return selector;
    }

    public SourceInfo getSelectorSourceInfo() {
        return selectorSourceInfo;
    }

    public @Nullable IdentifierNode getSearchType() {
        return searchType;
    }

    public @Nullable NumberNode getLevel() {
        return level;
    }

    public @Nullable SourceInfo getColonSourceInfo() {
        return colonSourceInfo;
    }

    public @Nullable SourceInfo getOpenAngleSourceInfo() {
        return openAngleSourceInfo;
    }

    public @Nullable SourceInfo getCloseAngleSourceInfo() {
        return closeAngleSourceInfo;
    }

    public @Nullable SourceInfo getOpenParenSourceInfo() {
        return openParenSourceInfo;
    }

    public @Nullable SourceInfo getCloseParenSourceInfo() {
        return closeParenSourceInfo;
    }

    @Override
    public String format() {
        if (searchType == null && level == null) {
            return selector.getText();
        }

        var builder = new StringBuilder(selector.getText());
        if (searchType != null) builder.append('<').append(searchType.format()).append('>');
        if (level != null) builder.append('(').append(level.format()).append(')');
        return builder.toString();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        if (searchType != null) searchType = (IdentifierNode)searchType.accept(visitor);
        if (level != null) level = (NumberNode)level.accept(visitor);
    }

    @Override
    public ContextSelectorNode deepClone() {
        return new ContextSelectorNode(
            selector, searchType != null ? searchType.deepClone() : null,
            level != null ? level.deepClone() : null, colonSourceInfo, selectorSourceInfo,
            openAngleSourceInfo, closeAngleSourceInfo, openParenSourceInfo, closeParenSourceInfo,
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ContextSelectorNode other
            && selector == other.selector
            && Objects.equals(searchType, other.searchType)
            && Objects.equals(level, other.level);
    }

    @Override
    public int hashCode() { return Objects.hash(selector, searchType, level); }
}
