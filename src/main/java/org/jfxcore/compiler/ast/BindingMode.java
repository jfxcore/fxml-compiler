// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

public enum BindingMode {
    ONCE,
    CONTENT,
    UNIDIRECTIONAL,
    UNIDIRECTIONAL_CONTENT,
    BIDIRECTIONAL,
    BIDIRECTIONAL_CONTENT;

    public boolean isUnidirectional() {
        return this == UNIDIRECTIONAL || this == UNIDIRECTIONAL_CONTENT;
    }

    public boolean isBidirectional() {
        return this == BIDIRECTIONAL || this == BIDIRECTIONAL_CONTENT;
    }

    public boolean isContent() {
        return this == CONTENT || this == UNIDIRECTIONAL_CONTENT || this == BIDIRECTIONAL_CONTENT;
    }

    public boolean isObservable() {
        return this != ONCE && this != CONTENT;
    }
}
