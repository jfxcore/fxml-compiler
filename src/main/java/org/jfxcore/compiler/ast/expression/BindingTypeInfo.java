// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;

/**
 * The semantic result of resolving an expression without constructing an emitter tree.
 */
public record BindingTypeInfo(
        TypeInstance emittedType,
        TypeInstance valueType,
        @Nullable TypeInstance valueSourceType,
        ValueSourceKind valueSourceKind,
        ObservableDependencyKind observableDependencyKind,
        @Nullable TypeDeclaration sourceDeclaringType,
        String sourceName,
        boolean function,
        boolean compiledPath,
        boolean mayBeNull,
        SourceInfo sourceInfo) {

    public TypeInstance type() {
        return valueSourceType != null ? valueSourceType : valueType;
    }

    public ObservableDependencyKind argumentDependencyKind() {
        if (observableDependencyKind != ObservableDependencyKind.NONE) {
            return observableDependencyKind;
        }

        return valueSourceType != null
            ? ObservableDependencyKind.VALUE
            : ObservableDependencyKind.NONE;
    }

    public boolean isObservableArgument() {
        return argumentDependencyKind() != ObservableDependencyKind.NONE;
    }
}
