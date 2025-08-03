// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.emit.EmitResourceNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.util.ExceptionHelper.*;

public class ResourceIntrinsicTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(Intrinsics.RESOURCE)){
            return node;
        }

        Resolver resolver = new Resolver(node.getSourceInfo());
        PropertyNode name = ((ObjectNode)node).getProperty("name");
        PropertyNode property = context.getParent().as(PropertyNode.class);
        if (property == null || property.isIntrinsic()) {
            return new EmitResourceNode(
                name.getTrimmedTextNotEmpty(context),
                resolver.getTypeInstance(Classes.URLType()),
                node.getSourceInfo());
        }

        ObjectNode parentObject = (ObjectNode)context.getParent(property);
        PropertyInfo propertyInfo = resolver.resolveProperty(
            TypeHelper.getTypeInstance(parentObject), property.isAllowQualifiedName(), property.getNames());

        TypeInstance targetType;
        TypeInstance propertyValueType;

        if (propertyInfo.getType().subtypeOf(Classes.CollectionType())) {
            propertyValueType = resolver.tryFindArgument(propertyInfo.getType(), Classes.CollectionType());
        } else {
            propertyValueType = propertyInfo.getType();
        }

        if (unchecked(node.getSourceInfo(), () -> propertyValueType.subtypeOf(Classes.URLType()))) {
            targetType = resolver.getTypeInstance(Classes.URLType());
        } else if (unchecked(node.getSourceInfo(), () -> propertyValueType.subtypeOf(Classes.URIType()))) {
            targetType = resolver.getTypeInstance(Classes.URIType());
        } else if (unchecked(node.getSourceInfo(), () -> propertyValueType.subtypeOf(Classes.StringType()))) {
            targetType = TypeInstance.StringType();
        } else {
            throw PropertyAssignmentErrors.incompatiblePropertyType(
                node.getSourceInfo(), propertyInfo, resolver.getTypeInstance(Classes.URLType()));
        }

        return new EmitResourceNode(name.getTrimmedTextNotEmpty(context), targetType, node.getSourceInfo());
    }

}
