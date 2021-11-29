// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.bytecode.annotation.Annotation;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsic;
import org.jfxcore.compiler.ast.intrinsic.IntrinsicProperty;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.common.ResolveTypeTransform;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * If an {@link ObjectNode} contains child nodes, this transform tries to look up the default
 * property of the class as indicated by the {@link javafx.beans.DefaultProperty} annotation.
 * If a default property annotation exists, the child nodes are added to the default property.
 */
public class DefaultPropertyTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(ResolveTypeTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        int numChildren = objectNode.getChildren().size();
        if (numChildren == 0) {
            return objectNode;
        }

        String defaultProperty;
        Intrinsic intrinsic = Intrinsics.find(objectNode);

        if (intrinsic != null) {
            if (!intrinsic.getUsage().isAttribute()) {
                IntrinsicProperty intrinsicDefaultProperty = intrinsic.getDefaultProperty();
                defaultProperty = intrinsicDefaultProperty != null ? intrinsicDefaultProperty.getName() : null;
            } else {
                defaultProperty = null;
            }
        } else {
            CtClass typeClass = ((ResolvedTypeNode)objectNode.getType()).getJvmType();
            Annotation defaultPropertyAnnotation = new Resolver(
                objectNode.getSourceInfo()).tryResolveClassAnnotation(typeClass, Classes.DefaultPropertyAnnotationName);
            defaultProperty = defaultPropertyAnnotation != null ?
                TypeHelper.getAnnotationString(defaultPropertyAnnotation, "value") : null;
        }

        if (defaultProperty == null) {
            return node;
        }

        if (objectNode.getProperties().stream().anyMatch(p -> p.getName().equals(defaultProperty))) {
            throw PropertyAssignmentErrors.duplicateProperty(
                objectNode.getSourceInfo(), objectNode.getType().getMarkupName(), defaultProperty);
        }

        List<ValueNode> children = new ArrayList<>(numChildren);
        Iterator<Node> it = objectNode.getChildren().iterator();
        while (it.hasNext()) {
            Node child = it.next();
            children.add((ValueNode)child);
            it.remove();
        }

        objectNode.getProperties().add(
            new PropertyNode(
                new String[] {defaultProperty},
                defaultProperty,
                children,
                false,
                objectNode.getSourceInfo()));

        return objectNode;
    }

}
