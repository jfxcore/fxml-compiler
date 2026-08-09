// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;

/**
 * A literal keyword recognized by the compiled-expression grammar.
 * This node records syntax only; its value and type are assigned during expression lowering.
 */
public final class LiteralKeywordNode extends TextNode {

    public enum Kind {
        TRUE("true"),
        FALSE("false"),
        NULL("null");

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
        super(kind.getText(), sourceInfo);
        this.kind = checkNotNull(kind);
    }

    private LiteralKeywordNode(Kind kind, TypeNode type, SourceInfo sourceInfo) {
        super(kind.getText(), false, type, sourceInfo);
        this.kind = checkNotNull(kind);
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public LiteralKeywordNode deepClone() {
        return new LiteralKeywordNode(kind, getType().deepClone(), getSourceInfo()).copy(this);
    }
}
