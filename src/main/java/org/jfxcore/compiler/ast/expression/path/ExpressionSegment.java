// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.ast.emit.NullableInfo;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.Objects;

/**
 * Adapts an already-analyzed expression value into the root segment of a property path.
 */
public final class ExpressionSegment extends Segment {

    private final BindingEmitterInfo emitterInfo;

    public ExpressionSegment(BindingEmitterInfo emitterInfo) {
        super("<expression>",
              "<expression>",
              emitterInfo.getType(),
              emitterInfo.getValueType(),
              emitterInfo.getValueSourceKind(),
              emitterInfo.getObservableDependencyKind());
        this.emitterInfo = Objects.requireNonNull(emitterInfo);
    }

    @Override
    public TypeDeclaration getDeclaringType() {
        return emitterInfo.getSourceDeclaringType();
    }

    @Override
    public boolean isNullable() {
        return NullableInfo.isNullable(emitterInfo.getValue(), true);
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return emitterInfo.getValue();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && emitterInfo.getValue().equals(((ExpressionSegment)o).emitterInfo.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), emitterInfo.getValue());
    }
}
