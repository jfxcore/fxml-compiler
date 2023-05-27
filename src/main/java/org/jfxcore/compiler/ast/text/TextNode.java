// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class TextNode extends AbstractNode implements ValueNode {

    private final boolean rawText;
    private final String text;
    private TypeNode type;

    public static TextNode createRawUnresolved(String text, SourceInfo sourceInfo) {
        return new TextNode(text, true, new TypeNode(Classes.StringName, sourceInfo), sourceInfo);
    }

    public static TextNode createRawResolved(String text, SourceInfo sourceInfo) {
        return new TextNode(text, true, new ResolvedTypeNode(TypeInstance.StringType(), sourceInfo), sourceInfo);
    }

    public TextNode(String text, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.rawText = false;
        this.text = checkNotNull(text);
        this.type = new TypeNode(Classes.StringName, sourceInfo);
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

    @Override
    public String toString() {
        return text;
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
        return new TextNode(text, rawText, type.deepClone(), getSourceInfo());
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

}
