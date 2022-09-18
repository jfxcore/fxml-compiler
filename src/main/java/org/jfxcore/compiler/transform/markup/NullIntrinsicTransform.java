// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

/**
 * Transforms the fx:null intrinsic into a null literal.
 */
public class NullIntrinsicTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(Intrinsics.NULL)){
            return node;
        }

        ObjectNode objectNode = (ObjectNode)node;
        PropertyNode propertyNode = context.getParent().as(PropertyNode.class);
        if (propertyNode == null) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), objectNode.getType().getMarkupName());
        }

        TypeInstance parentType = TypeHelper.getTypeInstance(context.getParent(1));
        Resolver resolver = new Resolver(propertyNode.getSourceInfo());
        PropertyInfo propertyInfo = resolver.resolveProperty(
            parentType, propertyNode.isAllowQualifiedName(), propertyNode.getNames());
        TypeInstance valueType = propertyInfo.getValueTypeInstance();

        if (valueType.isPrimitive()) {
            throw PropertyAssignmentErrors.incompatiblePropertyType(
                node.getSourceInfo(), propertyInfo, null);
        }

        return new EmitLiteralNode(valueType, null, node.getSourceInfo());
    }

}
