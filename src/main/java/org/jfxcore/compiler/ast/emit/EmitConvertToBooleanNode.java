// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.BindingOperator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.BooleanConversionHelper;
import java.util.Objects;

/**
 * Emits the child value, then replaces it with {@code true} or {@code false} as specified by {@link BindingOperator}.
 */
public class EmitConvertToBooleanNode extends AbstractNode implements ValueEmitterNode {

    private final BindingOperator operator;
    private final ResolvedTypeNode type;
    private EmitterNode child;

    public EmitConvertToBooleanNode(EmitterNode child, BindingOperator operator, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.child = checkNotNull(child);
        this.operator = checkNotNull(operator);
        this.type = new ResolvedTypeNode(TypeInstance.booleanType(), sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.emit(child);

        BooleanConversionHelper.emit(
            context.getOutput(),
            TypeHelper.getTypeDeclaration(child),
            operator == BindingOperator.NOT);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (EmitterNode)child.accept(visitor);
    }

    @Override
    public EmitConvertToBooleanNode deepClone() {
        return new EmitConvertToBooleanNode(child.deepClone(), operator, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitConvertToBooleanNode that = (EmitConvertToBooleanNode)o;
        return operator == that.operator &&
            type.equals(that.type) &&
            child.equals(that.child);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, type, child);
    }
}
