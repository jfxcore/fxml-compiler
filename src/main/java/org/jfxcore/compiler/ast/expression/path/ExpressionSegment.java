// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ValueSourceKind;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingTypeInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionResolution;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

/**
 * Adapts an already-analyzed expression value into the root segment of a property path.
 */
public final class ExpressionSegment extends Segment {

    private final ExpressionNode sourceExpressionSnapshot;
    private final SemanticKey semanticKey;
    private final ExpressionResolution resolution;

    public ExpressionSegment(
            ExpressionNode sourceExpression,
            ExpressionResolution resolution) {
        super("<expression>",
              "<expression>",
              resolution.getTypeInfo().type(),
              resolution.getTypeInfo().valueType(),
              resolution.getTypeInfo().valueSourceKind(),
              resolution.getTypeInfo().observableDependencyKind());
        this.resolution = Objects.requireNonNull(resolution);
        this.sourceExpressionSnapshot = Objects.requireNonNull(sourceExpression).deepClone();
        this.semanticKey = SemanticKey.of(resolution.getTypeInfo());
    }

    @Override
    public TypeDeclaration getDeclaringType() {
        return resolution.getTypeInfo().sourceDeclaringType();
    }

    @Override
    public boolean isNullable() {
        return resolution.getTypeInfo().mayBeNull();
    }

    @Override
    public ValueEmitterNode toEmitter(boolean requireNonNull, SourceInfo sourceInfo) {
        return resolution.toEmitter().getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }

        ExpressionSegment other = (ExpressionSegment)o;
        return sourceExpressionSnapshot.equals(other.sourceExpressionSnapshot)
            && semanticKey.equals(other.semanticKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sourceExpressionSnapshot, semanticKey);
    }

    private record SemanticKey(
            TypeInstance emittedType,
            TypeInstance valueType,
            @Nullable TypeInstance valueSourceType,
            ValueSourceKind valueSourceKind,
            ObservableDependencyKind observableDependencyKind,
            @Nullable TypeDeclaration sourceDeclaringType,
            String sourceName,
            boolean function,
            boolean compiledPath,
            boolean mayBeNull) {

        private static SemanticKey of(BindingTypeInfo info) {
            return new SemanticKey(
                info.emittedType(), info.valueType(), info.valueSourceType(),
                info.valueSourceKind(), info.observableDependencyKind(),
                info.sourceDeclaringType(), info.sourceName(), info.function(),
                info.compiledPath(), info.mayBeNull());
        }
    }
}
