// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Transforms the fx:value element into an {@link EmitObjectNode}.
 * Note: this transform doesn't apply to the fx:value property, which is handled by {@link ObjectTransform}.
 */
public class ValueIntrinsicTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(DefaultPropertyTransform.class);
    }

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

        TypeInstance parentType = TypeHelper.getTypeInstance(context.getParent(1));
        Resolver resolver = new Resolver(propertyNode.getSourceInfo());
        PropertyInfo propertyInfo = resolver.resolveProperty(parentType, propertyNode.getName());
        TypeInstance valueType = propertyInfo.getValueTypeInstance();
        String textValue = objectNode.getTextContent().getText();

        return createValueOfNode(valueType, textValue, node.getSourceInfo());
    }

    private ValueNode createValueOfNode(TypeInstance valueType, String textValue, SourceInfo sourceInfo) {
        Resolver resolver = new Resolver(sourceInfo);
        boolean hasValueOfMethod = resolver.tryResolveValueOfMethod(valueType.jvmType()) != null;

        if (!hasValueOfMethod) {
            TypeInstance supertype = resolver.tryResolveSupertypeWithValueOfMethod(valueType);
            throw ObjectInitializationErrors.valueOfMethodNotFound(
                sourceInfo, valueType.jvmType(), supertype != null ? supertype.jvmType() :  null);
        }

        return new EmitObjectNode(
            null,
            valueType,
            null,
            List.of(new EmitLiteralNode(resolver.getTypeInstance(Classes.StringType()), textValue, sourceInfo)),
            Collections.emptyList(),
            EmitObjectNode.CreateKind.VALUE_OF,
            sourceInfo);
    }

}
