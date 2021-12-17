// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.emit.EmitUrlNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Set;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

public class UrlIntrinsicTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(BindingTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(Intrinsics.URL)){
            return node;
        }

        Resolver resolver = new Resolver(node.getSourceInfo());
        PropertyNode value = ((ObjectNode)node).getProperty("value");
        PropertyNode property = context.getParent().as(PropertyNode.class);
        if (property == null) {
            return new EmitUrlNode(
                value.getTextValueNotEmpty(context),
                resolver.getTypeInstance(Classes.URLType()),
                node.getSourceInfo());
        }

        ObjectNode parentObject = (ObjectNode)context.getParent(property);
        PropertyInfo propertyInfo = resolver.resolveProperty(TypeHelper.getTypeInstance(parentObject), property.getName());

        TypeInstance targetType;
        TypeInstance propertyValueType;

        if (propertyInfo.getValueTypeInstance().subtypeOf(Classes.CollectionType())) {
            propertyValueType = resolver.tryFindArgument(propertyInfo.getValueTypeInstance(), Classes.CollectionType());
        } else {
            propertyValueType = propertyInfo.getValueTypeInstance();
        }

        if (unchecked(node.getSourceInfo(), () -> propertyValueType.subtypeOf(Classes.URLType()))) {
            targetType = resolver.getTypeInstance(Classes.URLType());
        } else if (unchecked(node.getSourceInfo(), () -> propertyValueType.subtypeOf(Classes.URIType()))) {
            targetType = resolver.getTypeInstance(Classes.URIType());
        } else if (unchecked(node.getSourceInfo(), () -> propertyValueType.subtypeOf(Classes.StringType()))) {
            targetType = resolver.getTypeInstance(Classes.StringType());
        } else {
            throw PropertyAssignmentErrors.incompatiblePropertyType(
                node.getSourceInfo(), propertyInfo, resolver.getTypeInstance(Classes.URLType()));
        }

        return new EmitUrlNode(value.getTextValueNotEmpty(context), targetType, node.getSourceInfo());
    }

}
