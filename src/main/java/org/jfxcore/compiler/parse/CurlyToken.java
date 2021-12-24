// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.StringHelper;

public class CurlyToken extends AbstractToken<CurlyTokenType> {

    public static CurlyToken parse(String value, String line, SourceInfo sourceInfo) {
        CurlyTokenType type = parseTokenType(value);
        if (type == CurlyTokenType.STRING) {
            value = StringHelper.unescape(StringHelper.unquote(value));
        }

        return new CurlyToken(type, value, line, sourceInfo);
    }

    public CurlyToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo) {
        super(type, value, line, sourceInfo);
    }

    protected static CurlyTokenType parseTokenType(String token) {
        switch (token) {
            case "true":
            case "false":
                return CurlyTokenType.BOOLEAN;
            case "{":
                return CurlyTokenType.OPEN_CURLY;
            case "}":
                return CurlyTokenType.CLOSE_CURLY;
            case "(":
                return CurlyTokenType.OPEN_PAREN;
            case ")":
                return CurlyTokenType.CLOSE_PAREN;
            case "[":
                return CurlyTokenType.OPEN_BRACKET;
            case "]":
                return CurlyTokenType.CLOSE_BRACKET;
            case ".":
                return CurlyTokenType.DOT;
            case ",":
                return CurlyTokenType.COMMA;
            case ";":
                return CurlyTokenType.SEMICOLON;
            case ":":
                return CurlyTokenType.COLON;
            case "=":
                return CurlyTokenType.EQUALS;
            case "*":
                return CurlyTokenType.STAR;
            case "/":
                return CurlyTokenType.SLASH;
            default:
                if (token.length() > 1 && (token.startsWith("'") && token.endsWith("'")
                        || token.startsWith("\"") && token.endsWith("\""))) {
                    return CurlyTokenType.STRING;
                }

                if (isDimensionNumber(token)) {
                    return CurlyTokenType.NUMBER;
                }

                if (NameHelper.isJavaIdentifier(token) || NameHelper.isCssIdentifier(token)) {
                    return CurlyTokenType.IDENTIFIER;
                }

                return CurlyTokenType.UNKNOWN;
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
