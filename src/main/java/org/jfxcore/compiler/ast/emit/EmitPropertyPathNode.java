// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents a property assignment of the form 'foo.bar.baz="value"'.
 */
public class EmitPropertyPathNode extends AbstractNode implements ValueEmitterNode {

    private final ResolvedTypeNode type;
    private final List<ValueEmitterNode> propertyGetters;
    private Node property;

    public EmitPropertyPathNode(
            Collection<? extends ValueEmitterNode> propertyGetters,
            Node property,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.propertyGetters = new ArrayList<>(checkNotNull(propertyGetters));
        this.type = (ResolvedTypeNode)this.propertyGetters.get(this.propertyGetters.size() - 1).getType();
        this.property = checkNotNull(property);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        context.getOutput().dup();

        for (EmitterNode child : propertyGetters) {
            context.emit(child);
        }

        context.emit(property);

        context.getOutput().pop();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        property = property.accept(visitor);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public EmitPropertyPathNode deepClone() {
        return new EmitPropertyPathNode(deepClone(propertyGetters), property.deepClone(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitPropertyPathNode that = (EmitPropertyPathNode)o;
        return Objects.equals(propertyGetters, that.propertyGetters) && Objects.equals(property, that.property);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyGetters, property);
    }

}
