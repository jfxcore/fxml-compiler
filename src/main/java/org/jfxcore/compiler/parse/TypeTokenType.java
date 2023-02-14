// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

public enum TypeTokenType implements TokenType {

    IDENTIFIER(null),
    DOT("."),
    COMMA(","),
    WILDCARD("?"),
    UPPER_BOUND("extends"),
    LOWER_BOUND("super"),
    OPEN_BRACKET("["),
    CLOSE_BRACKET("]"),
    OPEN_ANGLE("<"),
    CLOSE_ANGLE(">");

    TypeTokenType(String symbol) {
        this.symbol = symbol;
    }

    private final String symbol;

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean isIdentifier() {
        return this == IDENTIFIER;
    }

    @Override
    public boolean isWhitespace() {
        return false;
    }

}
