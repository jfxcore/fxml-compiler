// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jfxcore.compiler.type.TypeDeclaration;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Classifies how a type participates in binding reevaluation.
 * <p>
 * This enum describes the runtime dependency behavior of a resolved value. It is used to determine whether
 * a value should be observed for invalidation and, if so, what kind of observation is required.
 * <p>
 * Unlike {@link ValueSourceKind}, this classification is not about whether a value can be bound directly.
 * It only describes whether the value can cause a dependent expression to be reevaluated at runtime.
 * Typical examples include observable values, which contribute value-based dependencies, and observable
 * collections, which contribute content-based dependencies. Plain values contribute no dependency information.
 * <p>
 * This separation allows the compiler to support reevaluation through observable collections while requiring
 * {@code ObservableValue}-based types for direct value-binding sources.
 */
public enum ObservableDependencyKind {

    NONE,
    VALUE,
    CONTENT;

    public static ObservableDependencyKind get(TypeDeclaration type) {
        if (type.subtypeOf(ObservableValueDecl())) {
            return VALUE;
        }

        if (type.subtypeOf(ObservableListDecl())
                || type.subtypeOf(ObservableSetDecl())
                || type.subtypeOf(ObservableMapDecl())) {
            return CONTENT;
        }

        return NONE;
    }
}

