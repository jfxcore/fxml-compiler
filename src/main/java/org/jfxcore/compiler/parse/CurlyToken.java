// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.NumberUtil;

public class CurlyToken extends AbstractToken<CurlyTokenType> {

    protected CurlyToken(CurlyTokenType type, String value, String line, SourceInfo sourceInfo) {
        super(type, value, line, sourceInfo);
    }

    protected static CurlyTokenType parseTokenType(String token) {
        switch (token) {
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
            case "<":
                return CurlyTokenType.OPEN_ANGLE;
            case ">":
                return CurlyTokenType.CLOSE_ANGLE;
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
            case "+":
                return CurlyTokenType.PLUS;
            case "-":
                return CurlyTokenType.MINUS;
            case "/":
                return CurlyTokenType.SLASH;
            case "<=":
                return CurlyTokenType.LESS_THAN_OR_EQUAL;
            case ">=":
                return CurlyTokenType.GREATER_THAN_OR_EQUAL;
            case "==":
                return CurlyTokenType.VALUE_EQUALITY;
            case "!=":
                return CurlyTokenType.VALUE_INEQUALITY;
            case "===":
                return CurlyTokenType.IDENTITY_EQUALITY;
            case "!==":
                return CurlyTokenType.IDENTITY_INEQUALITY;
            case "&&":
                return CurlyTokenType.LOGICAL_AND;
            case "||":
                return CurlyTokenType.LOGICAL_OR;
            case "!":
                return CurlyTokenType.NOT;
            case "!!":
                return CurlyTokenType.BOOLIFY;
            default:
                if (token.length() > 1 && (token.startsWith("'") && token.endsWith("'")
                        || token.startsWith("\"") && token.endsWith("\""))) {
                    return CurlyTokenType.STRING;
                }

                if (isNumber(token)) {
                    return CurlyTokenType.NUMBER;
                }

                if (NameHelper.isJavaIdentifier(token)) {
                    return CurlyTokenType.IDENTIFIER;
                }

                return CurlyTokenType.UNKNOWN;
        }
    }

    private static boolean isNumber(String value) {
        try {
            NumberUtil.parse(value);
        } catch (NumberFormatException ignored) {
            return false;
        }

        return true;
    }

}
