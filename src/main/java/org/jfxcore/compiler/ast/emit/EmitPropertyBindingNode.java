// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.InvertBooleanBindingGenerator;
import org.jfxcore.compiler.generate.ReferenceTrackerGenerator;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;
import java.util.Objects;

import static javassist.CtClass.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

/**
 * Emits code to establish a binding between the value that is currently on top of the
 * operand stack and the provided child value.
 */
public class EmitPropertyBindingNode extends AbstractNode implements EmitterNode, GeneratorEmitterNode {

    private final PropertyInfo propertyInfo;
    private final BindingMode bindingMode;
    private ValueEmitterNode child;
    private ValueEmitterNode converter;
    private ValueEmitterNode format;

    public EmitPropertyBindingNode(
            PropertyInfo propertyInfo,
            BindingMode bindingMode,
            ValueEmitterNode child,
            @Nullable ValueEmitterNode converter,
            @Nullable ValueEmitterNode format,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.propertyInfo = checkNotNull(propertyInfo);
        this.bindingMode = bindingMode;
        this.child = checkNotNull(child);
        this.converter = converter;
        this.format = format;

        if (bindingMode != BindingMode.BIDIRECTIONAL && (converter != null || format != null)) {
            throw new IllegalArgumentException();
        }

        if (converter != null && format != null) {
            throw new IllegalArgumentException();
        }
    }

    public boolean isBidirectional() {
        return bindingMode.isBidirectional();
    }

    @Override
    public List<? extends Generator> emitGenerators(BytecodeEmitContext context) {
        return child.getNodeData(NodeDataKey.BIND_BIDIRECTIONAL_INVERT_BOOLEAN) == Boolean.TRUE
            ? List.of(new InvertBooleanBindingGenerator())
            : child instanceof EmitCollectionWrapperNode && bindingMode.isContent()
                ? List.of(new ReferenceTrackerGenerator())
                : List.of();
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        context.emit(child);

        Local local = code.acquireLocal(false);
        code.astore(local);

        if (NullableInfo.isNullable(child, true)) {
            code.aload(local)
                .ifnonnull(() -> emitBinding(context, local));
        } else {
            emitBinding(context, local);
        }

        code.releaseLocal(local);
    }

    private void emitBinding(BytecodeEmitContext context, Local local) {
        if (bindingMode.isBidirectional()) {
            Local param2 = null;

            if (converter != null) {
                param2 = context.getOutput().acquireLocal(false);
                converter.emit(context);
                context.getOutput().astore(param2);
            } else if (format != null) {
                param2 = context.getOutput().acquireLocal(false);
                format.emit(context);
                context.getOutput().astore(param2);
            }

            emitBindBidirectional(context, local, param2);

            if (param2 != null) {
                context.getOutput().releaseLocal(param2);
            }
        } else if (bindingMode.isUnidirectional()) {
            emitBindUnidirectional(context, local);
        }
    }

    private void emitBindBidirectional(BytecodeEmitContext context, Local param1, @Nullable Local param2) {
        Bytecode code = context.getOutput();

        code.dup()
            .ext_invoke(checkNotNull(propertyInfo.getPropertyGetter()))
            .aload(param1);

        if (param2 != null) {
            code.aload(param2);
        }

        if (child.getNodeData(NodeDataKey.BIND_BIDIRECTIONAL_INVERT_BOOLEAN) == Boolean.TRUE) {
            code.invokestatic(context.getNestedClasses().find(InvertBooleanBindingGenerator.CLASS_NAME),
                              "bindBidirectional", function(voidType, PropertyType(), PropertyType()));
        } else if (bindingMode.isContent()) {
            emitBindContent(context, true);
        } else {
            if (converter != null) {
                code.invokevirtual(StringPropertyType(), "bindBidirectional",
                                   function(voidType, PropertyType(), StringConverterType()));
            } else if (format != null) {
                code.invokevirtual(StringPropertyType(), "bindBidirectional",
                                   function(voidType, PropertyType(), FormatType()));
            } else {
                code.invokeinterface(PropertyType(), "bindBidirectional", function(voidType, PropertyType()));
            }
        }
    }

    private void emitBindUnidirectional(BytecodeEmitContext context, Local local) {
        Bytecode code = context.getOutput();

        if (bindingMode.isContent()) {
            code.acquireLocal(false);

            code.dup()
                .ext_invoke(checkNotNull(propertyInfo.getPropertyGetterOrGetter()))
                .aload(local);

            emitBindContent(context, false);
        } else {
            code.dup()
                .ext_invoke(checkNotNull(propertyInfo.getPropertyGetter()))
                .aload(local)
                .invokeinterface(PropertyType(), "bind", function(voidType, ObservableValueType()));
        }
    }

    private void emitBindContent(BytecodeEmitContext context, boolean bidirectional) {
        if (!tryEmitBindContentImpl(context, ListType(), ObservableListType(), ReadOnlyListPropertyType(), bidirectional) &&
                !tryEmitBindContentImpl(context, SetType(), ObservableSetType(), ReadOnlySetPropertyType(), bidirectional) &&
                !tryEmitBindContentImpl(context, MapType(), ObservableMapType(), ReadOnlyMapPropertyType(), bidirectional)) {
            throw new IllegalArgumentException(propertyInfo.getType().toString());
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean tryEmitBindContentImpl(
            BytecodeEmitContext context,
            CtClass collectionType,
            CtClass observableCollectionType,
            CtClass collectionPropertyType,
            boolean bidirectional) {
        if (propertyInfo.getType().subtypeOf(collectionType)) {
            TypeInstance observableType = propertyInfo.getObservableType();
            Bytecode code = context.getOutput();

            Local targetLocal = code.acquireLocal(false);
            Local sourceLocal = code.acquireLocal(false);

            code.astore(sourceLocal)
                .astore(targetLocal)
                .aload(targetLocal)
                .aload(sourceLocal);

            if (observableType != null && observableType.subtypeOf(collectionPropertyType)) {
                String methodName = bidirectional ? "bindContentBidirectional" : "bindContent";
                code.invokevirtual(collectionPropertyType, methodName, function(voidType, observableCollectionType));
            } else if (bidirectional) {
                code.invokestatic(BindingsType(), "bindContentBidirectional",
                                  function(voidType, observableCollectionType, observableCollectionType));
            } else {
                code.invokestatic(BindingsType(), "bindContent",
                                  function(voidType, collectionType, observableCollectionType));
            }

            if (child instanceof EmitCollectionWrapperNode) {
                emitAddChildToReferenceTracker(context, targetLocal, sourceLocal);
            }

            code.releaseLocal(targetLocal);
            code.releaseLocal(sourceLocal);

            return true;
        }

        return false;
    }

    private void emitAddChildToReferenceTracker(BytecodeEmitContext context, Local targetLocal, Local sourceLocal) {
        context.getOutput()
            .aload(0)
            .aload(targetLocal)
            .aload(sourceLocal)
            .invokevirtual(context.getMarkupClass(), ReferenceTrackerGenerator.ADD_REFERENCE_METHOD,
                           function(voidType, ObjectType(), ObjectType()));

    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (ValueEmitterNode)child.accept(visitor);

        if (converter != null) {
            converter = (ValueEmitterNode)converter.accept(visitor);
        }

        if (format != null) {
            format = (ValueEmitterNode)format.accept(visitor);
        }
    }

    @Override
    public EmitPropertyBindingNode deepClone() {
        return new EmitPropertyBindingNode(
            propertyInfo,
            bindingMode,
            child.deepClone(),
            converter != null ? converter.deepClone() : null,
            format != null ? format.deepClone() : null,
            getSourceInfo());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitPropertyBindingNode that = (EmitPropertyBindingNode)o;
        return bindingMode == that.bindingMode &&
            propertyInfo.equals(that.propertyInfo) &&
            child.equals(that.child) &&
            Objects.equals(converter, that.converter) &&
            Objects.equals(format, that.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyInfo, bindingMode, child, converter, format);
    }
}
