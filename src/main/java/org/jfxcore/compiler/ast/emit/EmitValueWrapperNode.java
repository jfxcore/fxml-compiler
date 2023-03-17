// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.ValueWrapperGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;

import static org.jfxcore.compiler.util.Classes.ObjectType;
import static org.jfxcore.compiler.util.Descriptors.constructor;

/**
 * Wraps a non-observable value into an instance of {@link javafx.beans.value.ObservableValue}.
 */
public class EmitValueWrapperNode extends AbstractNode implements ValueEmitterNode, GeneratorEmitterNode, NullableInfo {

    private final ClassGenerator generator;
    private final CtClass valueType;
    private EmitterNode child;
    private ResolvedTypeNode type;

    public EmitValueWrapperNode(EmitterNode child) {
        super(child.getSourceInfo());

        Resolver resolver = new Resolver(child.getSourceInfo());
        TypeInstance observableType = resolver.getObservableClass(TypeHelper.getTypeInstance(child));
        CtClass valueType = TypeHelper.getWidenedNumericType(TypeHelper.getJvmType(child));

        this.child = child;
        this.type = new ResolvedTypeNode(observableType, child.getSourceInfo());
        this.valueType = valueType;
        this.generator = ValueWrapperGenerator.newInstance(valueType);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    public CtClass getValueType() {
        return valueType;
    }

    @Override
    public List<ClassGenerator> emitGenerators(BytecodeEmitContext context) {
        return List.of(generator);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        CtClass generatedClass = context.getNestedClasses().find(generator.getClassName());
        CtClass childType = TypeHelper.getJvmType(child);
        boolean generic = !valueType.isPrimitive() && !TypeHelper.isPrimitiveBox(valueType);

        context.emit(child);

        Local local = code.acquireLocal(childType);

        code.ext_store(childType, local)
            .anew(generatedClass)
            .dup()
            .ext_load(childType, local)
            .ext_autoconv(getSourceInfo(), childType, valueType)
            .invokespecial(generatedClass, MethodInfo.nameInit, constructor(generic ? ObjectType() : valueType))
            .releaseLocal(local);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
        child = (EmitterNode)child.accept(visitor);
    }

    @Override
    public EmitValueWrapperNode deepClone() {
        return new EmitValueWrapperNode(child.deepClone());
    }

    @Override
    public boolean isNullable() {
        return false;
    }

}
