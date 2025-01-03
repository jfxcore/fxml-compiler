// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import javassist.CtClass;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.parse.TypeFormatter;
import org.jfxcore.compiler.parse.TypeParser;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Collections;
import java.util.List;

/**
 * Replaces {@link TypeNode} instances in the AST with {@link ResolvedTypeNode} by resolving
 * the names against the imported packages. When the name cannot be resolved, the {@code TypeNode}
 * instance is not replaced.
 */
public class ResolveTypeTransform implements Transform {

    private final boolean allowUnresolvableTypeArguments;

    public ResolveTypeTransform(boolean allowUnresolvableTypeArguments) {
        this.allowUnresolvableTypeArguments = allowUnresolvableTypeArguments;
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(TypeNode.class)) {
            return node;
        }

        TypeNode typeNode = (TypeNode)node;
        ObjectNode objectNode = context.getParent().as(ObjectNode.class);
        boolean parentIsDocument = context.getParent(1) instanceof DocumentNode;
        Resolver resolver = new Resolver(node.getSourceInfo());

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

        CtClass objectTypeClass = resolver.tryResolveClassAgainstImports(typeNode.getName());
        if (objectTypeClass == null) {
            return typeNode;
        }

        CtClass markupClass = context.getMarkupClass();
        if (markupClass != null) {
            AccessVerifier.verifyAccessible(objectTypeClass, markupClass, typeNode.getSourceInfo());
        }

        if (objectNode == null) {
            return new ResolvedTypeNode(
                resolver.getTypeInstance(objectTypeClass),
                typeNode.getName(),
                typeNode.getMarkupName(),
                true,
                typeNode.getSourceInfo());
        }

        TypeInstance type;
        PropertyNode typeArgsNode = objectNode.findIntrinsicProperty(Intrinsics.TYPE_ARGUMENTS);

        if (typeArgsNode != null) {
            if (objectTypeClass.getGenericSignature() == null) {
                throw ObjectInitializationErrors.cannotParameterizeType(node.getSourceInfo(), objectTypeClass);
            }

            if (parentIsDocument) {
                String formattedTypeArgs = new TypeFormatter(
                    typeArgsNode.getTextValueNotEmpty(context),
                    typeArgsNode.getSourceInfo().getStart()).format();

                objectNode.setNodeData(NodeDataKey.FORMATTED_TYPE_ARGUMENTS, formattedTypeArgs);
            }

            typeArgsNode.remove();
            TypeInstance objectType;

            try {
                objectType = resolver.getTypeInstance(objectTypeClass);
            } catch (MarkupException ex) {
                if (ex.getDiagnostic().getCode() != ErrorCode.CLASS_NOT_FOUND) {
                    throw ex;
                }

                return typeNode;
            }

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
                    typeArgsNode.getTextValue(context),
                    typeArgsNode.getValues().get(0).getSourceInfo().getStart()).parse();

                type = resolver.getTypeInstance(objectTypeClass, typeArguments);

                objectNode.setNodeData(NodeDataKey.TYPE_ARGUMENTS_SOURCE_INFO,
                    typeArgsNode.getValues().get(0).getSourceInfo());
            } catch (MarkupException ex) {
                if (!allowUnresolvableTypeArguments || ex.getDiagnostic().getCode() != ErrorCode.CLASS_NOT_FOUND) {
                    throw ex;
                }

                type = objectType;
            }
        } else {
            try {
                type = resolver.getTypeInstance(objectTypeClass, Collections.emptyList());
            } catch (MarkupException ex) {
                if (ex.getDiagnostic().getCode() != ErrorCode.CLASS_NOT_FOUND) {
                    throw ex;
                }

                return typeNode;
            }
        }

        CtClass classType = context.getCodeBehindOrMarkupClass();
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

}
