// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Objects;

public class EmitClassConstantNode extends ReferenceableNode {

    private final TypeDeclaration declaringType;
    private final String fieldName;
    private ResolvedTypeNode type;

    public EmitClassConstantNode(
            @Nullable String id,
            TypeInstance type,
            TypeDeclaration declaringType,
            String fieldName,
            SourceInfo sourceInfo) {
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

        if (type.getTypeDeclaration().isPrimitive()) {
            type = new ResolvedTypeNode(
                new TypeInvoker(type.getSourceInfo()).invokeType(type.getTypeDeclaration().boxed()),
                type.getSourceInfo());
        }

        return node;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        if (isEmitInPreamble()) {
            code.aload(0)
                .getstatic(declaringType.requireField(fieldName))
                .dup_x1()
                .putfield(context.getLocalMarkupClass().requireField(getId()));

            storeLocal(code, type.getTypeDeclaration());
        } else {
            code.getstatic(declaringType.requireField(fieldName));
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
        return declaringType.equals(that.declaringType) &&
            Objects.equals(getId(), that.getId()) &&
            fieldName.equals(that.fieldName) &&
            type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), declaringType, fieldName, type);
    }
}
