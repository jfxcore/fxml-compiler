// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

public enum UnaryOperator {
    PLUS("+"),
    MINUS("-"),
    NOT("!"),
    BOOLIFY("!!");

    UnaryOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    private final String symbol;
}
