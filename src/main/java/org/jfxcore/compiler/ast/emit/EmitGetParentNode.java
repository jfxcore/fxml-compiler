// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.RuntimeContextGenerator;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class EmitGetParentNode
        extends AbstractNode
        implements ValueEmitterNode, NullableInfo, ParentStackInfo {

    private final int parentIndex;
    private int parentIndexAdjustment;
    private ResolvedTypeNode type;

    public EmitGetParentNode(TypeInstance type, int parentIndex, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.parentIndex = parentIndex;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.getOutput()
            .aload(context.getRuntimeContextLocal())
            .iconst(parentIndex - parentIndexAdjustment)
            .invokevirtual(
                context.getRuntimeContextClass(),
                RuntimeContextGenerator.GET_ANCESTOR_METHOD,
                function(ObjectType(), CtClass.intType))
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

    public void setParentIndexAdjustment(int value) {
        parentIndexAdjustment = value;
    }

    @Override
    public EmitGetParentNode deepClone() {
        return new EmitGetParentNode(type.getTypeInstance(), parentIndex, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitGetParentNode that = (EmitGetParentNode)o;
        return parentIndex == that.parentIndex && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentIndex, type);
    }
}
