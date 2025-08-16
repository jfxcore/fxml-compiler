// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class EmitArrayNode extends AbstractNode implements ValueEmitterNode {

    private final CtClass componentType;
    private final List<? extends ValueEmitterNode> values;
    private ResolvedTypeNode type;

    public EmitArrayNode(TypeInstance arrayType, Collection<? extends ValueEmitterNode> values) {
        super(SourceInfo.span(values));
        this.type = new ResolvedTypeNode(checkNotNull(arrayType), getSourceInfo());
        this.componentType = checkNotNull(arrayType.getComponentType().jvmType());
        this.values = new ArrayList<>(checkNotNull(values));
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        code.newarray(componentType, values.size());

        for (int i = 0; i < values.size(); ++i) {
            code.dup()
                .iconst(i);

            context.emit(values.get(i));

            if (!componentType.isPrimitive()) {
                TypeInstance valueType = TypeHelper.getTypeInstance(values.get(i));

                if (valueType.isPrimitive()) {
                    code.ext_box(valueType.jvmType());
                }
            }

            code.ext_arraystore(componentType);
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        type = (ResolvedTypeNode)type.accept(visitor);
        acceptChildren(values, visitor, ValueEmitterNode.class);
    }

    @Override
    public EmitArrayNode deepClone() {
        return new EmitArrayNode(type.getTypeInstance(), deepClone(values));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitArrayNode other = (EmitArrayNode)o;
        return type.equals(other.type) && componentType.equals(other.componentType) && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, TypeHelper.hashCode(componentType), values);
    }
}
