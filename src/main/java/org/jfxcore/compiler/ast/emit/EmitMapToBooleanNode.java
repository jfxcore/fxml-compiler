// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.generate.BooleanMapperGenerator;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

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
            CtClass valueType = TypeHelper.getBoxedType(resolver.findObservableArgument(typeInstance).jvmType());
            this.generator = new BooleanMapperGenerator(valueType, invert);
        }

        this.child = checkNotNull(child);
        this.invert = invert;
        this.type = new ResolvedTypeNode(
            new TypeInvoker(sourceInfo).invokeType(ObservableValueType(), List.of(TypeInstance.BooleanType())),
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
        CtClass generatedClass = context.getNestedClasses().find(generator.getClassName());
        Bytecode code = context.getOutput();

        code.anew(generatedClass)
            .dup();

        context.emit(child);

        code.invokespecial(generatedClass, MethodInfo.nameInit, constructor(ObservableValueType()));
    }

    private void emitSharedImpl(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        String name = invert ? "isZero" : "isNotZero";
        TypeInstance childType = child.getType().getTypeInstance();

        context.emit(child);

        if (TypeInstance.of(ObservableDoubleValueType()).isAssignableFrom(childType)) {
            code.invokestatic(Markup.Runtime.BooleanBindingsType(), name,
                              function(BooleanBindingType(), ObservableDoubleValueType()));
        } else if (TypeInstance.of(ObservableIntegerValueType()).isAssignableFrom(childType)) {
            code.invokestatic(Markup.Runtime.BooleanBindingsType(), name,
                              function(BooleanBindingType(), ObservableIntegerValueType()));
        } else if (TypeInstance.of(ObservableFloatValueType()).isAssignableFrom(childType)) {
            code.invokestatic(Markup.Runtime.BooleanBindingsType(), name,
                              function(BooleanBindingType(), ObservableFloatValueType()));
        } else if (TypeInstance.of(ObservableLongValueType()).isAssignableFrom(childType)) {
            code.invokestatic(Markup.Runtime.BooleanBindingsType(), name,
                              function(BooleanBindingType(), ObservableLongValueType()));
        } else {
            Resolver resolver = new Resolver(getSourceInfo());
            TypeInstance argType = resolver.findObservableArgument(childType).boxed();

            if (argType.subtypeOf(NumberType())) {
                code.invokestatic(Markup.Runtime.BooleanBindingsType(), name,
                                  function(BooleanBindingType(), ObservableValueType()));
            } else if (argType.subtypeOf(BooleanType())) {
                code.invokestatic(Markup.Runtime.BooleanBindingsType(), "isNot",
                                  function(BooleanBindingType(), ObservableValueType()));
            } else {
                code.invokestatic(Markup.Runtime.BooleanBindingsType(), invert ? "isNull" : "isNotNull",
                                  function(BooleanBindingType(), ObservableValueType()));
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
