// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.BooleanMapperGenerator;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.type.Types.*;
import static org.jfxcore.compiler.type.Types.Markup.Runtime.*;

/**
 * Emits its child node, which places an {@link javafx.beans.value.ObservableValue} on top of the operand stack.
 * Then maps the observable value to a boolean value as specified by {@link Operator}.
 */
public class EmitMapToBooleanNode extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, NullableInfo {

    private final ResolvedTypeNode type;
    private final ClassGenerator generator;
    private final boolean invert;
    private ValueEmitterNode child;

    public EmitMapToBooleanNode(ValueEmitterNode child, boolean invert, SourceInfo sourceInfo) {
        super(sourceInfo);

        if (CompilationContext.getCurrent().useSharedImplementation()) {
            this.generator = null;
        } else {
            Resolver resolver = new Resolver(sourceInfo);
            TypeInstance typeInstance = child.getType().getTypeInstance();
            TypeDeclaration valueType = resolver.findObservableArgument(typeInstance).boxed().declaration();
            this.generator = new BooleanMapperGenerator(valueType, invert);
        }

        this.child = checkNotNull(child);
        this.invert = invert;
        this.type = new ResolvedTypeNode(
            new TypeInvoker(sourceInfo).invokeType(ObservableValueDecl(), List.of(TypeInstance.BooleanType())),
            sourceInfo);
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public List<? extends Generator> emitGenerators(BytecodeEmitContext context) {
        return generator != null ? List.of(generator) : List.of();
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        if (generator != null) {
            emitLocalImpl(context);
        } else {
            emitSharedImpl(context);
        }
    }

    private void emitLocalImpl(BytecodeEmitContext context) {
        TypeDeclaration generatedClass = context.getNestedClasses().find(generator.getClassName());
        Bytecode code = context.getOutput();

        code.anew(generatedClass)
            .dup();

        context.emit(child);

        code.invoke(generatedClass.requireConstructor(ObservableValueDecl()));
    }

    private void emitSharedImpl(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        String name = invert ? "isZero" : "isNotZero";
        TypeInstance childType = child.getType().getTypeInstance();

        context.emit(child);

        if (TypeInstance.of(ObservableDoubleValueDecl()).isAssignableFrom(childType)) {
            code.invoke(BooleanBindingsDecl().requireDeclaredMethod(name, ObservableDoubleValueDecl()));
        } else if (TypeInstance.of(ObservableIntegerValueDecl()).isAssignableFrom(childType)) {
            code.invoke(BooleanBindingsDecl().requireDeclaredMethod(name, ObservableIntegerValueDecl()));
        } else if (TypeInstance.of(ObservableFloatValueDecl()).isAssignableFrom(childType)) {
            code.invoke(BooleanBindingsDecl().requireDeclaredMethod(name, ObservableFloatValueDecl()));
        } else if (TypeInstance.of(ObservableLongValueDecl()).isAssignableFrom(childType)) {
            code.invoke(BooleanBindingsDecl().requireDeclaredMethod(name, ObservableLongValueDecl()));
        } else {
            Resolver resolver = new Resolver(getSourceInfo());
            TypeInstance argType = resolver.findObservableArgument(childType).boxed();

            if (argType.subtypeOf(NumberDecl())) {
                code.invoke(BooleanBindingsDecl().requireDeclaredMethod(name, ObservableValueDecl()));
            } else if (argType.subtypeOf(BooleanDecl())) {
                code.invoke(BooleanBindingsDecl().requireDeclaredMethod("isNot", ObservableValueDecl()));
            } else {
                code.invoke(BooleanBindingsDecl().requireDeclaredMethod(
                    invert ? "isNull" : "isNotNull", ObservableValueDecl()));
            }
        }
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (ValueEmitterNode)child.accept(visitor);
    }

    @Override
    public EmitMapToBooleanNode deepClone() {
        return new EmitMapToBooleanNode(child.deepClone(), invert, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitMapToBooleanNode that = (EmitMapToBooleanNode)o;
        return invert == that.invert && type.equals(that.type) && child.equals(that.child);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invert, type, child);
    }
}
