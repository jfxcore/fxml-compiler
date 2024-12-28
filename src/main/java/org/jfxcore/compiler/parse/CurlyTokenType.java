// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

public enum CurlyTokenType implements TokenType {

    UNKNOWN(CurlyTokenClass.NONE),
    KEYWORD(CurlyTokenClass.NONE),
    STRING(CurlyTokenClass.LITERAL),
    NUMBER(CurlyTokenClass.LITERAL),
    BOOLEAN(CurlyTokenClass.LITERAL),
    IDENTIFIER(CurlyTokenClass.LITERAL),
    OPEN_CURLY("{", CurlyTokenClass.NONE),
    CLOSE_CURLY("}", CurlyTokenClass.DELIMITER),
    OPEN_PAREN("(", CurlyTokenClass.NONE),
    CLOSE_PAREN(")", CurlyTokenClass.DELIMITER),
    OPEN_BRACKET("[", CurlyTokenClass.NONE),
    CLOSE_BRACKET("]", CurlyTokenClass.NONE),
    OPEN_ANGLE("<", CurlyTokenClass.NONE),
    CLOSE_ANGLE(">", CurlyTokenClass.NONE),
    SEMICOLON(";", CurlyTokenClass.SEMI),
    NEWLINE("\\n", CurlyTokenClass.SEMI),
    DOT(".", CurlyTokenClass.NONE),
    STAR("*", CurlyTokenClass.NONE),
    COMMA(",", CurlyTokenClass.DELIMITER),
    COLON(":", CurlyTokenClass.NONE),
    EQUALS("=", CurlyTokenClass.DELIMITER),
    SLASH("/", CurlyTokenClass.NONE);

    CurlyTokenType(CurlyTokenClass tokenClass) {
        this.symbol = null;
        this.tokenClass = tokenClass;
    }

    CurlyTokenType(String symbol, CurlyTokenClass tokenClass) {
        this.symbol = symbol;
        this.tokenClass = tokenClass;
    }

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
        return getTokenClass() == CurlyTokenClass.SEMI;
    }

    public CurlyTokenClass getTokenClass() {
        return tokenClass;
    }

    private final String symbol;
    private final CurlyTokenClass tokenClass;

}
