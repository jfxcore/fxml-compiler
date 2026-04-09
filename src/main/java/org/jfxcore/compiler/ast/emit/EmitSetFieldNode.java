// Copyright (c) 2024, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import java.util.Objects;

public class EmitSetFieldNode extends AbstractNode implements ValueEmitterNode {

    private final FieldDeclaration field;
    private final ResolvedTypeNode type;
    private ValueEmitterNode value;


    public EmitSetFieldNode(
            FieldDeclaration field,
            ValueEmitterNode value,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.field = checkNotNull(field);
        this.type = new ResolvedTypeNode(value.getType().getTypeInstance(), sourceInfo);
        this.value = value;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        if (field.isStatic()) {
            value.emit(context);
            code.putstatic(field);
        } else {
            code.aload(0);
            value.emit(context);
            code.putfield(field);
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (ValueEmitterNode)value.accept(visitor);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitSetFieldNode deepClone() {
        return new EmitSetFieldNode(field, value.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitSetFieldNode that = (EmitSetFieldNode)o;
        return field.equals(that.field) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, value);
    }
}
