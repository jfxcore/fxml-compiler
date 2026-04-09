// Copyright (c) 2024, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import java.util.Objects;

public class ContextNode extends AbstractNode implements ValueNode {

    private final FieldDeclaration field;
    private final ResolvedTypeNode typeNode;
    private final TypeInstance type;
    private final TypeInstance valueType;
    private final TypeInstance observableType;
    private ValueNode value;

    public ContextNode(
            FieldDeclaration field,
            TypeInstance type,
            TypeInstance valueType,
            @Nullable TypeInstance observableType,
            ValueNode value,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.field = checkNotNull(field);
        this.type = checkNotNull(type);
        this.valueType = checkNotNull(valueType);
        this.observableType = observableType;
        this.value = checkNotNull(value);
        this.typeNode = new ResolvedTypeNode(type, sourceInfo);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (ValueNode)value.accept(visitor);
    }

    @Override
    public ResolvedTypeNode getType() {
        return typeNode;
    }

    public TypeInstance getValueType() {
        return valueType;
    }

    public @Nullable TypeInstance getObservableType() {
        return observableType;
    }

    public FieldDeclaration getField() {
        return field;
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public ContextNode deepClone() {
        return new ContextNode(field, type, valueType, observableType, value.deepClone(), getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextNode that = (ContextNode)o;
        return field.equals(that.field)
            && value.equals(that.value)
            && valueType.equals(that.valueType)
            && Objects.equals(observableType, that.observableType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, value, valueType, observableType);
    }
}
