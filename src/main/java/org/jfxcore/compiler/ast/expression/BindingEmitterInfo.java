// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

public final class BindingEmitterInfo {

    private final ValueEmitterNode value;
    private final BindingTypeInfo typeInfo;

    BindingEmitterInfo(ValueEmitterNode value, BindingTypeInfo typeInfo) {
        this.value = Objects.requireNonNull(value);
        this.typeInfo = Objects.requireNonNull(typeInfo);
    }

    public ValueEmitterNode getValue() {
        return value;
    }

    public TypeInstance getType() {
        return typeInfo.type();
    }

    public TypeInstance getValueType() {
        return typeInfo.valueType();
    }

    public @Nullable TypeInstance getValueSourceType() {
        return typeInfo.valueSourceType();
    }

    public @Nullable TypeDeclaration getSourceDeclaringType() {
        return typeInfo.sourceDeclaringType();
    }

    public ValueSourceKind getValueSourceKind() {
        return typeInfo.valueSourceKind();
    }

    public ObservableDependencyKind getObservableDependencyKind() {
        return typeInfo.observableDependencyKind();
    }

    public String getSourceName() {
        return typeInfo.sourceName();
    }

    public boolean isFunction() {
        return typeInfo.function();
    }

    public boolean isCompiledPath() {
        return typeInfo.compiledPath();
    }

    public SourceInfo getSourceInfo() {
        return typeInfo.sourceInfo();
    }
}
