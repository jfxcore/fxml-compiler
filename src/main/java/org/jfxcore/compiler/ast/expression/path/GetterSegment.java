// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.ast.emit.EmitInvokeGetterNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ObservableKind;
import java.util.Objects;

public class GetterSegment extends Segment {

    private final MethodDeclaration getter;
    private final ObservableKind observableKind;
    private final boolean staticPropertyGetter;

    public GetterSegment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            MethodDeclaration getter,
            boolean staticPropertyGetter,
            ObservableKind observableKind) {
        super(name, displayName, type, valueType, observableKind);
        this.getter = Objects.requireNonNull(getter);
        this.observableKind = observableKind;
        this.staticPropertyGetter = staticPropertyGetter;
    }

    public MethodDeclaration getGetter() {
        return getter;
    }

    @Override
    public TypeDeclaration getDeclaringType() {
        return getter.declaringType();
    }

    public boolean isStaticPropertyGetter() {
        return staticPropertyGetter;
    }

    @Override
    public boolean isNullable() {
        return !observableKind.isNonNull();
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return new EmitInvokeGetterNode(
            getter, getTypeInstance(), observableKind, observableKind.isNonNull() || requireNonNull, sourceInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GetterSegment that = (GetterSegment)o;
        return getter.equals(that.getter)
            && observableKind == that.observableKind
            && staticPropertyGetter == that.staticPropertyGetter;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getter, observableKind, staticPropertyGetter);
    }
}
