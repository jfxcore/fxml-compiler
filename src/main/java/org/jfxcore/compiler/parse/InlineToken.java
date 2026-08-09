// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.StringHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InlineToken extends CurlyToken {

    private static final Set<String> KEYWORDS = new HashSet<>(List.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "throw", "throws", "transient", "try", "void",
        "volatile", "while"));

    private final String lexeme;

    public InlineToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo) {
        this(type, value, value, line, sourceInfo);
    }

    private InlineToken(
            CurlyTokenType type, String value, String lexeme, String line, SourceInfo sourceInfo) {
        super(type, value, line, sourceInfo);
        this.lexeme = lexeme;
    }

    public static InlineToken parse(String value, String line, SourceInfo sourceInfo) {
        String lexeme = value;
        CurlyTokenType type = parseTokenType(value);
        if (type == CurlyTokenType.STRING) {
            value = StringHelper.unescape(StringHelper.unquote(value));
        }

        return new InlineToken(type, value, lexeme, line, sourceInfo);
    }

    public String getLexeme() {
        return lexeme;
    }

    protected static CurlyTokenType parseTokenType(String token) {
        if (KEYWORDS.contains(token)) {
            return CurlyTokenType.KEYWORD;
        }

        return CurlyToken.parseTokenType(token);
    }
}
