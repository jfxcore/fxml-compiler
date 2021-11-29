// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

public enum MeTokenType implements TokenType {

    UNKNOWN(MeTokenClass.NONE),
    KEYWORD(MeTokenClass.NONE),
    STRING(MeTokenClass.LITERAL),
    NUMBER(MeTokenClass.LITERAL),
    BOOLEAN(MeTokenClass.LITERAL),
    IDENTIFIER(MeTokenClass.LITERAL),
    OPEN_CURLY("{", MeTokenClass.DELIMITER),
    CLOSE_CURLY("}", MeTokenClass.DELIMITER),
    OPEN_PAREN("(", MeTokenClass.DELIMITER),
    CLOSE_PAREN(")", MeTokenClass.DELIMITER),
    OPEN_BRACKET("[", MeTokenClass.NONE),
    CLOSE_BRACKET("]", MeTokenClass.NONE),
    SEMICOLON(";", MeTokenClass.SEMI),
    NEWLINE("\\n", MeTokenClass.SEMI),
    DOT(".", MeTokenClass.NONE),
    STAR("*", MeTokenClass.NONE),
    COMMA(",", MeTokenClass.NONE),
    COLON(":", MeTokenClass.NONE),
    EQUALS("=", MeTokenClass.NONE);

    MeTokenType(MeTokenClass tokenClass) {
        this.symbol = null;
        this.tokenClass = tokenClass;
    }

    MeTokenType(String symbol, MeTokenClass tokenClass) {
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
        return getTokenClass() == MeTokenClass.SEMI;
    }

    public MeTokenClass getTokenClass() {
        return tokenClass;
    }

    private final String symbol;
    private final MeTokenClass tokenClass;

}
