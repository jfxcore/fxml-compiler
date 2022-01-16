// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import javassist.NotFoundException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;

public enum ObservableKind {

    NONE,
    FX_OBSERVABLE,
    FX_PROPERTY;

    public static ObservableKind get(TypeInstance type) {
        return get(type.jvmType());
    }

    public static ObservableKind get(CtClass type) {
        try {
            if (type.subtypeOf(Classes.PropertyType())) {
                return FX_PROPERTY;
            }

            if (type.subtypeOf(Classes.ObservableValueType())) {
                return FX_OBSERVABLE;
            }
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
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
