// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtMethod;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Objects;

public class EmitStaticPropertySetterNode extends AbstractNode implements EmitterNode {

    private final TypeInstance declaringType;
    private final CtMethod setter;
    private ValueNode value;

    public EmitStaticPropertySetterNode(
            TypeInstance declaringType, CtMethod setter, ValueNode value, SourceInfo sourceInfo) {
        super(sourceInfo);
        this.declaringType = checkNotNull(declaringType);
        this.setter = checkNotNull(setter);
        this.value = checkNotNull(value);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        code.dup();
        context.emit(value);
        code.ext_autoconv(getSourceInfo(), TypeHelper.getJvmType(value), unchecked(() -> setter.getParameterTypes()[1]))
            .invokestatic(declaringType.getName(), setter.getName(), setter.getSignature());
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        value = (ValueNode)value.accept(visitor);
    }

    @Override
    public EmitStaticPropertySetterNode deepClone() {
        return new EmitStaticPropertySetterNode(declaringType, setter, value.deepClone(), getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitStaticPropertySetterNode that = (EmitStaticPropertySetterNode)o;
        return declaringType.equals(that.declaringType) &&
            TypeHelper.equals(setter, that.setter) &&
            value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaringType, TypeHelper.hashCode(setter), value);
    }

}
