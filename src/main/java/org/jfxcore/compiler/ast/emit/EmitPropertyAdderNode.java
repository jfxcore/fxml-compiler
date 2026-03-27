// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.type.Types.*;

/**
 * Emits opcodes to to add a list of values to a collection-type property or a map property.
 */
public class EmitPropertyAdderNode extends AbstractNode implements EmitterNode {

    private final PropertyInfo propertyInfo;
    private final List<ValueNode> keys;
    private final List<ValueNode> values;
    private final TypeInstance itemType;

    public EmitPropertyAdderNode(
            PropertyInfo propertyInfo,
            Collection<? extends ValueNode> keys,
            Collection<? extends ValueNode> values,
            TypeInstance itemType,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.propertyInfo = checkNotNull(propertyInfo);
        this.keys = new ArrayList<>(checkNotNull(keys));
        this.values = new ArrayList<>(checkNotNull(values));
        this.itemType = checkNotNull(itemType);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        MethodDeclaration method = propertyInfo.getGetterOrPropertyGetter();
        Bytecode code = context.getOutput();
        boolean isMap = !keys.isEmpty();

        code.dup()
            .invoke(method);

        if (propertyInfo.getGetter() == null) {
            code.invoke(ObservableValueDecl().requireDeclaredMethod("getValue"));
        }

        Local local = code.acquireLocal(false);
        code.astore(local);

        for (int i = 0; i < values.size(); ++i) {
            ValueNode value = values.get(i);

            code.aload(local);

            if (isMap) {
                context.emit(keys.get(i));
            }

            context.emit(value);

            if (itemType != null) {
                code.autoconv(TypeHelper.getTypeDeclaration(value), itemType.declaration());
            }

            if (isMap) {
                code.invoke(MapDecl().requireDeclaredMethod("put", ObjectDecl(), ObjectDecl()));
            } else {
                code.invoke(CollectionDecl().requireDeclaredMethod("add", ObjectDecl()));
            }

            code.pop();
        }

        code.releaseLocal(local);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        acceptChildren(values, visitor, ValueNode.class);
    }

    @Override
    public EmitPropertyAdderNode deepClone() {
        return new EmitPropertyAdderNode(
            propertyInfo,
            keys.stream().map(ValueNode::deepClone).collect(Collectors.toList()),
            values.stream().map(ValueNode::deepClone).collect(Collectors.toList()),
            itemType,
            getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitPropertyAdderNode that = (EmitPropertyAdderNode)o;
        return propertyInfo.equals(that.propertyInfo) &&
            keys.equals(that.keys) &&
            values.equals(that.values) &&
            itemType.equals(that.itemType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyInfo, keys, values, itemType);
    }
}
