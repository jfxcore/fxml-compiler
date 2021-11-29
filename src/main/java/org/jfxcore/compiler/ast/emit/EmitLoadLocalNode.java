// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

/**
 * Loads a local variable and places it on top of the operand stack.
 */
public class EmitLoadLocalNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    public enum Variable {
        THIS,
        PARAM_1,
        CONTEXT
    }

    private final Variable variable;
    private final ResolvedTypeNode type;

    public EmitLoadLocalNode(Variable variable, TypeInstance type, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.variable = checkNotNull(variable);
        this.type = new ResolvedTypeNode(type, sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public boolean isNullable() {
        return variable != Variable.THIS;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        switch (variable) {
            case THIS -> code.aload(0);
            case PARAM_1 -> code.aload(1);
            case CONTEXT -> code.aload(context.getRuntimeContextLocal());
            default -> throw new IllegalArgumentException();
        }

        code.checkcast(type.getJvmType());
    }

    @Override
    public EmitLoadLocalNode deepClone() {
        return new EmitLoadLocalNode(variable, type.getTypeInstance(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitLoadLocalNode that = (EmitLoadLocalNode)o;
        return variable == that.variable && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable, type);
    }

}
