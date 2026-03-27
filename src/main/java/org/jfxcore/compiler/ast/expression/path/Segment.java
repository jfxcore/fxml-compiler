// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.EmitUnwrapObservableNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ObservableKind;

public abstract class Segment {

    private final String name;
    private final String displayName;
    private final TypeInstance type;
    private final TypeInstance valueType;
    private final ObservableKind observableKind;

    protected Segment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            ObservableKind observableKind) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.valueType = valueType;
        this.observableKind = observableKind;
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

    public ObservableKind getObservableKind() {
        return observableKind;
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

        if (observableKind != ObservableKind.NONE) {
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
