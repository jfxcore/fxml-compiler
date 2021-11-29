// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import org.jetbrains.annotations.Nullable;

public interface TokenType {

    @Nullable
    String getSymbol();

    boolean isIdentifier();

    boolean isWhitespace();

}
