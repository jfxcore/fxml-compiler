// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.ValueWrapperGenerator;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import java.util.List;

import static org.jfxcore.compiler.type.TypeSymbols.*;

/**
 * Wraps a non-observable value into an instance of {@link javafx.beans.value.ObservableValue}.
 */
public class EmitValueWrapperNode extends AbstractNode implements ValueEmitterNode, GeneratorEmitterNode, NullableInfo {

    private final ClassGenerator generator;
    private final TypeInstance valueType;
    private EmitterNode child;
    private ResolvedTypeNode type;

    public EmitValueWrapperNode(EmitterNode child) {
        super(child.getSourceInfo());

        Resolver resolver = new Resolver(child.getSourceInfo());
        TypeInstance observableType = resolver.getObservableClass(TypeHelper.getTypeInstance(child));
        TypeInstance valueType = widenShortInt(TypeHelper.getTypeInstance(child));

        this.child = child;
        this.type = new ResolvedTypeNode(observableType, child.getSourceInfo());
        this.valueType = valueType;
        this.generator = ValueWrapperGenerator.newInstance(valueType.declaration());
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    public TypeInstance getValueType() {
        return valueType;
    }

    @Override
    public List<ClassGenerator> emitGenerators(BytecodeEmitContext context) {
        return List.of(generator);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        TypeDeclaration generatedClass = context.getNestedClasses().find(generator.getClassName());
        TypeDeclaration childType = TypeHelper.getTypeDeclaration(child);
        boolean generic = !valueType.isPrimitive() && !valueType.declaration().isPrimitiveBox();

        context.emit(child);

        Local local = code.acquireLocal(childType);

        code.store(childType, local)
            .anew(generatedClass)
            .dup()
            .load(childType, local)
            .autoconv(childType, valueType.declaration())
            .invoke(generatedClass.requireConstructor(generic ? ObjectDecl() : valueType.declaration()))
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

    private static TypeInstance widenShortInt(TypeInstance type) {
        return switch (type.name()) {
            case "short", "byte", "char" -> TypeInstance.intType();
            case ShortName, ByteName, CharacterName -> TypeInstance.IntegerType();
            default -> type;
        };
    }
}
