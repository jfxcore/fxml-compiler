// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.NotFoundException;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.markup.util.ValueEmitterFactory;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.util.PropertyHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Replaces all instances of {@link ObjectNode} in the AST with a node that represents one of the following:
 *     1. reference to an existing object ({@link EmitClassConstantNode})
 *     2. instantiation of a new object ({@link EmitObjectNode})
 */
public class ObjectTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(
            AddTemplateIdFields.class,
            IdPropertyTransform.class,
            DefaultPropertyTransform.class,
            DefineBlockTransform.class,
            StylesheetTransform.class,
            NullIntrinsicTransform.class,
            TypeIntrinsicTransform.class,
            ConstantIntrinsicTransform.class,
            ValueIntrinsicTransform.class,
            UrlIntrinsicTransform.class,
            BindingTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        if (objectNode.getType().isIntrinsic()) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), objectNode.getType().getMarkupName());
        }

        Node parentNode = context.getParent(0);

        if (parentNode instanceof EmitInitializeRootNode || parentNode instanceof TemplateContentNode) {
            return new EmitObjectNode(
                null,
                TypeHelper.getTypeInstance(objectNode),
                null,
                Collections.emptyList(),
                PropertyHelper.getSorted(objectNode, objectNode.getProperties()),
                EmitObjectNode.CreateKind.NONE,
                objectNode.getSourceInfo());
        }

        return createNode(context, objectNode);
    }

    private ValueNode createNode(TransformContext context, ObjectNode node) {
        PropertyNode valueNode = node.findIntrinsicProperty(Intrinsics.VALUE);
        if (valueNode != null) {
            valueNode.remove();
            return createValueOfNode(context, node, valueNode, valueNode.getTextValue(context));
        }

        PropertyNode constantNode = node.findIntrinsicProperty(Intrinsics.CONSTANT);
        if (constantNode != null) {
            constantNode.remove();
            return createConstantNode(context, node, constantNode);
        }

        ValueNode newObjectNode;
        List<DiagnosticInfo> namedArgsDiagnostics = new ArrayList<>();

        try {
            newObjectNode = ValueEmitterFactory.newObjectWithNamedParams(node, namedArgsDiagnostics);
            if (newObjectNode != null) {
                return newObjectNode;
            }
        } catch (MarkupException ex) {
            if (ex.getDiagnostic().getCode() != ErrorCode.BINDING_EXPRESSION_NOT_APPLICABLE) {
                throw ex;
            }
        }

        newObjectNode = ValueEmitterFactory.newLiteralValue(node);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        List<DiagnosticInfo> withArgsDiagnostics = new ArrayList<>();
        newObjectNode = ValueEmitterFactory.newObjectWithArguments(node, withArgsDiagnostics);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        List<DiagnosticInfo> newCollectionDiagnostics = new ArrayList<>();
        newObjectNode = ValueEmitterFactory.newCollection(node, newCollectionDiagnostics);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        List<DiagnosticInfo> newMapDiagnostics = new ArrayList<>();
        newObjectNode = ValueEmitterFactory.newMap(node, newMapDiagnostics);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        newObjectNode = ValueEmitterFactory.newObjectByCoercion(node);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        diagnostics.addAll(namedArgsDiagnostics);
        diagnostics.addAll(withArgsDiagnostics);
        diagnostics.addAll(newCollectionDiagnostics);
        diagnostics.addAll(newMapDiagnostics);

        if (!diagnostics.isEmpty()) {
            throw ObjectInitializationErrors.constructorNotFound(
                node.getSourceInfo(),
                TypeHelper.getJvmType(node),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        throw ObjectInitializationErrors.constructorNotFound(node.getSourceInfo(), TypeHelper.getJvmType(node));
    }

    private ValueNode createValueOfNode(
            TransformContext context, ObjectNode objectNode, PropertyNode propertyNode, String textValue) {
        TypeInstance nodeType = TypeHelper.getTypeInstance(objectNode);

        if (!objectNode.getChildren().isEmpty()) {
            throw ObjectInitializationErrors.valueOfCannotHaveContent(
                propertyNode.getSourceInfo(), nodeType.jvmType(), propertyNode.getMarkupName());
        }

        PropertyNode constantNode = objectNode.getProperties().stream()
            .filter(p -> p.isIntrinsic(Intrinsics.CONSTANT)).findFirst().orElse(null);

        if (constantNode != null) {
            throw ObjectInitializationErrors.conflictingProperties(
                propertyNode.getSourceInfo(), propertyNode.getMarkupName(), constantNode.getMarkupName());
        }

        Resolver resolver = new Resolver(propertyNode.getSourceInfo());
        boolean hasValueOfMethod = resolver.tryResolveValueOfMethod(nodeType.jvmType()) != null;

        if (!hasValueOfMethod) {
            TypeInstance supertype = resolver.tryResolveSupertypeWithValueOfMethod(nodeType);
            throw ObjectInitializationErrors.valueOfMethodNotFound(
                propertyNode.getSourceInfo(), nodeType.jvmType(), supertype != null ? supertype.jvmType() :  null);
        }

        return new EmitObjectNode(
            findAndRemoveId(context, objectNode),
            nodeType,
            null,
            List.of(new EmitLiteralNode(
                resolver.getTypeInstance(Classes.StringType()),
                textValue,
                propertyNode.getSingleValue(context).getSourceInfo())),
            Stream.concat(objectNode.getChildren().stream(), objectNode.getProperties().stream())
                .collect(Collectors.toList()),
            EmitObjectNode.CreateKind.VALUE_OF,
            propertyNode.getSourceInfo());
    }

    private ValueNode createConstantNode(
            TransformContext context, ObjectNode objectNode, PropertyNode constantProperty) {
        if (!objectNode.getChildren().isEmpty()) {
            throw ObjectInitializationErrors.constantCannotHaveContent(constantProperty.getSourceInfo());
        }

        PropertyNode valueNode = objectNode.getProperties().stream()
            .filter(p -> p.isIntrinsic(Intrinsics.VALUE)).findFirst().orElse(null);

        if (valueNode != null) {
            throw ObjectInitializationErrors.conflictingProperties(
                constantProperty.getSourceInfo(), constantProperty.getMarkupName(), valueNode.getMarkupName());
        }

        if (objectNode.getProperties().stream().anyMatch(
                p -> !p.isIntrinsic(Intrinsics.ID) && !p.isIntrinsic(Intrinsics.CONSTANT))) {
            throw ObjectInitializationErrors.constantCannotBeModified(constantProperty.getSourceInfo());
        }

        CtClass declaringType;
        String[] segments = constantProperty.getTextValueNotEmpty(context).split("\\.");

        if (segments.length == 1) {
            declaringType = TypeHelper.getJvmType(objectNode);
        } else if (segments.length == 2) {
            declaringType = new Resolver(objectNode.getSourceInfo()).resolveClassAgainstImports(segments[0]);
        } else {
            String className = Arrays.stream(segments)
                .limit(segments.length - 1)
                .collect(Collectors.joining("."));

            declaringType = new Resolver(objectNode.getSourceInfo()).resolveClass(className);
        }

        String fieldName = segments[segments.length - 1];
        TypeInstance fieldType;

        try {
            Resolver resolver = new Resolver(constantProperty.getSourceInfo());
            fieldType = resolver.getTypeInstance(declaringType.getField(fieldName), Collections.emptyList());
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.memberNotFound(constantProperty.getSourceInfo(), declaringType, fieldName);
        }

        ValueNode constantNode = new EmitClassConstantNode(
            findAndRemoveId(context, objectNode),
            fieldType,
            declaringType,
            fieldName,
            constantProperty.getSourceInfo());

        TypeInstance objectType = TypeHelper.getTypeInstance(objectNode);

        if (!objectType.isConvertibleFrom(fieldType)) {
            throw ObjectInitializationErrors.cannotAssignConstant(constantNode.getSourceInfo(), objectType, fieldType);
        }

        return constantNode;
    }

    private String findAndRemoveId(TransformContext context, ObjectNode node) {
        PropertyNode propertyNode = node.findIntrinsicProperty(Intrinsics.ID);
        if (propertyNode != null) {
            propertyNode.remove();
            return propertyNode.getTextValue(context);
        }

        return null;
    }

}
