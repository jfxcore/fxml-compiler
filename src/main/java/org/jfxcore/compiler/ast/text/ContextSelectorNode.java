package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;

import java.util.Objects;

public class ContextSelectorNode extends TextNode {

    private TextNode selector;
    private TextNode searchType;
    private NumberNode level;

    public ContextSelectorNode(
            TextNode selector,
            @Nullable TextNode searchType,
            @Nullable NumberNode level,
            SourceInfo sourceInfo) {
        super(formatText(selector, searchType, level), sourceInfo);
        this.selector = selector;
        this.searchType = searchType;
        this.level = level;
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

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);

        selector = (TextNode)selector.accept(visitor);

        if (searchType != null) {
            searchType = (TextNode) searchType.accept(visitor);
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
            getSourceInfo());
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
            return selector.getText();
        }

        var builder = new StringBuilder(selector.getText()).append('[');

        if (typeName != null) {
            builder.append(typeName.getText());
            if (depth != null) {
                builder.append(':');
            }
        }

        if (depth != null) {
            builder.append(depth.getText());
        }

        builder.append(']');
        return builder.toString();
    }

}
