// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;

public class EmitUnwrapObservableNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private ValueEmitterNode child;
    private ResolvedTypeNode type;

    public EmitUnwrapObservableNode(ValueEmitterNode child) {
        super(child.getSourceInfo());
        Resolver resolver = new Resolver(child.getSourceInfo());
        this.child = child;
        this.type = new ResolvedTypeNode(
            resolver.findObservableArgument(TypeHelper.getTypeInstance(child)), child.getSourceInfo());
    }

    @Override
    public TypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        context.emit(child);
        code.ext_ObservableUnbox(getSourceInfo(), TypeHelper.getJvmType(child), type.getJvmType());
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
        child = (ValueEmitterNode)child.accept(visitor);
    }

    @Override
    public EmitUnwrapObservableNode deepClone() {
        return new EmitUnwrapObservableNode(child.deepClone());
    }

    @Override
    public boolean isNullable() {
        return !type.getJvmType().isPrimitive();
    }

}
