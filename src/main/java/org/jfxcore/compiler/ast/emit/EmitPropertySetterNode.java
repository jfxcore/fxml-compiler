// Copyright (c) 2021, JFXcore. All rights reserved.
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
        if (content) {
            emitAddValues(context);
        } else {
            emitSetValue(context);
        }
    }

    private void emitSetValue(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass valueType = TypeHelper.getJvmType(value);
        CtMethod method = propertyInfo.getSetterOrPropertyGetter();

        code.dup();

        if (propertyInfo.getSetter() == null) {
            code.ext_invoke(method);
        }

        context.emit(value);

        if (propertyInfo.getSetter() != null) {
            code.ext_autoconv(getSourceInfo(), valueType, unchecked(() -> method.getParameterTypes()[0]))
                .ext_invoke(method);
        } else {
            switch (valueType.getName()) {
                case "boolean" ->
                    code.ext_autoconv(getSourceInfo(), valueType, booleanType)
                        .invokeinterface(WritableBooleanValueType(), "set", function(voidType, booleanType));
                case "int" ->
                    code.ext_autoconv(getSourceInfo(), valueType, intType)
                        .invokeinterface(WritableIntegerValueType(), "set", function(voidType, intType));
                case "long" ->
                    code.ext_autoconv(getSourceInfo(), valueType, longType)
                        .invokeinterface(WritableLongValueType(), "set", function(voidType, longType));
                case "float" ->
                    code.ext_autoconv(getSourceInfo(), valueType, floatType)
                        .invokeinterface(WritableFloatValueType(), "set", function(voidType, floatType));
                case "double" ->
                    code.ext_autoconv(getSourceInfo(), valueType, doubleType)
                        .invokeinterface(WritableDoubleValueType(), "set", function(voidType, doubleType));
                default ->
                    code.invokeinterface(WritableValueType(), "setValue", function(voidType, ObjectType()));
            }
        }
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

                    if (propertyInfo.getValueTypeInstance().subtypeOf(CollectionType()) &&
                            !propertyInfo.getObservableTypeInstance().subtypeOf(CollectionType())) {
                        code.invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                            .checkcast(CollectionType());
                    }
                    else if (propertyInfo.getValueTypeInstance().subtypeOf(MapType()) &&
                                !propertyInfo.getObservableTypeInstance().subtypeOf(MapType())) {
                        code.invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                            .checkcast(MapType());
                    }

                    code.aload(local);
                }

                if (propertyInfo.getValueTypeInstance().subtypeOf(CollectionType())) {
                    code.invokeinterface(CollectionType(), "addAll", function(booleanType, CollectionType()))
                        .pop();
                } else if (propertyInfo.getValueTypeInstance().subtypeOf(MapType())) {
                    code.invokeinterface(MapType(), "putAll", function(voidType, MapType()));
                } else {
                    throw new IllegalArgumentException(propertyInfo.getValueTypeInstance().toString());
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
