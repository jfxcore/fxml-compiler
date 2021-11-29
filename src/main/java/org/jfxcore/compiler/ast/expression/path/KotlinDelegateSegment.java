// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import javassist.CtField;
import org.jfxcore.compiler.ast.emit.EmitInvokeGetterNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.expression.util.KotlinDelegateHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class KotlinDelegateSegment extends Segment {

    private final CtField delegateField;

    public KotlinDelegateSegment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            CtField delegateField,
            ObservableKind observableKind) {
        super(name, displayName, type, valueType, observableKind);
        this.delegateField = Objects.requireNonNull(delegateField);
    }

    public CtField getDelegateField() {
        return delegateField;
    }

    @Override
    public CtClass getDeclaringClass() {
        return delegateField.getDeclaringClass();
    }

    @Override
    public ValueEmitterNode toEmitter(SourceInfo sourceInfo) {
        return new EmitInvokeGetterNode(
            KotlinDelegateHelper.getKotlinDelegateGetter(sourceInfo, delegateField),
            getTypeInstance(),
            getObservableKind(),
            true,
            sourceInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        KotlinDelegateSegment that = (KotlinDelegateSegment)o;
        return TypeHelper.equals(delegateField, that.delegateField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), TypeHelper.hashCode(delegateField));
    }

}
