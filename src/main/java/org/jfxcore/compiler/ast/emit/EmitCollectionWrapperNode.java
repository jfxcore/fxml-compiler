// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javafx.collections.FXCollections;
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
import org.jfxcore.compiler.generate.collections.ListWrapperGenerator;
import org.jfxcore.compiler.generate.collections.MapWrapperGenerator;
import org.jfxcore.compiler.generate.collections.ListObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.MapObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.SetObservableValueWrapperGenerator;
import org.jfxcore.compiler.generate.collections.SetWrapperGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * {@link EmitCollectionWrapperNode} supports certain advanced scenarios for binding to lists, sets and maps.
 * <p>
 * Often, collection-type properties on JavaFX controls are declared as either:
 * <ol>
 *     <li>ListProperty&lt;T> or ObjectProperty&lt;ObservableList&lt;T>>
 *     <li>SetProperty&lt;T> or ObjectProperty&lt;ObservableSet&lt;T>>
 *     <li>MapProperty&lt;K,V> or ObjectProperty&lt;ObservableMap&lt;K,V>>
 * </ol>
 * These properties can only be bound to ObservableList/ObservableSet/ObservableMap instances.
 * <p>
 * Sometimes, a data class may contain List/Set/Map properties that are not observable. If we want to use
 * such properties as binding sources, we need to make them conform to their respective observable interfaces.
 * <p>
 * EmitCollectionWrapperNode does this by wrapping non-observable source lists by using one of the
 * following methods (illustrated for List here, but Set and Map work analogously):
 * <ol>
 *     <li>{@link FXCollections#observableList(List)} if the target property is set only once, i.e. the wrapper will
 *         be set using targetProperty().set(ObservableList) or setTargetProperty(ObservableList)
 *     <li>{@link ListWrapperGenerator} if the target property is bound one-way (i.e. the
 *         wrapper will be set using targetProperty().bind(ObservableValue)) and the source is of type List&lt;T>
 *     <li>{@link ListObservableValueWrapperGenerator} like 2) but the source is of type
 *         ObservableValue&lt;List&lt;T>>
 * </ol>
 */
public class EmitCollectionWrapperNode extends AbstractNode
        implements ValueEmitterNode, GeneratorEmitterNode, NullableInfo {

    private final TypeInstance sourceValueType;
    private final TypeInstance sourceObservableType;
    private final boolean emitObservableValue;
    private final ClassGenerator wrapperGenerator;
    private EmitterNode child;
    private ResolvedTypeNode type;

    public EmitCollectionWrapperNode(
            EmitterNode child,
            TypeInstance valueType,
            @Nullable TypeInstance observableType,
            boolean emitObservableValue,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.sourceValueType = checkNotNull(valueType);
        this.sourceObservableType = observableType;
        this.emitObservableValue = emitObservableValue;
        this.child = checkNotNull(child);

        TypeInstance type;
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());

        if (sourceValueType.subtypeOf(ListType())) {
            type = invoker.invokeType(
                emitObservableValue ? ObservableListValueType() : ObservableListType(), sourceValueType.getArguments());
        } else if (sourceValueType.subtypeOf(SetType())) {
            type = invoker.invokeType(
                emitObservableValue ? ObservableSetValueType() : ObservableSetType(), sourceValueType.getArguments());
        } else if (sourceValueType.subtypeOf(MapType())) {
            type = invoker.invokeType(
                emitObservableValue ? ObservableMapValueType() : ObservableMapType(), sourceValueType.getArguments());
        } else {
            throw new IllegalArgumentException("valueType");
        }

        this.type = new ResolvedTypeNode(type, sourceInfo);

        boolean sourceIsObservableValue =
            sourceObservableType != null &&
            sourceObservableType.subtypeOf(ObservableValueType());

        ClassGenerator generator = null;

        if (emitObservableValue || sourceIsObservableValue) {
            if (sourceValueType.subtypeOf(ListType())) {
                generator = sourceIsObservableValue ?
                    new ListObservableValueWrapperGenerator() : new ListWrapperGenerator();
            } else if (sourceValueType.subtypeOf(SetType())) {
                generator = sourceIsObservableValue ?
                    new SetObservableValueWrapperGenerator() : new SetWrapperGenerator();
            } else if (sourceValueType.subtypeOf(MapType())) {
                generator = sourceIsObservableValue ?
                    new MapObservableValueWrapperGenerator() : new MapWrapperGenerator();
            }
        }

        wrapperGenerator = generator;
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

        if (wrapperGenerator != null) {
            CtClass argType;
            boolean sourceIsObservableValue =
                sourceObservableType != null &&
                sourceObservableType.subtypeOf(ObservableValueType());

            if (sourceValueType.subtypeOf(ListType())) {
                argType = sourceIsObservableValue ? ObservableValueType() : ListType();
            } else if (sourceValueType.subtypeOf(SetType())) {
                argType = sourceIsObservableValue ? ObservableValueType() : SetType();
            } else if (sourceValueType.subtypeOf(MapType())) {
                argType = sourceIsObservableValue ? ObservableValueType() : MapType();
            } else {
                throw new IllegalStateException();
            }

            CtClass generatedClass = context.getNestedClasses().find(wrapperGenerator.getClassName());
            Local local = code.acquireLocal(false);

            code.astore(local)
                .anew(generatedClass)
                .dup()
                .aload(0)
                .aload(local)
                .invokespecial(generatedClass, MethodInfo.nameInit, constructor(context.getMarkupClass(), argType))
                .releaseLocal(local);
        } else {
            if (sourceValueType.subtypeOf(ListType())) {
                code.invokestatic(
                    FXCollectionsType(),
                    "observableList",
                    function(ObservableListType(), ListType()));
            } else if (sourceValueType.subtypeOf(SetType())) {
                code.invokestatic(
                    FXCollectionsType(),
                    "observableSet",
                    function(ObservableSetType(), SetType()));
            } else if (sourceValueType.subtypeOf(MapType())) {
                code.invokestatic(
                    FXCollectionsType(),
                    "observableMap",
                    function(ObservableMapType(), MapType()));
            } else {
                throw new IllegalArgumentException();
            }
        }
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
            child.deepClone(), sourceValueType, sourceObservableType, emitObservableValue, getSourceInfo());
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
            emitObservableValue == that.emitObservableValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceValueType, sourceObservableType, emitObservableValue, child, type);
    }

}
