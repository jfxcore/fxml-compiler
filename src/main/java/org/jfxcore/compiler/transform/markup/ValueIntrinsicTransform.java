// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

/**
 * Transforms the fx:value element into an {@link EmitObjectNode}.
 * Note: this transform doesn't apply to the fx:value property, which is handled by {@link ObjectTransform}.
 */
public class ValueIntrinsicTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(Intrinsics.VALUE)){
            return node;
        }

        ObjectNode objectNode = (ObjectNode)node;
        PropertyNode propertyNode = context.getParent().as(PropertyNode.class);
        if (propertyNode == null) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), objectNode.getType().getMarkupName());
        }

        if (objectNode.getChildren().size() == 0) {
            throw ParserErrors.invalidExpression(objectNode.getSourceInfo());
        }

        if (objectNode.getChildren().size() > 1) {
            throw ParserErrors.invalidExpression(SourceInfo.span(objectNode.getChildren()));
        }

        ValueEmitterNode content;
        if (objectNode.getChildren().get(0) instanceof ValueEmitterNode n) {
            content = n;
        } else if (objectNode.getChildren().get(0) instanceof TextNode n) {
            TypeInstance stringType = new Resolver(node.getSourceInfo()).getTypeInstance(Classes.StringType());
            content = new EmitLiteralNode(stringType, n.getText(), n.getSourceInfo());
        } else {
            throw ParserErrors.invalidExpression(objectNode.getChildren().get(0).getSourceInfo());
        }

        if (unchecked(content.getSourceInfo(), () -> !Classes.StringType().subtypeOf(TypeHelper.getJvmType(content)))) {
            throw GeneralErrors.incompatibleValue(
                content.getSourceInfo(), TypeHelper.getTypeInstance(content),
                new Resolver(content.getSourceInfo()).getTypeInstance(Classes.StringType()));
        }

        return createValueOfNode(TypeHelper.getTypeInstance(objectNode), content, node.getSourceInfo());
    }

    private ValueNode createValueOfNode(TypeInstance valueType, ValueEmitterNode content, SourceInfo sourceInfo) {
        Resolver resolver = new Resolver(sourceInfo);
        boolean hasValueOfMethod = resolver.tryResolveValueOfMethod(valueType.jvmType()) != null;

        if (!hasValueOfMethod) {
            TypeInstance supertype = resolver.tryResolveSupertypeWithValueOfMethod(valueType);
            throw ObjectInitializationErrors.valueOfMethodNotFound(
                sourceInfo, valueType.jvmType(), supertype != null ? supertype.jvmType() :  null);
        }

        return EmitObjectNode
            .valueOf(valueType, sourceInfo)
            .value(content)
            .create();
    }

}
