// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.NotFoundException;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transforms the fx:constant element into an {@link EmitClassConstantNode}.
 * Note: this transform doesn't apply to the fx:constant property, which is handled by {@link ObjectTransform}.
 */
public class ConstantIntrinsicTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(BindingTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(Intrinsics.CONSTANT)){
            return node;
        }

        ObjectNode objectNode = (ObjectNode)node;
        Node parentNode = context.getParent();
        TypeInstance valueType = null;

        if (parentNode instanceof PropertyNode propertyNode) {
            TypeInstance parentType = TypeHelper.getTypeInstance(context.getParent(1));
            Resolver resolver = new Resolver(propertyNode.getSourceInfo());
            PropertyInfo propertyInfo = resolver.resolveProperty(parentType, propertyNode.getName());
            valueType = propertyInfo.getValueTypeInstance();
        }

        if (objectNode.getChildren().size() == 0) {
            throw ParserErrors.invalidExpression(objectNode.getSourceInfo());
        }

        if (objectNode.getChildren().size() > 1) {
            throw ParserErrors.expectedIdentifier(SourceInfo.span(objectNode.getChildren()));
        }

        if (!(objectNode.getChildren().get(0) instanceof TextNode textNode)) {
            throw ParserErrors.expectedIdentifier(objectNode.getChildren().get(0).getSourceInfo());
        }

        return createConstantNode(valueType, textNode.getText(), node.getSourceInfo());
    }

    private ValueNode createConstantNode(@Nullable TypeInstance valueType, String textValue, SourceInfo sourceInfo) {
        CtClass declaringType;
        String[] segments = textValue.split("\\.");

        if (segments.length == 1) {
            if (valueType == null) {
                throw SymbolResolutionErrors.notFound(sourceInfo, segments[0]);
            }

            declaringType = TypeHelper.getBoxedType(valueType.jvmType());
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
            Resolver resolver = new Resolver(sourceInfo);
            fieldType = resolver.getTypeInstance(declaringType.getField(fieldName), Collections.emptyList());
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.memberNotFound(sourceInfo, declaringType, fieldName);
        }

        return new EmitClassConstantNode(
            null,
            fieldType,
            declaringType,
            fieldName,
            sourceInfo);
    }

}
