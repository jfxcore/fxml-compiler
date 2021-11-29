// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import javassist.CtField;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.EmitGetFieldNode;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class FieldSegment extends Segment {

    private final CtField field;
    private final boolean requireNonNull;

    public FieldSegment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            CtField field,
            ObservableKind observableKind) {
        super(name, displayName, type, valueType, observableKind);
        this.field = Objects.requireNonNull(field);
        this.requireNonNull = observableKind.isNonNull();
    }

    public CtField getField() {
        return field;
    }

    @Override
    public boolean isNullable() {
        return !requireNonNull;
    }

    @Override
    public CtClass getDeclaringClass() {
        return field.getDeclaringClass();
    }

    @Override
    public ValueEmitterNode toEmitter(SourceInfo sourceInfo) {
        return new EmitGetFieldNode(field, getTypeInstance(), getObservableKind(), requireNonNull, sourceInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FieldSegment that = (FieldSegment)o;
        return TypeHelper.equals(field, that.field) && requireNonNull == that.requireNonNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), TypeHelper.hashCode(field), requireNonNull);
    }

}
