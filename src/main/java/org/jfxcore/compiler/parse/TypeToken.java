// Copyright (c) 2021, JFXcore. All rights reserved.
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

            case "<":
            case "(":
                return TypeTokenType.OPEN_ANGLE;

            case ">":
            case ")":
                return TypeTokenType.CLOSE_ANGLE;

            case "?":
                return TypeTokenType.WILDCARD;

            case ".":
                return TypeTokenType.DOT;

            case ",":
                return TypeTokenType.COMMA;
        }

        return TypeTokenType.IDENTIFIER;
    }

}
