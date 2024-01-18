// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class EmitStaticPropertySetterNode extends AbstractNode implements EmitterNode {

    private final TypeInstance declaringType;
    private final PropertyInfo propertyInfo;
    private ValueNode value;

    public EmitStaticPropertySetterNode(
            TypeInstance declaringType, PropertyInfo propertyInfo, ValueNode value, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.declaringType = checkNotNull(declaringType);
        this.propertyInfo = checkNotNull(propertyInfo);
        this.value = checkNotNull(value);

        if (propertyInfo.isReadOnly()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        code.dup();

        if (propertyInfo.getSetter() == null) {
            code.ext_invoke(propertyInfo.getPropertyGetter());
        }

        context.emit(value);

        code.ext_autoconv(getSourceInfo(), TypeHelper.getJvmType(value), propertyInfo.getType().jvmType());

        if (propertyInfo.getSetter() == null) {
            code.invokeinterface(Classes.WritableValueType(), "setValue",
                                 Descriptors.function(CtClass.voidType, Classes.ObjectType()));
        } else {
            code.ext_invoke(propertyInfo.getSetter());
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (ValueNode)value.accept(visitor);
    }

    @Override
    public EmitStaticPropertySetterNode deepClone() {
        return new EmitStaticPropertySetterNode(declaringType, propertyInfo, value.deepClone(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitStaticPropertySetterNode that = (EmitStaticPropertySetterNode)o;
        return declaringType.equals(that.declaringType) &&
            propertyInfo.equals(that.propertyInfo) &&
            value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaringType, propertyInfo.hashCode(), value);
    }

}
