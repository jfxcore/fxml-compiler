// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import java.util.Objects;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class EmitCollectionAdderNode extends AbstractNode implements EmitterNode {

    private ValueNode value;

    public EmitCollectionAdderNode(ValueNode value) {
        super(value.getSourceInfo());
        this.value = checkNotNull(value);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.getOutput().dup();

        context.emit(value);

        context.getOutput()
            .invokeinterface(CollectionType(), "add", function(booleanType, ObjectType()))
            .pop();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        value = (ValueNode)value.accept(visitor);
    }

    @Override
    public EmitterNode deepClone() {
        return new EmitCollectionAdderNode(value.deepClone());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitCollectionAdderNode adderNode = (EmitCollectionAdderNode)o;
        return value.equals(adderNode.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

}
