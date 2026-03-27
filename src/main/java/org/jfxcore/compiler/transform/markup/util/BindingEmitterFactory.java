// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.ast.emit.EmitterNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.List;

import static org.jfxcore.compiler.type.TypeSymbols.*;

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
        BindingEmitterInfo result = bindingNode.toPathEmitter(propertyInfo.getDeclaringType(), propertyInfo.getType());
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
                    result.getSourceInfo(), result.getSourceDeclaringType(), result.getSourceName());
            }

            TypeInstance targetItemType = targetType.arguments().get(0);
            TypeInstance sourceItemType = TypeHelper.getTypeInstance(value).arguments().get(0);

            if (!targetItemType.isAssignableFrom(sourceItemType)) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    result.getSourceInfo(), sourceItemType.javaName(), targetItemType.javaName());
            }
        } else {
            if (targetType.isAssignableFrom(result.getType())) {
                value = result.getValue();
            } else if (result.getObservableType() != null) {
                value = new EmitUnwrapObservableNode(result.getValue());
            }

            if (value == null || !targetType.isAssignableFrom(TypeHelper.getTypeInstance(value))) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    result.getSourceInfo(), result.getValueType().javaName(), targetType.javaName());
            }
        }

        if (propertyInfo.isStatic()) {
            return new EmitStaticPropertySetterNode(propertyInfo.getDeclaringType(), propertyInfo, value, sourceInfo);
        }

        return new EmitPropertySetterNode(propertyInfo, value, bindingMode.isContent(), sourceInfo);
    }

    private static EmitterNode createPropertyBindingEmitter(BindingNode bindingNode, PropertyInfo propertyInfo) {
        BindingMode bindingMode = bindingNode.getMode();
        TypeInstance targetType = propertyInfo.getType();
        ValueEmitterNode value, format = null, converter = null;
        BindingEmitterInfo result;

        try {
            result = bindingNode.toPathEmitter(propertyInfo.getDeclaringType(), propertyInfo.getType());
        } catch (MarkupException ex) {
            TypeInstance sourceType = (TypeInstance)ex.getProperties().get("sourceType");

            if (ex.getDiagnostic().getCode() == ErrorCode.INVALID_BIDIRECTIONAL_BINDING_SOURCE
                    && isValidContentBindingSource(bindingMode, targetType, sourceType)) {
                throw BindingSourceErrors.invalidBidirectionalBindingSource(
                    ex.getSourceInfo(), sourceType, true);
            }

            throw ex;
        }

        if (bindingMode.isContent()) {
            if (isValidContentBindingSource(bindingMode, targetType, result.getType())) {
                value = result.getValue();
            } else if (isCollectionWrapperApplicable(bindingNode, targetType, result)) {
                value = new EmitCollectionWrapperNode(
                    result.getValue(),
                    result.getValueType(),
                    result.getObservableType(),
                    bindingNode.getSourceInfo());
            } else {
                throw BindingSourceErrors.invalidContentBindingSource(
                    result.getSourceInfo(), result.getSourceDeclaringType(),
                    result.getSourceName(), targetType, bindingMode.isBidirectional(),
                    isValidContentBindingSource(BindingMode.CONTENT, targetType, result.getValueType()));
            }

            TypeInstance sourceType = TypeHelper.getTypeInstance(value);

            if (!targetType.isAssignableFrom(sourceType)) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    result.getSourceInfo(), result.getValueType().javaName(), targetType.javaName());
            }
        } else if (bindingMode.isBidirectional()) {
            BindingEmitterInfo converterEmitterInfo = bindingNode.toConverterEmitter(propertyInfo.getDeclaringType());
            converter = converterEmitterInfo != null ? converterEmitterInfo.getValue() : null;

            BindingEmitterInfo formatEmitterInfo = bindingNode.toFormatEmitter(propertyInfo.getDeclaringType());
            format = formatEmitterInfo != null ? formatEmitterInfo.getValue() : null;

            if (!propertyInfo.getObservableType().subtypeOf(StringPropertyDecl())) {
                if (converterEmitterInfo != null) {
                    throw BindingSourceErrors.stringConversionNotApplicable(bindingNode.getSourceInfo(), "converter");
                }

                if (formatEmitterInfo != null) {
                    throw BindingSourceErrors.stringConversionNotApplicable(bindingNode.getSourceInfo(), "format");
                }
            }

            if (converter != null) {
                var invoker = new TypeInvoker(converter.getSourceInfo());
                var type = invoker.invokeType(StringConverterDecl(), List.of(result.getValueType().boxed()));

                if (!type.isAssignableFrom(converter.getType().getTypeInstance())) {
                    throw BindingSourceErrors.cannotConvertSourceType(
                        converter.getSourceInfo(),
                        converter.getType().getTypeInstance().javaName(),
                        type.javaName());
                }
            }

            if (format != null && !format.getType().getTypeInstance().subtypeOf(FormatDecl())) {
                throw BindingSourceErrors.cannotConvertSourceType(
                    format.getSourceInfo(),
                    format.getType().getTypeInstance().javaName(),
                    FormatDecl().javaName());
            }

            // Bidirectional bindings require equal types
            if (!targetType.equals(result.getValueType()) && converter == null && format == null) {
                throw BindingSourceErrors.sourceTypeMismatch(
                    result.getSourceInfo(), result.getValueType().javaName(), targetType.javaName());
            }

            value = result.getValue();
        } else if (targetType.isAssignableFrom(result.getValueType())) {
            if (result.getObservableType() != null) {
                value = result.getValue();
            } else {
                throw BindingSourceErrors.invalidUnidirectionalBindingSource(
                    result.getSourceInfo(), result.getSourceDeclaringType(),
                    result.getSourceName(), result.isFunction());
            }
        } else {
            throw BindingSourceErrors.cannotConvertSourceType(
                result.getSourceInfo(), result.getValueType().javaName(), targetType.javaName());
        }

        return new EmitPropertyBindingNode(
            propertyInfo, bindingMode, value, converter, format, bindingNode.getSourceInfo());
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
            return target.subtypeOf(ListDecl()) && source.subtypeOf(ObservableListDecl())
                || target.subtypeOf(SetDecl()) && source.subtypeOf(ObservableSetDecl())
                || target.subtypeOf(MapDecl()) && source.subtypeOf(ObservableMapDecl());
        }

        if (mode.isBidirectional()) {
            return target.subtypeOf(ObservableListDecl()) && source.subtypeOf(ObservableListDecl())
                || target.subtypeOf(ObservableSetDecl()) && source.subtypeOf(ObservableSetDecl())
                || target.subtypeOf(ObservableMapDecl()) && source.subtypeOf(ObservableMapDecl());
        }

        return target.subtypeOf(CollectionDecl()) && source.subtypeOf(CollectionDecl())
            || target.subtypeOf(MapDecl()) && source.subtypeOf(MapDecl());
    }

    private static boolean isCollectionWrapperApplicable(
            BindingNode node, TypeInstance target, BindingEmitterInfo source) {
        if (!source.isCompiledPath()) {
            return false;
        }

        TypeInstance sourceType = source.getValueType();

        switch (node.getMode()) {
            case UNIDIRECTIONAL_CONTENT:
                if (sourceType.subtypeOf(ObservableValueDecl())) {
                    Resolver resolver = new Resolver(node.getSourceInfo());
                    sourceType = resolver.findObservableArgument(sourceType);

                    return target.subtypeOf(ListDecl()) && sourceType.subtypeOf(ListDecl())
                        || target.subtypeOf(SetDecl()) && sourceType.subtypeOf(SetDecl())
                        || target.subtypeOf(MapDecl()) && sourceType.subtypeOf(MapDecl());
                }

                return target.subtypeOf(ListDecl()) && sourceType.subtypeOf(ObservableListDecl())
                    || target.subtypeOf(SetDecl()) && sourceType.subtypeOf(ObservableSetDecl())
                    || target.subtypeOf(MapDecl()) && sourceType.subtypeOf(ObservableMapDecl());

            case BIDIRECTIONAL_CONTENT:
                if (sourceType.subtypeOf(ObservableValueDecl())) {
                    Resolver resolver = new Resolver(node.getSourceInfo());
                    sourceType = resolver.findObservableArgument(sourceType);
                }

                return target.subtypeOf(ObservableListDecl()) && sourceType.subtypeOf(ObservableListDecl())
                    || target.subtypeOf(ObservableSetDecl()) && sourceType.subtypeOf(ObservableSetDecl())
                    || target.subtypeOf(ObservableMapDecl()) && sourceType.subtypeOf(ObservableMapDecl());

            default:
                return false;
        }
    }
}
