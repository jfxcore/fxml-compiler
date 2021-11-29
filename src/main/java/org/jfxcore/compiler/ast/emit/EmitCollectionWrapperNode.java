// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javafx.beans.value.ObservableValue;
import javafx.beans.value.ObservableListValue;
import javafx.collections.FXCollections;
import javassist.CtClass;
import javassist.NotFoundException;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.function;

/**
 * {@link EmitCollectionWrapperNode} supports certain advanced scenarios for binding to lists, sets and maps.
 *
 * Often, collection-type properties on JavaFX controls are declared as either:
 * <ol>
 *     <li>ListProperty&lt;T> or ObjectProperty&lt;ObservableList&lt;T>>
 *     <li>SetProperty&lt;T> or ObjectProperty&lt;ObservableSet&lt;T>>
 *     <li>MapProperty&lt;K,V> or ObjectProperty&lt;ObservableMap&lt;K,V>>
 * </ol>
 *
 * These properties can only be bound to ObservableList/ObservableSet/ObservableMap instances.
 *
 * Sometimes, a data class may contain List/Set/Map properties that are not observable. If we want to use
 * such properties as binding sources, we need to make them conform to their respective observable interfaces.
 *
 * EmitCollectionWrapperNode does this by wrapping non-observable source lists by using one of the
 * following methods (illustrated for List here, but Set and Map work analogously):
 * <ol>
 *     <li>{@link FXCollections#observableList(List)} if the target property is set only once, i.e. the wrapper will
 *         be set using targetProperty().set(ObservableList) or setTargetProperty(ObservableList)
 *     <li>{@link ObservableListValue#observableListValue(List)} if the target property is bound one-way (i.e. the
 *         wrapper will be set using targetProperty().bind(ObservableValue)) and the source is of type List&lt;T>
 *     <li>{@link ObservableListValue#observableListValue(ObservableValue)} like 2) but the source is of type
 *         ObservableValue&lt;List&lt;T>>
 * </ol>
 */
public class EmitCollectionWrapperNode extends AbstractNode implements ValueEmitterNode, NullableInfo {

    private final TypeInstance sourceValueType;
    private final TypeInstance sourceObservableType;
    private final boolean emitObservableValue;
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
        Resolver resolver = new Resolver(SourceInfo.none());

        if (sourceValueType.subtypeOf(ListType())) {
            type = resolver.getTypeInstance(
                emitObservableValue ? ObservableListValueType() : ObservableListType(), sourceValueType.getArguments());
        } else if (sourceValueType.subtypeOf(SetType())) {
            type = resolver.getTypeInstance(
                emitObservableValue ? ObservableSetValueType() : ObservableSetType(), sourceValueType.getArguments());
        } else if (sourceValueType.subtypeOf(MapType())) {
            type = resolver.getTypeInstance(
                emitObservableValue ? ObservableMapValueType() : ObservableMapType(), sourceValueType.getArguments());
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
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        context.emit(child);

        boolean sourceIsObservableValue =
            sourceObservableType != null &&
            sourceObservableType.subtypeOf(ObservableValueType());

        if (emitObservableValue || sourceIsObservableValue) {
            if (sourceValueType.subtypeOf(ListType())) {
                invokestaticSafe(code,
                    ObservableListValueType(),
                    "observableListValue",
                    function(ObservableListValueType(), sourceIsObservableValue ? ObservableValueType() : ListType()));
            } else if (sourceValueType.subtypeOf(SetType())) {
                invokestaticSafe(code,
                    ObservableSetValueType(),
                    "observableSetValue",
                    function(ObservableSetValueType(), sourceIsObservableValue ? ObservableValueType() : SetType()));
            } else if (sourceValueType.subtypeOf(MapType())) {
                invokestaticSafe(code,
                    ObservableMapValueType(),
                    "observableMapValue",
                    function(ObservableMapValueType(), sourceIsObservableValue ? ObservableValueType() : MapType()));
            } else {
                throw new IllegalArgumentException();
            }
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

    /**
     * Check whether the method actually exists, as it is exclusive to the JFXcore runtime
     */
    private void invokestaticSafe(Bytecode code, CtClass type, String name, String desc) {
        try {
            type.getMethod(name, desc);
        } catch (NotFoundException ex) {
            throw BindingSourceErrors.bindingNotSupported(getSourceInfo());
        }

        code.invokestatic(type, name, desc);
    }

}
