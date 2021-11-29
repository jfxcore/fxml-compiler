// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class EmitMapAdderNode extends AbstractNode implements EmitterNode {

    private ValueNode key;
    private ValueNode value;

    public EmitMapAdderNode(ValueNode key, ValueNode value) {
        super(value.getSourceInfo());
        this.key = checkNotNull(key);
        this.value = checkNotNull(value);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.getOutput().dup();

        context.emit(key);
        context.emit(value);

        context.getOutput()
            .invokeinterface(MapType(), "put", function(ObjectType(), ObjectType(), ObjectType()))
            .pop();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        key = (ValueNode)key.accept(visitor);
        value = (ValueNode)value.accept(visitor);
    }

    @Override
    public EmitterNode deepClone() {
        return new EmitMapAdderNode(key.deepClone(), value.deepClone());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitMapAdderNode that = (EmitMapAdderNode)o;
        return key.equals(that.key) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

}
