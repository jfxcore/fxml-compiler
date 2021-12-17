// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.TypeInstance;

import java.util.Objects;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Emits code to establish a binding between the value that is currently on top of the
 * operand stack and the provided child value.
 */
public class EmitPropertyBindingNode extends AbstractNode implements EmitterNode {

    private final PropertyInfo propertyInfo;
    private final BindingMode bindingMode;
    private ValueNode child;

    public EmitPropertyBindingNode(
            PropertyInfo propertyInfo, ValueNode child, BindingMode bindingMode, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.propertyInfo = checkNotNull(propertyInfo);
        this.child = checkNotNull(child);
        this.bindingMode = bindingMode;
    }

    public boolean isBidirectional() {
        return bindingMode.isBidirectional();
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        context.emit(child);

        Local local = code.acquireLocal(false);
        code.astore(local);

        if (!(child instanceof NullableInfo) || ((NullableInfo)child).isNullable()) {
            code.aload(local)
                .ifnonnull(() -> emitBinding(code, local));
        } else {
            emitBinding(code, local);
        }

        code.releaseLocal(local);
    }

    private void emitBinding(Bytecode code, Local local) {
        if (bindingMode.isBidirectional()) {
            emitBindBidirectional(code, local);
        } else if (bindingMode.isUnidirectional()) {
            emitBindUnidirectional(code, local);
        }
    }

    private void emitBindBidirectional(Bytecode code, Local local) {
        code.dup()
            .ext_invoke(checkNotNull(propertyInfo.getPropertyGetter()))
            .aload(local);

        if (child.getNodeData(NodeDataKey.BIND_BIDIRECTIONAL_NEGATED) == Boolean.TRUE) {
            throw GeneralErrors.unsupported("Negated bidirectional bindings are not supported.");
        } else if (bindingMode.isContent()) {
            emitBindContent(code, true);
        } else {
            code.invokeinterface(PropertyType(), "bindBidirectional", function(voidType, PropertyType()));
        }
    }

    private void emitBindUnidirectional(Bytecode code, Local local) {
        if (bindingMode.isContent()) {
            code.dup()
                .ext_invoke(checkNotNull(propertyInfo.getPropertyGetterOrGetter()))
                .aload(local);

            emitBindContent(code, false);
        } else {
            code.dup()
                .ext_invoke(checkNotNull(propertyInfo.getPropertyGetter()))
                .aload(local)
                .invokeinterface(PropertyType(), "bind", function(voidType, ObservableValueType()));
        }
    }

    private void emitBindContent(Bytecode code, boolean bidirectional) {
        if (!tryEmitBindContentImpl(code, ListType(), ObservableListType(), ReadOnlyListPropertyType(), bidirectional) &&
                !tryEmitBindContentImpl(code, SetType(), ObservableSetType(), ReadOnlySetPropertyType(), bidirectional) &&
                !tryEmitBindContentImpl(code, MapType(), ObservableMapType(), ReadOnlyMapPropertyType(), bidirectional)) {
            throw new IllegalArgumentException(propertyInfo.getValueTypeInstance().toString());
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean tryEmitBindContentImpl(
            Bytecode code,
            CtClass collectionType,
            CtClass observableCollectionType,
            CtClass collectionPropertyType,
            boolean bidirectional) {
        if (propertyInfo.getValueTypeInstance().subtypeOf(collectionType)) {
            String methodName = bidirectional ? "bindContentBidirectional" : "bindContent";
            TypeInstance observableType = propertyInfo.getObservableTypeInstance();

            if (observableType != null && observableType.subtypeOf(collectionPropertyType)) {
                code.invokevirtual(collectionPropertyType, methodName, function(voidType, observableCollectionType));
            } else if (bidirectional) {
                code.invokestatic(BindingsType(), methodName,
                                  function(voidType, observableCollectionType, observableCollectionType));
            } else {
                code.invokestatic(BindingsType(), methodName,
                                  function(voidType, collectionType, observableCollectionType));
            }

            return true;
        }

        return false;
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (ValueNode)child.accept(visitor);
    }

    @Override
    public EmitPropertyBindingNode deepClone() {
        return new EmitPropertyBindingNode(propertyInfo, child, bindingMode, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitPropertyBindingNode that = (EmitPropertyBindingNode)o;
        return bindingMode == that.bindingMode &&
            propertyInfo.equals(that.propertyInfo) &&
            child.equals(that.child);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyInfo, bindingMode, child);
    }

}
