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

public class BindingEmitterInfo {

    private final ValueEmitterNode value;
    private final TypeInstance valueType;
    private final TypeInstance valueSourceType;
    private final ValueSourceKind valueSourceKind;
    private final ObservableDependencyKind dependencyKind;
    private final TypeDeclaration sourceDeclaringType;
    private final String sourceName;
    private final boolean function;
    private final boolean compiledPath;
    private final SourceInfo sourceInfo;

    public BindingEmitterInfo(
            ValueEmitterNode value,
            TypeInstance valueType,
            TypeInstance valueSourceType,
            ValueSourceKind valueSourceKind,
            ObservableDependencyKind dependencyKind,
            @Nullable TypeDeclaration sourceDeclaringType,
            String sourceName,
            boolean function,
            boolean compiledPath,
            SourceInfo sourceInfo) {
        this.value = value;
        this.valueType = valueType;
        this.valueSourceType = valueSourceType;
        this.valueSourceKind = valueSourceKind;
        this.dependencyKind = dependencyKind;
        this.sourceDeclaringType = sourceDeclaringType;
        this.sourceName = sourceName;
        this.function = function;
        this.compiledPath = compiledPath;
        this.sourceInfo = sourceInfo;
    }

    public ValueEmitterNode getValue() {
        return value;
    }

    public TypeInstance getType() {
        return valueSourceType != null ? valueSourceType : valueType;
    }

    public TypeInstance getValueType() {
        return valueType;
    }

    public @Nullable TypeInstance getValueSourceType() {
        return valueSourceType;
    }

    public @Nullable TypeDeclaration getSourceDeclaringType() {
        return sourceDeclaringType;
    }

    public ValueSourceKind getValueSourceKind() {
        return valueSourceKind;
    }

    public ObservableDependencyKind getObservableDependencyKind() {
        return dependencyKind;
    }

    public String getSourceName() {
        return sourceName;
    }

    public boolean isFunction() {
        return function;
    }

    public boolean isCompiledPath() {
        return compiledPath;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }
}
