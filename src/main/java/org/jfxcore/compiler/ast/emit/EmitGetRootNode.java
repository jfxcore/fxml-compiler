// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.util.Classes.*;

public class EmitGetRootNode
        extends AbstractNode
        implements ValueEmitterNode, NullableInfo, ParentStackInfo {

    private ResolvedTypeNode type;

    public EmitGetRootNode(TypeInstance type, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.getOutput()
            .aload(context.getRuntimeContextLocal())
            .invokevirtual(context.getRuntimeContextClass(),
                           RuntimeContextGenerator.GET_ROOT_METHOD,
                           Descriptors.function(ObjectType()))
            .checkcast(type.getJvmType());
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
    }

    @Override
    public boolean needsParentStack() {
        return true;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitGetRootNode deepClone() {
        return new EmitGetRootNode(type.getTypeInstance(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitGetRootNode that = (EmitGetRootNode)o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }
}
