// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.Arrays;

/**
 * FXML allows element values to refer to properties of the parent element:
 *     <pre>{@code
 *         <Button>
 *             <text>Hello!</text>
 *         </Button>
 *     }</pre>
 *
 * In this example, {@code <text>} looks like the name of a class, but we want it to be
 * interpreted as a property of the Button class. This transform converts all object nodes
 * that can be interpreted as properties on the parent element to property nodes.
 * <p>
 * Note that this transform only applies to unresolved elements.
 * If {@code <text>} was the name of a resolvable class, then this transform does not replace the
 * object node with a property node. In this situation, users will have to qualify the
 * property name with the name of the class on which it is defined.
 */
public class ObjectToPropertyTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode.getType().isIntrinsic() ?
                tryConvertIntrinsicObjectToProperty(context, objectNode) :
                tryConvertObjectToProperty(context, objectNode);
        }

        return node;
    }

    private Node tryConvertObjectToProperty(TransformContext context, ObjectNode objectNode) {
        if (!objectNode.getType().typeEquals(TypeNode.class) || objectNode.getProperties().size() > 0) {
            return objectNode;
        }

        ObjectNode parentNode = context.getParent().as(ObjectNode.class);
        if (parentNode == null || !(parentNode.getType() instanceof ResolvedTypeNode)) {
            return objectNode;
        }

        String propertyName = objectNode.getType().getName();
        String[] propertyNames = new String[] {propertyName};
        Intrinsic intrinsic = Intrinsics.find(parentNode);

        if (intrinsic == null) {
            propertyNames = propertyName.split("\\.");
            TypeInstance parentType = TypeHelper.getTypeInstance(parentNode);
            Resolver resolver = new Resolver(objectNode.getSourceInfo());
            PropertyInfo propertyInfo = null;
            String[] names = propertyNames;

            // For chained properties like "ListView.selectionModel.selectionMode", we need to resolve all
            // sub-chains ("ListView", "ListView.selectionModel", "ListView.selectionModel.selectionMode").
            // If any sub-chain can be interpreted as a property on the parent type, we convert the
            // ObjectNode to a PropertyNode.
            while (propertyInfo == null && names.length > 0) {
                propertyInfo = resolver.tryResolveProperty(parentType, true, names);
                names = Arrays.copyOf(names, names.length - 1);

                if (propertyInfo == null && names.length > 0) {
                    CtClass type = resolver.tryResolveClassAgainstImports(String.join(".", names));
                    if (type != null && parentType.subtypeOf(type)) {
                        throw SymbolResolutionErrors.propertyNotFound(
                            objectNode.getType().getSourceInfo(), type, propertyNames[names.length]);
                    }
                }
            }

            // If we have no match for the property at this point, the property name could also be the
            // name of a static property on another class.
            if (propertyInfo == null && propertyNames.length > 1) {
                names = Arrays.copyOf(propertyNames, propertyNames.length - 1);
                String staticPropertyClassName = String.join(".", names);
                CtClass staticPropertyClass = resolver.tryResolveClassAgainstImports(staticPropertyClassName);
                if (staticPropertyClass != null) {
                    String staticPropertyName = propertyNames[propertyNames.length - 1];
                    propertyInfo = resolver.tryResolveProperty(
                        resolver.getTypeInstance(staticPropertyClass), false, staticPropertyName);
                }
            }

            if (propertyInfo == null && !TypeHelper.hasNamedConstructorParam(parentType.jvmType(), propertyName)) {
                return objectNode;
            }
        } else if (intrinsic.getProperties().stream().noneMatch(p -> p.getName().equals(propertyName))) {
            return objectNode;
        }

        return new PropertyNode(
            propertyNames,
            objectNode.getType().getMarkupName(),
            objectNode.getChildren(),
            false,
            propertyNames.length > 1,
            objectNode.getSourceInfo());
    }

    private Node tryConvertIntrinsicObjectToProperty(TransformContext context, ObjectNode objectNode) {
        Intrinsic intrinsic = Intrinsics.find(objectNode);
        if (intrinsic == null || context.getParent() instanceof PropertyNode) {
            return objectNode;
        }

        ObjectNode parentNode = context.getParent().as(ObjectNode.class);
        if (parentNode == null || parentNode.getType().isIntrinsic()) {
            return objectNode;
        }

        return new PropertyNode(
            new String[] {objectNode.getType().getName()},
            objectNode.getType().getMarkupName(),
            objectNode.getChildren(),
            true,
            false,
            objectNode.getSourceInfo());
    }

}
