// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * A string literal with a decoded semantic value and its original inline-language lexeme.
 */
public final class StringLiteralNode extends TextNode {

    private final String lexeme;

    public StringLiteralNode(String value, String lexeme, SourceInfo sourceInfo) {
        this(value, lexeme, new TypeNode(StringName, sourceInfo), sourceInfo);
    }

    private StringLiteralNode(String value, String lexeme, TypeNode type, SourceInfo sourceInfo) {
        super(value, true, type, sourceInfo);
        this.lexeme = checkNotNull(lexeme);
    }

    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String formatText() {
        return lexeme;
    }

    @Override
    public StringLiteralNode deepClone() {
        return new StringLiteralNode(
            getText(), lexeme, getType().deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && lexeme.equals(((StringLiteralNode)o).lexeme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lexeme);
    }
}
