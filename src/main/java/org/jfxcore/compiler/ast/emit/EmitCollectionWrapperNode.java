// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.collections.ListObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.ListReseatableSourceWrapperGenerator;
import org.jfxcore.compiler.generate.collections.MapObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.MapReseatableSourceWrapperGenerator;
import org.jfxcore.compiler.generate.collections.SetObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.SetReseatableSourceWrapperGenerator;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * {@link EmitCollectionWrapperNode} wraps an {@code ObservableValue<{Observable}List<T>>} into an
 * {@code ObservableList<T>} to support content bindings along observable binding paths (set/map likewise).
 */
public class EmitCollectionWrapperNode extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, NullableInfo {

    private final TypeInstance sourceValueType;
    private final TypeInstance sourceObservableType;
    private final ClassGenerator wrapperGenerator;
    private final boolean reverseWrapper;
    private EmitterNode child;
    private ResolvedTypeNode type;

    public EmitCollectionWrapperNode(
            EmitterNode child,
            TypeInstance valueType,
            @Nullable TypeInstance observableType,
            boolean reverseWrapper,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.sourceValueType = checkNotNull(valueType);
        this.sourceObservableType = observableType;
        this.reverseWrapper = reverseWrapper;
        this.child = checkNotNull(child);

        TypeInstance type;
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());

        if (sourceValueType.subtypeOf(ListDecl())) {
            type = invoker.invokeType(ObservableListDecl(), sourceValueType.arguments());
            wrapperGenerator = reverseWrapper
                ? new ListReseatableSourceWrapperGenerator()
                : new ListObservableValueWrapperGenerator();
        } else if (sourceValueType.subtypeOf(SetDecl())) {
            type = invoker.invokeType(ObservableSetDecl(), sourceValueType.arguments());
            wrapperGenerator = reverseWrapper
                ? new SetReseatableSourceWrapperGenerator()
                : new SetObservableValueWrapperGenerator();
        } else if (sourceValueType.subtypeOf(MapDecl())) {
            type = invoker.invokeType(ObservableMapDecl(), sourceValueType.arguments());
            wrapperGenerator = reverseWrapper
                ? new MapReseatableSourceWrapperGenerator()
                : new MapObservableValueWrapperGenerator();
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
        return List.of(wrapperGenerator);
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        if (reverseWrapper) {
            emitReverseWrapper(context);
        } else {
            emitForwardWrapper(context);
        }
    }

    private void emitForwardWrapper(BytecodeEmitContext context) {
        TypeDeclaration generatedClass = context.getNestedClasses().find(wrapperGenerator.getClassName());
        Bytecode code = context.getOutput();
        Local source = code.acquireLocal(false);

        context.emit(child);

        code.astore(source)
            .anew(generatedClass)
            .dup()
            .aload(0)
            .aload(source)
            .invoke(generatedClass.requireConstructor(context.getMarkupClass(), ObservableValueDecl()))
            .releaseLocal(source);
    }

    private void emitReverseWrapper(BytecodeEmitContext context) {
        TypeDeclaration generatedClass = context.getNestedClasses().find(wrapperGenerator.getClassName());
        Bytecode code = context.getOutput();
        Local target = code.acquireLocal(false);
        Local source = code.acquireLocal(false);

        code.dup()
            .astore(target);

        context.emit(child);

        code.astore(source)
            .anew(generatedClass)
            .dup()
            .aload(target)
            .aload(source)
            .invoke(generatedClass.requireConstructor(type.getTypeDeclaration(), ObservableValueDecl()));

        code.releaseLocal(target);
        code.releaseLocal(source);
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
            child.deepClone(), sourceValueType, sourceObservableType, reverseWrapper, getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitCollectionWrapperNode that = (EmitCollectionWrapperNode)o;
        return Objects.equals(sourceValueType, that.sourceValueType) &&
            Objects.equals(sourceObservableType, that.sourceObservableType) &&
            Objects.equals(child, that.child) &&
            Objects.equals(type, that.type) &&
            reverseWrapper == that.reverseWrapper;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceValueType, sourceObservableType, reverseWrapper, child, type);
    }
}
