// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import javassist.CtMethod;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.EmitInvokeGetterNode;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class GetterSegment extends Segment {

    private final CtMethod getter;
    private final boolean requireNonNull;
    private final boolean staticPropertyGetter;

    public GetterSegment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            CtMethod getter,
            boolean staticPropertyGetter,
            ObservableKind observableKind) {
        super(name, displayName, type, valueType, observableKind);
        this.getter = Objects.requireNonNull(getter);
        this.requireNonNull = observableKind.isNonNull();
        this.staticPropertyGetter = staticPropertyGetter;
    }

    public CtMethod getGetter() {
        return getter;
    }

    public boolean isStaticPropertyGetter() {
        return staticPropertyGetter;
    }

    @Override
    public boolean isNullable() {
        return !requireNonNull;
    }

    @Override
    public CtClass getDeclaringClass() {
        return getter.getDeclaringClass();
    }

    @Override
    public ValueEmitterNode toEmitter(SourceInfo sourceInfo) {
        return new EmitInvokeGetterNode(getter, getTypeInstance(), getObservableKind(), requireNonNull, sourceInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GetterSegment that = (GetterSegment)o;
        return TypeHelper.equals(getter, that.getter)
            && requireNonNull == that.requireNonNull
            && staticPropertyGetter == that.staticPropertyGetter;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), TypeHelper.hashCode(getter), requireNonNull, staticPropertyGetter);
    }

}
