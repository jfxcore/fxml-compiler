// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.type.TypeDeclaration;

import static org.jfxcore.compiler.type.TypeSymbols.*;

public enum ObservableKind {

    NONE,
    FX_OBSERVABLE,
    FX_PROPERTY;

    public static ObservableKind get(TypeDeclaration type) {
        if (type.subtypeOf(PropertyDecl())) {
            return FX_PROPERTY;
        }

        if (type.subtypeOf(ObservableValueDecl())) {
            return FX_OBSERVABLE;
        }

        return NONE;
    }

    public boolean isNonNull() {
        return this == FX_OBSERVABLE || this == FX_PROPERTY;
    }

    public boolean isReadOnly() {
        return this == NONE || this == FX_OBSERVABLE;
    }

    public ObservableKind toReadOnly() {
        if (this == FX_PROPERTY) {
            return FX_OBSERVABLE;
        }

        return NONE;
    }
}
