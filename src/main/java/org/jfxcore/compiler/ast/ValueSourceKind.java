// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.type.TypeDeclaration;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Classifies whether a type can serve as the direct source of a normal value binding.
 * <p>
 * This enum describes binding-source semantics for a resolved value. It distinguishes between plain values,
 * read-only observable values, and writable properties. The classification is used when validating whether
 * an expression can be bound directly, for example, in unidirectional or bidirectional bindings.
 * <p>
 * {@code ValueSourceKind} is intentionally separate from {@link ObservableDependencyKind}. A type may contribute
 * reevaluation dependencies without being a valid direct binding source. Observable collections are the main example:
 * they can invalidate expressions that depend on their contents, but they are not an {@code ObservableValue} and
 * therefore are not valid direct sources for ordinary value bindings.
 */
public enum ValueSourceKind {

    NONE,
    READONLY,
    WRITABLE;

    public static ValueSourceKind get(TypeDeclaration type) {
        if (type.subtypeOf(PropertyDecl())) {
            return WRITABLE;
        }

        if (type.subtypeOf(ObservableValueDecl())) {
            return READONLY;
        }

        return NONE;
    }

    public boolean isObservable() {
        return this != NONE;
    }

    public boolean isNonNull() {
        return this != NONE;
    }

    public boolean isReadOnly() {
        return this != WRITABLE;
    }

    public ValueSourceKind toReadOnly() {
        if (this == WRITABLE) {
            return READONLY;
        }

        return NONE;
    }
}

