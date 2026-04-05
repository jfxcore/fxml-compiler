// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.ast.emit.EmitInvokeGetterNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.util.KotlinDelegateHelper;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.ObservableKind;
import java.util.Objects;

public class KotlinDelegateSegment extends Segment {

    private final FieldDeclaration delegateField;

    public KotlinDelegateSegment(
            String name,
            String displayName,
            TypeInstance type,
            TypeInstance valueType,
            FieldDeclaration delegateField,
            ObservableKind observableKind) {
        super(name, displayName, type, valueType, observableKind);
        this.delegateField = Objects.requireNonNull(delegateField);
    }

    public FieldDeclaration getDelegateField() {
        return delegateField;
    }

    @Override
    public TypeDeclaration getDeclaringType() {
        return delegateField.declaringType();
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return new EmitInvokeGetterNode(
            KotlinDelegateHelper.getKotlinDelegateGetter(delegateField),
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
        return delegateField.equals(that.delegateField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegateField);
    }
}
