// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jfxcore.compiler.diagnostic.SourceInfo;

public class TypeToken extends AbstractToken<TypeTokenType> {

    public TypeToken(String value, String line, SourceInfo sourceInfo) {
        super(parseTokenType(value), value, line, sourceInfo);
    }

    private static TypeTokenType parseTokenType(String value) {
        switch (value) {
            case "[":
                return TypeTokenType.OPEN_BRACKET;

            case "]":
                return TypeTokenType.CLOSE_BRACKET;

            // Braces are parsed as angle brackets to provide well-formed XML documents with a substitute
            // for angle brackets in generic types that doesn't require escaped symbols.
            case "<":
            case "(":
                return TypeTokenType.OPEN_ANGLE;

            case ">":
            case ")":
                return TypeTokenType.CLOSE_ANGLE;

            case "?":
                return TypeTokenType.WILDCARD;

            case "extends":
                return TypeTokenType.UPPER_BOUND;

            case "super":
                return TypeTokenType.LOWER_BOUND;

            case ".":
                return TypeTokenType.DOT;

            case ",":
                return TypeTokenType.COMMA;
        }

        return TypeTokenType.IDENTIFIER;
    }

}
