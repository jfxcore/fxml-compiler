// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractSyntaxNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.Objects;

/**
 * Boolean or null keyword syntax in a compiled expression.
 */
public final class LiteralKeywordNode extends AbstractSyntaxNode {

    public enum Kind {
        TRUE("true"), FALSE("false"), NULL("null");

        private final String text;

        Kind(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    private final Kind kind;

    public static @Nullable LiteralKeywordNode tryCreate(String text, SourceInfo sourceInfo) {
        Kind kind = switch (text) {
            case "true" -> Kind.TRUE;
            case "false" -> Kind.FALSE;
            case "null" -> Kind.NULL;
            default -> null;
        };

        return kind != null ? new LiteralKeywordNode(kind, sourceInfo) : null;
    }

    public LiteralKeywordNode(Kind kind, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.kind = checkNotNull(kind);
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return kind.getText();
    }

    @Override
    public String format() {
        return kind.getText();
    }

    @Override
    public LiteralKeywordNode deepClone() {
        return new LiteralKeywordNode(kind, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LiteralKeywordNode other && kind == other.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind);
    }
}
