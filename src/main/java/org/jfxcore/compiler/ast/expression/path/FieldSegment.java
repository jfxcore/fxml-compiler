// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.EmitGetFieldNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

public class FieldSegment extends Segment {

    private final FieldDeclaration field;

    public FieldSegment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            FieldDeclaration field,
            ValueSourceKind valueSourceKind,
            ObservableDependencyKind dependencyKind) {
        super(name, displayName, type, valueType, valueSourceKind, dependencyKind);
        this.field = Objects.requireNonNull(field);
    }

    public FieldDeclaration getField() {
        return field;
    }

    @Override
    public TypeDeclaration getDeclaringType() {
        return field.declaringType();
    }

    @Override
    public boolean isNullable() {
        return !getValueSourceKind().isNonNull();
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return new EmitGetFieldNode(
            field, getTypeInstance(), getValueSourceKind().isNonNull() || requireNonNull, false, sourceInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FieldSegment that = (FieldSegment)o;
        return field.equals(that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), field);
    }
}
