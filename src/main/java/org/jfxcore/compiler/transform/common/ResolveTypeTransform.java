// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.UnresolvedTypeNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.parse.TypeFormatter;
import org.jfxcore.compiler.parse.TypeParser;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.AccessVerifier;
import java.util.List;

/**
 * Replaces {@link TypeNode} instances in the AST with {@link ResolvedTypeNode} by resolving
 * the names against the imported packages. When the name cannot be resolved, the {@code TypeNode}
 * instance is replaced with {@link UnresolvedTypeNode}.
 */
public class ResolveTypeTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(TypeNode.class)) {
            return node;
        }

        TypeNode typeNode = (TypeNode)node;
        ObjectNode objectNode = context.getParent().as(ObjectNode.class);
        boolean parentIsDocument = context.getParent(1) instanceof DocumentNode;
        Resolver resolver = new Resolver(node.getSourceInfo());
        TypeInvoker invoker = new TypeInvoker(node.getSourceInfo());

        if (typeNode.isIntrinsic()) {
            Intrinsic intrinsic = Intrinsics.find(typeNode.getName());

            //noinspection ConstantConditions
            return new ResolvedTypeNode(
                intrinsic.getType(typeNode),
                typeNode.getName(),
                typeNode.getMarkupName(),
                true,
                typeNode.getSourceInfo());
        }

        TypeDeclaration objectTypeClass;

        try {
            objectTypeClass = resolver.resolveClassAgainstImports(typeNode.getName());
        } catch (MarkupException ex) {
            return new UnresolvedTypeNode(typeNode, List.of(), ex);
        }

        if (objectNode != null) {
            PropertyNode constantProperty = objectNode.findIntrinsicProperty(Intrinsics.CONSTANT);
            if (constantProperty != null) {
                return resolveConstantType(context, constantProperty, objectTypeClass);
            }

            PropertyNode factoryProperty = objectNode.findIntrinsicProperty(Intrinsics.FACTORY);
            if (factoryProperty != null) {
                return resolveFactoryType(context, factoryProperty, objectTypeClass);
            }
        }

        TypeDeclaration markupClass = context.getMarkupClass();
        if (markupClass != null) {
            AccessVerifier.verifyAccessible(objectTypeClass, markupClass, typeNode.getSourceInfo());
        }

        if (objectNode == null) {
            return new ResolvedTypeNode(
                invoker.invokeType(objectTypeClass),
                typeNode.getName(),
                typeNode.getMarkupName(),
                true,
                typeNode.getSourceInfo());
        }

        TypeInstance type;
        PropertyNode typeArgsNode = objectNode.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS);

        if (typeArgsNode != null) {
            if (objectTypeClass.genericSignature() == null) {
                throw ObjectInitializationErrors.cannotParameterizeType(node.getSourceInfo(), objectTypeClass);
            }

            if (parentIsDocument) {
                String formattedTypeArgs = new TypeFormatter(
                    typeArgsNode.getTrimmedTextNotEmpty(context),
                    typeArgsNode.getTrimmedTextSourceInfo(context).getStart()).format();

                objectNode.setNodeData(NodeDataKey.FORMATTED_TYPE_ARGUMENTS, formattedTypeArgs);
            }

            typeArgsNode.remove();

            try {
                switch (typeArgsNode.getValues().size()) {
                    case 1: break;
                    case 0: throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                        typeArgsNode.getSourceInfo(), objectTypeClass, typeArgsNode.getMarkupName());
                    default: throw PropertyAssignmentErrors.propertyCannotHaveMultipleValues(
                        typeArgsNode.getSourceInfo(), objectTypeClass, typeArgsNode.getMarkupName());
                }

                if (!(typeArgsNode.getValues().get(0) instanceof TextNode)) {
                    throw PropertyAssignmentErrors.propertyMustContainText(
                        typeArgsNode.getSourceInfo(), objectTypeClass, typeArgsNode.getMarkupName());
                } else if (((TextNode)typeArgsNode.getValues().get(0)).getText().isEmpty()) {
                    throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                        typeArgsNode.getSourceInfo(), objectTypeClass, typeArgsNode.getMarkupName());
                }

                List<TypeInstance> typeArguments = new TypeParser(
                    typeArgsNode.getTrimmedTextNotEmpty(context),
                    typeArgsNode.getTrimmedTextSourceInfo(context).getStart()).parse();

                type = invoker.invokeType(objectTypeClass, typeArguments);

                objectNode.setNodeData(NodeDataKey.TYPE_ARGUMENTS_SOURCE_INFO,
                    typeArgsNode.getValues().get(0).getSourceInfo());
            } catch (MarkupException ex) {
                if (ex.getDiagnostic().getCode() != ErrorCode.CLASS_NOT_FOUND) {
                    throw ex;
                }

                List<? extends Node> typeArgs = typeArgsNode.getValues() instanceof ListNode listNode
                    ? listNode.getValues()
                    : List.of(typeArgsNode.getValues().get(0));

                return new UnresolvedTypeNode(typeNode, typeArgs, ex);
            }
        } else {
            try {
                type = invoker.invokeType(objectTypeClass, List.of());
            } catch (MarkupException ex) {
                if (ex.getDiagnostic().getCode() != ErrorCode.CLASS_NOT_FOUND) {
                    throw ex;
                }

                return new UnresolvedTypeNode(typeNode, List.of(), ex);
            }
        }

        TypeDeclaration classType = context.getCodeBehindOrMarkupClass();
        if (classType != null && parentIsDocument) {
            type = TypeInstance.of(classType);
        }

        return new ResolvedTypeNode(
            type,
            typeNode.getName(),
            typeNode.getMarkupName(),
            false,
            node.getSourceInfo());
    }

    private ResolvedTypeNode resolveConstantType(
            TransformContext context, PropertyNode constantProperty, TypeDeclaration objectTypeClass) {
        Node fieldNameNode = constantProperty.getSingleValue(context);
        SourceInfo sourceInfo = fieldNameNode.getSourceInfo();

        if (!(fieldNameNode instanceof TextNode fieldNameTextNode)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                sourceInfo, objectTypeClass, constantProperty.getMarkupName());
        }

        var resolver = new Resolver(sourceInfo);
        FieldDeclaration field = resolver.resolveField(objectTypeClass, fieldNameTextNode.getText().trim(), false);
        TypeInstance fieldType = new TypeInvoker(sourceInfo).invokeFieldType(field, List.of());
        var resolvedType = new ResolvedTypeNode(
            fieldType, fieldType.name(), fieldType.name(), false, sourceInfo);
        resolvedType.setNodeData(NodeDataKey.CONSTANT_DECLARING_TYPE, objectTypeClass);
        return resolvedType;
    }

    private ResolvedTypeNode resolveFactoryType(
            TransformContext context, PropertyNode factoryProperty, TypeDeclaration objectTypeClass) {
        Node methodNameNode = factoryProperty.getSingleValue(context);
        SourceInfo sourceInfo = methodNameNode.getSourceInfo();

        if (!(methodNameNode instanceof TextNode methodNameTextNode)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                sourceInfo, objectTypeClass, factoryProperty.getMarkupName());
        }

        TypeParser.MethodInfo methodInfo =
            new TypeParser(methodNameTextNode.getText(), sourceInfo.getStart()).parseMethod();

        var resolver = new Resolver(methodInfo.sourceInfo());
        var invoker = new TypeInvoker(methodInfo.sourceInfo());
        MethodDeclaration method = resolver.resolveGetter(objectTypeClass, methodInfo.methodName(), true, null);
        TypeInstance returnType = invoker.invokeReturnType(method, List.of(), methodInfo.typeWitnesses());
        var resolvedType = new ResolvedTypeNode(
            returnType, returnType.name(), returnType.name(), false, sourceInfo);
        resolvedType.setNodeData(NodeDataKey.FACTORY_DECLARING_TYPE, objectTypeClass);
        return resolvedType;
    }
}
