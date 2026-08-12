// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * A compiled-expression string literal with decoded value and original lexeme.
 */
public final class StringLiteralNode extends AbstractSyntaxNode {

    private final String value;
    private final String lexeme;

    public StringLiteralNode(String value, String lexeme, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.value = checkNotNull(value);
        this.lexeme = checkNotNull(lexeme);
    }

    public String getText() {
        return value;
    }

    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String format() {
        return lexeme;
    }

    @Override
    public StringLiteralNode deepClone() {
        return new StringLiteralNode(value, lexeme, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StringLiteralNode other
            && value.equals(other.value) && lexeme.equals(other.lexeme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, lexeme);
    }
}
