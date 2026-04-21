// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitUnwrapObservableNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;

public abstract class Segment {

    private final String name;
    private final String displayName;
    private final TypeInstance type;
    private final TypeInstance valueType;
    private final ValueSourceKind valueSourceKind;
    private final ObservableDependencyKind dependencyKind;

    protected Segment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            ValueSourceKind valueSourceKind,
            ObservableDependencyKind dependencyKind) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.valueType = valueType;
        this.valueSourceKind = valueSourceKind;
        this.dependencyKind = dependencyKind;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TypeInstance getTypeInstance() {
        return type;
    }

    public TypeInstance getValueTypeInstance() {
        return valueType;
    }

    public ValueSourceKind getValueSourceKind() {
        return valueSourceKind;
    }

    public ObservableDependencyKind getObservableDependencyKind() {
        return dependencyKind;
    }

    public boolean hasObservableDependency() {
        return dependencyKind != ObservableDependencyKind.NONE;
    }

    public boolean hasValueSource() {
        return valueSourceKind != ValueSourceKind.NONE;
    }

    public @Nullable TypeDeclaration getDeclaringType() {
        return null;
    }

    public boolean isNullable() {
        return true;
    }

    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        throw new UnsupportedOperationException();
    }

    public final ValueEmitterNode toValueEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        ValueEmitterNode emitter = toEmitter(requireNonNull, sourceInfo);

        if (hasValueSource()) {
            return new EmitUnwrapObservableNode(emitter);
        }

        return emitter;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Segment segment = (Segment)o;
        return name.equals(segment.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
