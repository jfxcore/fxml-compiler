// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

public class PropertyHelper {

    /**
     * Sort all properties such that properties containing scene nodes come before properties that do not.
     */
    public static List<PropertyNode> getSorted(ObjectNode objectNode, List<PropertyNode> properties) {
        List<PropertyNode> first = new ArrayList<>(properties.size());
        List<PropertyNode> last = new ArrayList<>(properties.size());

        for (PropertyNode property : properties) {
            if (property.isIntrinsic()) {
                first.add(property);
                continue;
            }

            Resolver resolver = new Resolver(property.getSourceInfo());
            PropertyInfo propertyInfo = resolver.tryResolveProperty(TypeHelper.getTypeInstance(objectNode), property.getName());
            if (propertyInfo == null) {
                last.add(property);
                continue;
            }

            TypeInstance propertyType = propertyInfo.getValueTypeInstance();

            if (unchecked(property.getSourceInfo(), () -> propertyInfo.getValueType().subtypeOf(Classes.CollectionType()))) {
                List<TypeInstance> typeArguments = resolver.getPropertyTypeArguments(propertyInfo);
                propertyType = typeArguments.size() == 1 ? typeArguments.get(0) : propertyInfo.getValueTypeInstance();
            }

            if (propertyType.subtypeOf(Classes.NodeType())) {
                first.add(property);
            } else {
                last.add(property);
            }
        }

        first.addAll(last);
        return first;
    }

}
