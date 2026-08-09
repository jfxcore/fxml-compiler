// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

public enum BinaryOperator {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    VALUE_EQUAL("=="),
    VALUE_NOT_EQUAL("!="),
    IDENTITY_EQUAL("==="),
    IDENTITY_NOT_EQUAL("!=="),
    LOGICAL_AND("&&"),
    LOGICAL_OR("||");

    BinaryOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    private final String symbol;
}
