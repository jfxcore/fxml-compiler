// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Transforms the fx:constant element into an {@link EmitClassConstantNode}.
 * Note: this transform doesn't apply to the fx:constant attribute, which is handled by {@link ObjectTransform}.
 */
public class ConstantIntrinsicTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(Intrinsics.CONSTANT)){
            return node;
        }

        ObjectNode objectNode = (ObjectNode)node;

        if (objectNode.getChildren().size() == 0 || objectNode.getProperties().size() > 0) {
            throw ParserErrors.invalidExpression(objectNode.getSourceInfo());
        }

        if (objectNode.getChildren().size() > 1) {
            throw ParserErrors.expectedIdentifier(SourceInfo.span(objectNode.getChildren()));
        }

        if (!(objectNode.getChildren().get(0) instanceof TextNode textNode)) {
            throw ParserErrors.expectedIdentifier(objectNode.getChildren().get(0).getSourceInfo());
        }

        return createConstantNode(context, TypeHelper.getTypeInstance(objectNode), textNode, node.getSourceInfo());
    }

    private ValueNode createConstantNode(
            TransformContext context,
            @Nullable TypeInstance propertyType,
            TextNode textNode,
            SourceInfo nodeSourceInfo) {
        CtClass declaringType;
        String[] segments = textNode.getText().split("\\.");
        SourceInfo sourceInfo = textNode.getSourceInfo();
        Resolver resolver = new Resolver(sourceInfo);

        if (segments.length == 1) {
            if (propertyType == null) {
                propertyType = resolver.getTypeInstance(context.getBindingContextClass());
            }

            declaringType = TypeHelper.getBoxedType(propertyType.jvmType());
        } else if (segments.length == 2) {
            declaringType = new Resolver(sourceInfo).resolveClassAgainstImports(segments[0]);
        } else {
            String className = Arrays.stream(segments)
                .limit(segments.length - 1)
                .collect(Collectors.joining("."));

            declaringType = new Resolver(sourceInfo).resolveClass(className);
        }

        String fieldName = segments[segments.length - 1];
        TypeInstance fieldType;

        try {
            CtField field = declaringType.getField(fieldName);
            AccessVerifier.verifyAccessible(field, context.getMarkupClass(), sourceInfo);
            fieldType = resolver.getTypeInstance(field, Collections.emptyList());
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.memberNotFound(sourceInfo, declaringType, fieldName);
        }

        return new EmitClassConstantNode(null, fieldType, declaringType, fieldName, nodeSourceInfo);
    }

}
