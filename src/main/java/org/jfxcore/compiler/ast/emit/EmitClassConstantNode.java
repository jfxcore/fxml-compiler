// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

import static org.jfxcore.compiler.util.Descriptors.*;

public class EmitClassConstantNode extends ReferenceableNode {

    private final CtClass declaringType;
    private final String fieldName;
    private ResolvedTypeNode type;

    public EmitClassConstantNode(
            @Nullable String id, TypeInstance type, CtClass declaringType, String fieldName, SourceInfo sourceInfo) {
        super(null, id, sourceInfo);
        this.type = new ResolvedTypeNode(checkNotNull(type), sourceInfo);
        this.declaringType = checkNotNull(declaringType);
        this.fieldName = checkNotNull(fieldName);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public ValueEmitterNode convertToLocalReference() {
        if (!isEmitInPreamble()) {
            throw new UnsupportedOperationException();
        }

        EmitObjectNode node = EmitObjectNode.loadLocal(type.getTypeInstance(), this, getSourceInfo());

        if (type.getJvmType().isPrimitive()) {
            type = new ResolvedTypeNode(
                new Resolver(type.getSourceInfo()).getTypeInstance(
                    TypeHelper.getBoxedType(type.getJvmType())),
                type.getSourceInfo());
        }

        return node;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        if (isEmitInPreamble()) {
            code.aload(0);
            code.getstatic(declaringType.getName(), fieldName, types(type.getJvmType()));
            code.dup_x1()
                .putfield(context.getLocalMarkupClass(), getId(), type.getJvmType());
            storeLocal(code, type.getJvmType());
        } else {
            code.getstatic(declaringType.getName(), fieldName, types(type.getJvmType()));
        }
    }

    @Override
    public EmitClassConstantNode deepClone() {
        return new EmitClassConstantNode(getId(), getType().getTypeInstance(), declaringType, fieldName, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitClassConstantNode that = (EmitClassConstantNode)o;
        return TypeHelper.equals(declaringType, that.declaringType) &&
            Objects.equals(getId(), that.getId()) &&
            fieldName.equals(that.fieldName) &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), TypeHelper.hashCode(declaringType), fieldName, type);
    }

}
