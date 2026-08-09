// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

/**
 * Loads one ordered external input from a compiled-expression helper parameter.
 */
public final class EmitExpressionInputNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final ResolvedTypeNode type;
    private final int localIndex;

    public EmitExpressionInputNode(TypeInstance type, int localIndex, SourceInfo sourceInfo) {
        super(sourceInfo);

        if (localIndex < 0) {
            throw new IllegalArgumentException("localIndex");
        }

        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.localIndex = localIndex;
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public boolean isNullable() {
        return !type.getTypeInstance().isPrimitive();
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.getOutput().load(type.getTypeDeclaration(), localIndex);
    }

    @Override
    public EmitExpressionInputNode deepClone() {
        return new EmitExpressionInputNode(type.getTypeInstance(), localIndex, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof EmitExpressionInputNode that
            && localIndex == that.localIndex
            && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, localIndex);
    }
}
