// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.CtMethod;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Objects;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Emits opcodes to set a property value.
 */
public class EmitPropertySetterNode extends AbstractNode implements EmitterNode {

    private final PropertyInfo propertyInfo;
    private final boolean content;
    private ValueNode value;

    public EmitPropertySetterNode(PropertyInfo propertyInfo, ValueNode value, boolean content, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.propertyInfo = checkNotNull(propertyInfo);
        this.value = checkNotNull(value);
        this.content = content;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        if (!(value instanceof ValueEmitterNode)) {
            context.emit(value);
        } else if (content) {
            emitAddValues(context);
        } else if (propertyInfo.getSetter() != null) {
            emitInvokeSetter(context);
        } else {
            emitInvokePropertySetter(context);
        }
    }

    private void emitInvokeSetter(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtMethod method = Objects.requireNonNull(propertyInfo.getSetter());
        CtClass valueType = TypeHelper.getJvmType(value);
        CtClass targetType = unchecked(() -> method.getParameterTypes()[0]);

        code.dup();
        context.emit(value);
        code.ext_castconv(getSourceInfo(), valueType, targetType)
            .ext_invoke(method);
    }

    private void emitInvokePropertySetter(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass valueType = TypeHelper.getJvmType(value);
        CtClass targetType = propertyInfo.getType().jvmType();
        CtMethod method = propertyInfo.getSetterOrPropertyGetter();
        CtClass declaringClass = new Resolver(getSourceInfo()).getWritableClass(unchecked(method::getReturnType), false);

        context.emit(value);

        Local valueLocal = code.acquireLocal(valueType);
        code.ext_store(valueType, valueLocal);

        code.dup()
            .ext_invoke(method)
            .ext_load(valueType, valueLocal)
            .ext_castconv(getSourceInfo(), valueType, targetType);

        if (targetType.isPrimitive()) {
            code.invokeinterface(declaringClass, "set", function(voidType, targetType));
        } else {
            code.invokeinterface(declaringClass, "setValue", function(voidType, ObjectType()));
        }

        code.releaseLocal(valueLocal);
    }

    private void emitAddValues(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        context.emit(value);

        Local local = code.acquireLocal(false);
        code.astore(local);

        code.aload(local)
            .ifnonnull(() -> {
                if (propertyInfo.getGetter() != null) {
                    code.dup()
                        .ext_invoke(propertyInfo.getGetter())
                        .aload(local);
                } else {
                    code.dup()
                        .ext_invoke(propertyInfo.getPropertyGetter());

                    if (propertyInfo.getType().subtypeOf(CollectionType()) &&
                            !propertyInfo.getObservableType().subtypeOf(CollectionType())) {
                        code.invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                            .checkcast(CollectionType());
                    }
                    else if (propertyInfo.getType().subtypeOf(MapType()) &&
                                !propertyInfo.getObservableType().subtypeOf(MapType())) {
                        code.invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                            .checkcast(MapType());
                    }

                    code.aload(local);
                }

                if (propertyInfo.getType().subtypeOf(CollectionType())) {
                    code.invokeinterface(CollectionType(), "addAll", function(booleanType, CollectionType()))
                        .pop();
                } else if (propertyInfo.getType().subtypeOf(MapType())) {
                    code.invokeinterface(MapType(), "putAll", function(voidType, MapType()));
                } else {
                    throw new IllegalArgumentException(propertyInfo.getType().toString());
                }
            });
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (ValueNode)value.accept(visitor);
    }

    @Override
    public EmitPropertySetterNode deepClone() {
        return new EmitPropertySetterNode(propertyInfo, value.deepClone(), content, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitPropertySetterNode that = (EmitPropertySetterNode)o;
        return propertyInfo.equals(that.propertyInfo) && value.equals(that.value) && content == that.content;
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyInfo, value, content);
    }
}
