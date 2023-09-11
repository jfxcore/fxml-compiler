// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.emit.EmitCollectionWrapperNode;
import org.jfxcore.compiler.ast.emit.EmitPropertyBindingNode;
import org.jfxcore.compiler.ast.emit.EmitPropertySetterNode;
import org.jfxcore.compiler.ast.emit.EmitStaticPropertySetterNode;
import org.jfxcore.compiler.ast.emit.EmitUnwrapObservableNode;
import org.jfxcore.compiler.ast.emit.EmitValueWrapperNode;
import org.jfxcore.compiler.ast.emit.EmitterNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.util.Classes.*;

public class BindingEmitterFactory {

    public static EmitterNode createBindingEmitter(
            TransformContext context, PropertyNode propertyNode, BindingNode bindingNode, PropertyInfo propertyInfo) {
        checkPreconditions(context, propertyNode, propertyInfo, bindingNode);

        if (bindingNode.getMode().isObservable()) {
            return createPropertyBindingEmitter(bindingNode, propertyInfo);
        }

        return createPropertyAssignmentEmitter(bindingNode, propertyInfo);
    }

    private static EmitterNode createPropertyAssignmentEmitter(BindingNode bindingNode, PropertyInfo propertyInfo) {
        SourceInfo sourceInfo = bindingNode.getSourceInfo();
        BindingMode bindingMode = bindingNode.getMode();
        TypeInstance targetType = propertyInfo.getType();
        BindingEmitterInfo result = bindingNode.toEmitter(propertyInfo.getDeclaringType());
        ValueEmitterNode value = null;

        if (bindingMode.isContent()) {
            if (isValidContentBindingSource(bindingMode, targetType, result.getType())) {
                value = result.getValue();
            } else if (result.getObservableType() != null
                    && isValidContentBindingSource(bindingMode, targetType, result.getValueType())) {
                value = new EmitUnwrapObservableNode(result.getValue());
            }

            if (value == null) {
                throw BindingSourceErrors.invalidContentAssignmentSource(
                    sourceInfo, result.getSourceDeclaringType(), result.getSourceName());
            }

            TypeInstance targetItemType = targetType.getArguments().get(0);
            TypeInstance sourceItemType = TypeHelper.getTypeInstance(value).getArguments().get(0);

            if (!targetItemType.isAssignableFrom(sourceItemType)) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    sourceInfo, sourceItemType.getJavaName(), targetItemType.getJavaName());
            }
        } else {
            if (targetType.isAssignableFrom(result.getType())) {
                value = result.getValue();
            } else if (result.getObservableType() != null) {
                value = new EmitUnwrapObservableNode(result.getValue());
            } else if (isCollectionWrapperApplicable(bindingNode, targetType, result.getValueType())) {
                value = new EmitCollectionWrapperNode(
                    result.getValue(),
                    result.getValueType(),
                    result.getObservableType(),
                    false,
                    sourceInfo);
            } else {
                throw BindingSourceErrors.cannotConvertSourceType(
                    sourceInfo, result.getValueType().getJavaName(), targetType.getJavaName());
            }

            if (!targetType.isAssignableFrom(TypeHelper.getTypeInstance(value))) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    sourceInfo, result.getValueType().getJavaName(), targetType.getJavaName());
            }
        }

        if (propertyInfo.isStatic()) {
            return new EmitStaticPropertySetterNode(
                propertyInfo.getDeclaringType(), propertyInfo.getSetter(), value, sourceInfo);
        }

        return new EmitPropertySetterNode(propertyInfo, value, bindingMode.isContent(), sourceInfo);
    }

    private static EmitterNode createPropertyBindingEmitter(BindingNode bindingNode, PropertyInfo propertyInfo) {
        BindingMode bindingMode = bindingNode.getMode();
        TypeInstance targetType = propertyInfo.getType();
        ValueEmitterNode value;
        BindingEmitterInfo result;

        try {
            result = bindingNode.toEmitter(propertyInfo.getDeclaringType());
        } catch (MarkupException ex) {
            TypeInstance sourceType = (TypeInstance)ex.getProperties().get("sourceType");

            if (ex.getDiagnostic().getCode() == ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE
                    && isValidContentBindingSource(bindingMode, targetType, sourceType)) {
                throw BindingSourceErrors.invalidBidirectionalBindingSource(
                    ex.getSourceInfo(), sourceType.jvmType(), true);
            }

            throw ex;
        }

        if (bindingMode.isContent()) {
            if (isValidContentBindingSource(bindingMode, targetType, result.getType())) {
                value = result.getValue();
            } else if (isCollectionWrapperApplicable(bindingNode, targetType, result.getType())) {
                value = new EmitCollectionWrapperNode(
                    result.getValue(),
                    result.getValueType(),
                    result.getObservableType(),
                    false,
                    bindingNode.getSourceInfo());
            } else {
                throw BindingSourceErrors.invalidContentBindingSource(
                    bindingNode.getSourceInfo(), result.getSourceDeclaringType(),
                    result.getSourceName(), bindingMode.isBidirectional(),
                    isValidContentBindingSource(BindingMode.CONTENT, targetType, result.getValueType()));
            }

            TypeInstance sourceType = TypeHelper.getTypeInstance(value);

            if (bindingMode.isUnidirectional() && !targetType.isAssignableFrom(sourceType)) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    bindingNode.getSourceInfo(), result.getValueType().getJavaName(), targetType.getJavaName());
            }
            else if (bindingMode.isBidirectional() && !targetType.equals(sourceType)) {
                throw BindingSourceErrors.sourceTypeMismatch(
                    bindingNode.getSourceInfo(), result.getValueType().getJavaName(), targetType.getJavaName());
            }
        } else if (bindingMode.isBidirectional()) {
            if (!targetType.equals(result.getValueType())) {
                throw BindingSourceErrors.sourceTypeMismatch(
                    bindingNode.getSourceInfo(), result.getValueType().getJavaName(), targetType.getJavaName());
            }

            value = result.getValue();
        } else {
            if (targetType.isAssignableFrom(result.getValueType())) {
                if (result.getObservableType() == null) {
                    value = new EmitValueWrapperNode(result.getValue());
                } else {
                    value = result.getValue();
                }
            }
            else if (isCollectionWrapperApplicable(bindingNode, targetType, result.getValueType())) {
                value = new EmitCollectionWrapperNode(
                    result.getValue(),
                    result.getValueType(),
                    result.getObservableType(),
                    true,
                    bindingNode.getSourceInfo());
            }
            else {
                throw BindingSourceErrors.cannotConvertSourceType(
                    bindingNode.getSourceInfo(), result.getValueType().getJavaName(), targetType.getJavaName());
            }
        }

        return new EmitPropertyBindingNode(propertyInfo, value, bindingMode, bindingNode.getSourceInfo());
    }

    private static void checkPreconditions(
            TransformContext context, PropertyNode propertyNode, PropertyInfo propertyInfo, BindingNode bindingNode) {
        int count = ValueEmitterFactory.getParentsUnderInitializationCount(context);
        if (count > 0 && bindingNode.getBindingDistance() <= count) {
            throw PropertyAssignmentErrors.cannotReferenceNodeUnderInitialization(
                context, propertyInfo, bindingNode.getBindingDistance(), propertyNode.getSourceInfo());
        }

        BindingMode bindingMode = bindingNode.getMode();
        if (bindingMode.isObservable()) {
            if (bindingMode.isContent()) {
                if (!propertyInfo.isContentBindable(bindingMode)) {
                    throw PropertyAssignmentErrors.invalidContentBindingTarget(
                        propertyNode.getSourceInfo(), propertyInfo, bindingMode);
                }
            } else {
                if (propertyInfo.isReadOnly()) {
                    throw PropertyAssignmentErrors.cannotModifyReadOnlyProperty(
                        propertyNode.getSourceInfo(), propertyInfo);
                }

                if (!propertyInfo.isBindable()) {
                    throw PropertyAssignmentErrors.invalidBindingTarget(
                        propertyNode.getSourceInfo(), propertyInfo);
                }
            }
        } else {
            if (bindingMode.isContent()) {
                if (!propertyInfo.isContentBindable(bindingMode)) {
                    throw PropertyAssignmentErrors.invalidContentBindingTarget(
                        propertyNode.getSourceInfo(), propertyInfo, bindingMode);
                }
            } else {
                if (propertyInfo.isReadOnly()) {
                    throw PropertyAssignmentErrors.cannotModifyReadOnlyProperty(
                        propertyNode.getSourceInfo(), propertyInfo);
                }
            }
        }
    }

    private static boolean isValidContentBindingSource(
            BindingMode mode, TypeInstance target, @Nullable TypeInstance source) {
        if (source == null) {
            return false;
        }

        if (mode.isUnidirectional()) {
            return target.subtypeOf(ListType()) && source.subtypeOf(ObservableListType())
                || target.subtypeOf(SetType()) && source.subtypeOf(ObservableSetType())
                || target.subtypeOf(MapType()) && source.subtypeOf(ObservableMapType());
        }

        if (mode.isBidirectional()) {
            return target.subtypeOf(ObservableListType()) && source.subtypeOf(ObservableListType())
                || target.subtypeOf(ObservableSetType()) && source.subtypeOf(ObservableSetType())
                || target.subtypeOf(ObservableMapType()) && source.subtypeOf(ObservableMapType());
        }

        return target.subtypeOf(CollectionType()) && source.subtypeOf(CollectionType())
            || target.subtypeOf(MapType()) && source.subtypeOf(MapType());
    }

    private static boolean isCollectionWrapperApplicable(
            BindingNode node, TypeInstance target, TypeInstance source) {
        switch (node.getMode()) {
            case ONCE:
            case UNIDIRECTIONAL:
                return target.subtypeOf(ObservableListType()) && source.subtypeOf(ListType())
                    || target.subtypeOf(ObservableSetType()) && source.subtypeOf(SetType())
                    || target.subtypeOf(ObservableMapType()) && source.subtypeOf(MapType());

            case UNIDIRECTIONAL_CONTENT:
                if (source.subtypeOf(ObservableValueType())) {
                    Resolver resolver = new Resolver(node.getSourceInfo());
                    source = resolver.findObservableArgument(source);

                    return target.subtypeOf(ListType()) && source.subtypeOf(ListType())
                        || target.subtypeOf(SetType()) && source.subtypeOf(SetType())
                        || target.subtypeOf(MapType()) && source.subtypeOf(MapType());
                }

                return target.subtypeOf(ListType()) && source.subtypeOf(ObservableListType())
                    || target.subtypeOf(SetType()) && source.subtypeOf(ObservableSetType())
                    || target.subtypeOf(MapType()) && source.subtypeOf(ObservableMapType());

            case BIDIRECTIONAL_CONTENT:
                if (source.subtypeOf(ObservableValueType())) {
                    Resolver resolver = new Resolver(node.getSourceInfo());
                    source = resolver.findObservableArgument(source);
                }

                return target.subtypeOf(ObservableListType()) && source.subtypeOf(ObservableListType())
                    || target.subtypeOf(ObservableSetType()) && source.subtypeOf(ObservableSetType())
                    || target.subtypeOf(ObservableMapType()) && source.subtypeOf(ObservableMapType());

            default:
                return false;
        }
    }

}
