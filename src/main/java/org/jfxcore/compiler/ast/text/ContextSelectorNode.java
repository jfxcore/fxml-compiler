// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;

import java.util.Objects;

public class ContextSelectorNode extends DerivedTextNode {

    private final @Nullable SourceInfo colonSourceInfo;
    private final @Nullable SourceInfo openParenSourceInfo;
    private final @Nullable SourceInfo commaSourceInfo;
    private final @Nullable SourceInfo closeParenSourceInfo;
    private TextNode selector;
    private TextNode searchType;
    private NumberNode level;

    public ContextSelectorNode(
            TextNode selector,
            @Nullable TextNode searchType,
            @Nullable NumberNode level,
            @Nullable SourceInfo colonSourceInfo,
            @Nullable SourceInfo openParenSourceInfo,
            @Nullable SourceInfo commaSourceInfo,
            @Nullable SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.selector = checkNotNull(selector);
        this.searchType = searchType;
        this.level = level;
        this.colonSourceInfo = colonSourceInfo;
        this.openParenSourceInfo = openParenSourceInfo;
        this.commaSourceInfo = commaSourceInfo;
        this.closeParenSourceInfo = closeParenSourceInfo;
    }

    private ContextSelectorNode(
            TextNode selector,
            @Nullable TextNode searchType,
            @Nullable NumberNode level,
            @Nullable SourceInfo colonSourceInfo,
            @Nullable SourceInfo openParenSourceInfo,
            @Nullable SourceInfo commaSourceInfo,
            @Nullable SourceInfo closeParenSourceInfo,
            TypeNode type,
            SourceInfo sourceInfo) {
        super(type, sourceInfo);
        this.selector = checkNotNull(selector);
        this.searchType = searchType;
        this.level = level;
        this.colonSourceInfo = colonSourceInfo;
        this.openParenSourceInfo = openParenSourceInfo;
        this.commaSourceInfo = commaSourceInfo;
        this.closeParenSourceInfo = closeParenSourceInfo;
    }

    public TextNode getSelector() {
        return selector;
    }

    public @Nullable TextNode getSearchType() {
        return searchType;
    }

    public @Nullable NumberNode getLevel() {
        return level;
    }

    public @Nullable SourceInfo getColonSourceInfo() {
        return colonSourceInfo;
    }

    public @Nullable SourceInfo getOpenParenSourceInfo() {
        return openParenSourceInfo;
    }

    public @Nullable SourceInfo getCommaSourceInfo() {
        return commaSourceInfo;
    }

    public @Nullable SourceInfo getCloseParenSourceInfo() {
        return closeParenSourceInfo;
    }

    @Override
    public String formatText() {
        return formatText(selector, searchType, level);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);

        selector = (TextNode)selector.accept(visitor);

        if (searchType != null) {
            searchType = (TextNode)searchType.accept(visitor);
        }

        if (level != null) {
            level = (NumberNode)level.accept(visitor);
        }
    }

    @Override
    public ContextSelectorNode deepClone() {
        return new ContextSelectorNode(
            selector.deepClone(),
            searchType != null ? searchType.deepClone() : null,
            level != null ? level.deepClone() : null,
            colonSourceInfo,
            openParenSourceInfo,
            commaSourceInfo,
            closeParenSourceInfo,
            getType().deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ContextSelectorNode that = (ContextSelectorNode) o;
        return Objects.equals(selector, that.selector)
            && Objects.equals(searchType, that.searchType)
            && Objects.equals(level, that.level);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), selector, searchType, level);
    }

    private static String formatText(TextNode selector, @Nullable TextNode typeName, @Nullable NumberNode depth) {
        if (typeName == null && depth == null) {
            return selector.formatText();
        }

        var builder = new StringBuilder(selector.formatText()).append('(');

        if (typeName != null) {
            builder.append(typeName.formatText());
            if (depth != null) {
                builder.append(", ");
            }
        }

        if (depth != null) {
            builder.append(depth.formatText());
        }

        builder.append(')');
        return builder.toString();
    }
}
