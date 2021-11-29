// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.StringHelper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MeToken extends AbstractToken<MeTokenType> {

    private static final Set<String> KEYWORDS = new HashSet<>(List.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "extends", "false", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "throw", "throws", "transient", "true", "try", "void",
        "volatile", "while"));

    private final boolean compact;

    public static MeToken parse(String intrinsicPrefix, String value, String line, SourceInfo sourceInfo) {
        MeTokenType type = parseTokenType(intrinsicPrefix, value);
        if (type == MeTokenType.STRING) {
            value = StringHelper.unescape(StringHelper.unquote(value));
        }

        return new MeToken(type, value, false, line, sourceInfo);
    }

    public MeToken(MeTokenType type, String value, String line, SourceInfo sourceInfo) {
        super(type, value, line, sourceInfo);
        this.compact = false;
    }

    public MeToken(MeTokenType type, String value, boolean compact, String line, SourceInfo sourceInfo) {
        super(type, value, line, sourceInfo);
        this.compact = compact;
    }

    public boolean isCompact() {
        return compact;
    }

    private static MeTokenType parseTokenType(String intrinsicPrefix, String token) {
        switch (token) {
            case "true":
            case "false":
                return MeTokenType.BOOLEAN;
            case "{":
                return MeTokenType.OPEN_CURLY;
            case "}":
                return MeTokenType.CLOSE_CURLY;
            case "(":
                return MeTokenType.OPEN_PAREN;
            case ")":
                return MeTokenType.CLOSE_PAREN;
            case "[":
                return MeTokenType.OPEN_BRACKET;
            case "]":
                return MeTokenType.CLOSE_BRACKET;
            case ".":
                return MeTokenType.DOT;
            case ",":
                return MeTokenType.COMMA;
            case ";":
                return MeTokenType.SEMICOLON;
            case ":":
                return MeTokenType.COLON;
            case "=":
                return MeTokenType.EQUALS;
            case "*":
                return MeTokenType.STAR;
            default:
                if (token.length() > 1 && (token.startsWith("'") && token.endsWith("'")
                        || token.startsWith("\"") && token.endsWith("\""))) {
                    return MeTokenType.STRING;
                }

                if (token.startsWith(intrinsicPrefix)) {
                    if (NameHelper.isJavaIdentifier(token.substring(intrinsicPrefix.length()).trim())) {
                        return MeTokenType.IDENTIFIER;
                    }
                }

                if (KEYWORDS.contains(token)) {
                    return MeTokenType.KEYWORD;
                }

                if (isDimensionNumber(token)) {
                    return MeTokenType.NUMBER;
                }

                if (NameHelper.isJavaIdentifier(token) || NameHelper.isCssIdentifier(token)) {
                    return MeTokenType.IDENTIFIER;
                }

                return MeTokenType.UNKNOWN;
        }
    }

    private static boolean isDimensionNumber(String value) {
        int i = 0;
        for (; i < value.length(); ++i) {
            if (!Character.isDigit(value.charAt(i)) && value.charAt(i) != '.') {
                break;
            }
        }

        if (i > 0 && i < value.length()) {
            String dim = value.substring(i);

            if (!NameHelper.isJavaIdentifier(dim) && !NameHelper.isCssIdentifier(dim)) {
                return false;
            }

            value = value.substring(0, i);
        }

        try {
            Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return false;
        }

        return true;
    }

}
