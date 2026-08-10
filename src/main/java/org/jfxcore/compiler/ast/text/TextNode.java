// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class TextNode extends AbstractNode implements ValueNode {

    private final boolean rawText;
    private final String text;
    private TypeNode type;

    public static TextNode createRawUnresolved(String text, SourceInfo sourceInfo) {
        return new TextNode(text, true, new TypeNode(StringName, sourceInfo), sourceInfo);
    }

    public TextNode(String text, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.rawText = false;
        this.text = checkNotNull(text);
        this.type = new TypeNode(StringName, sourceInfo);
    }

    protected TextNode(String text, boolean rawText, TypeNode type, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.rawText = rawText;
        this.text = checkNotNull(text);
        this.type = checkNotNull(type);
    }

    /**
     * Raw text will not be interpreted as a comma-separated list.
     */
    public boolean isRawText() {
        return rawText;
    }

    public String getText() {
        return text;
    }

    /**
     * Returns the text used when this syntax node is embedded in a formatted compound node.
     * Lexical nodes normally use their semantic text directly, while nodes that retain a distinct
     * source lexeme can override this method without changing {@link #getText()}.
     */
    public String formatText() {
        return text;
    }

    @Override
    public String toString() {
        return formatText();
    }

    @Override
    public TypeNode getType() {
        return type;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (TypeNode)type.accept(visitor);
    }

    @Override
    public TextNode deepClone() {
        return new TextNode(text, rawText, type.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextNode textNode = (TextNode)o;
        return rawText == textNode.rawText
            && Objects.equals(text, textNode.text)
            && Objects.equals(type, textNode.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawText, text, type);
    }

    protected static String formatValue(ValueNode node) {
        return node instanceof TextNode textNode
            ? textNode.formatText()
            : node.getType().getMarkupName();
    }
}
