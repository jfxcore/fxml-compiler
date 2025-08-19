// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.collections.ListObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.MapObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.SetObservableValueWrapperGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * {@link EmitCollectionWrapperNode} wraps an {@code ObservableValue<ObservableList<T>>} into an
 * {@code ObservableList<T>} to support content bindings along observable binding paths (set/map likewise).
 */
public class EmitCollectionWrapperNode extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, NullableInfo {

    private final TypeInstance sourceValueType;
    private final TypeInstance sourceObservableType;
    private final ClassGenerator wrapperGenerator;
    private EmitterNode child;
    private ResolvedTypeNode type;

    public EmitCollectionWrapperNode(
            EmitterNode child,
            TypeInstance valueType,
            @Nullable TypeInstance observableType,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.sourceValueType = checkNotNull(valueType);
        this.sourceObservableType = observableType;
        this.child = checkNotNull(child);

        TypeInstance type;
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());

        if (sourceValueType.subtypeOf(ObservableListType())) {
            type = invoker.invokeType(ObservableListType(), sourceValueType.getArguments());
            wrapperGenerator = new ListObservableValueWrapperGenerator();
        } else if (sourceValueType.subtypeOf(ObservableSetType())) {
            type = invoker.invokeType(ObservableSetType(), sourceValueType.getArguments());
            wrapperGenerator = new SetObservableValueWrapperGenerator();
        } else if (sourceValueType.subtypeOf(ObservableMapType())) {
            type = invoker.invokeType(ObservableMapType(), sourceValueType.getArguments());
            wrapperGenerator = new MapObservableValueWrapperGenerator();
        } else {
            throw new IllegalArgumentException("valueType");
        }

        this.type = new ResolvedTypeNode(type, sourceInfo);
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public ResolvedTypeNode getType() {
        return type;
    }

    @Override
    public List<Generator> emitGenerators(BytecodeEmitContext context) {
        return wrapperGenerator != null ? List.of(wrapperGenerator) : List.of();
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        context.emit(child);

        CtClass generatedClass = context.getNestedClasses().find(wrapperGenerator.getClassName());
        Local local = code.acquireLocal(false);

        code.astore(local)
            .anew(generatedClass)
            .dup()
            .aload(0)
            .aload(local)
            .invokespecial(generatedClass, MethodInfo.nameInit,
                           constructor(context.getMarkupClass(), ObservableValueType()))
            .releaseLocal(local);
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (EmitterNode)child.accept(visitor);
        type = (ResolvedTypeNode)type.accept(visitor);
    }

    @Override
    public EmitCollectionWrapperNode deepClone() {
        return new EmitCollectionWrapperNode(
            child.deepClone(), sourceValueType, sourceObservableType, getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitCollectionWrapperNode that = (EmitCollectionWrapperNode)o;
        return Objects.equals(sourceValueType, that.sourceValueType) &&
            Objects.equals(sourceObservableType, that.sourceObservableType) &&
            Objects.equals(child, that.child) &&
            Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceValueType, sourceObservableType, child, type);
    }
}
