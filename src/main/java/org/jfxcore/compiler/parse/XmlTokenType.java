// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

public enum XmlTokenType implements TokenType {

    WHITESPACE(null),
    IDENTIFIER(null),
    QUOTED_STRING(null),
    OPEN_BRACKET("<"),
    CLOSE_BRACKET(">"),
    OPEN_PROCESSING_INSTRUCTION("<?"),
    CLOSE_PROCESSING_INSTRUCTION("?>"),
    COLON(":"),
    EQUALS("="),
    SLASH("/"),
    COMMENT_START("<!--"),
    COMMENT_END("-->"),
    CDATA_START("<![CDATA["),
    CDATA_END("]]>"),
    OTHER(null);

    XmlTokenType(String symbol) {
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
        return this == WHITESPACE;
    }

}
