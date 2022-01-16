// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.common;

import javassist.CtClass;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
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
import org.jfxcore.compiler.parse.TypeParser;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Replaces all instances of {@link TypeNode} in the AST with {@link ResolvedTypeNode} by resolving
 * the names against the imported packages.
 */
public class ResolveTypeTransform implements Transform {

    private final boolean allowUnresolvableTypes;

    public ResolveTypeTransform(boolean allowUnresolvableTypes) {
        this.allowUnresolvableTypes = allowUnresolvableTypes;
    }

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(ValidateIntrinsicsTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(TypeNode.class)) {
            return node;
        }

        TypeNode typeNode = (TypeNode)node;
        ObjectNode objectNode = context.getParent().as(ObjectNode.class);
        Resolver resolver = new Resolver(node.getSourceInfo());

        if (typeNode.isIntrinsic()) {
            Intrinsic intrinsic = Intrinsics.find(typeNode.getName());

            //noinspection ConstantConditions
            return new ResolvedTypeNode(
                resolver.getTypeInstance(intrinsic.getType()),
                typeNode.getName(),
                typeNode.getMarkupName(),
                true,
                typeNode.getSourceInfo());
        }

        CtClass objectTypeClass = allowUnresolvableTypes ?
            resolver.tryResolveClassAgainstImports(typeNode.getName()) :
            resolver.resolveClassAgainstImports(typeNode.getName());

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

            typeArgsNode.remove();
            TypeInstance objectType = resolver.getTypeInstance(objectTypeClass);

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
            } catch (MarkupException ex) {
                if (!allowUnresolvableTypes || ex.getDiagnostic().getCode() != ErrorCode.CLASS_NOT_FOUND) {
                    throw ex;
                }

                type = objectType;
            }
        } else {
            type = resolver.getTypeInstance(objectTypeClass, Collections.emptyList());
        }

        CtClass bindingContextType = context.getBindingContextClass();
        if (bindingContextType != null && context.getParent(objectNode) instanceof DocumentNode) {
            type = new TypeInstance(bindingContextType, Collections.emptyList(), List.of(type));
        }

        return new ResolvedTypeNode(
            type,
            typeNode.getName(),
            typeNode.getMarkupName(),
            false,
            node.getSourceInfo());
    }

}
