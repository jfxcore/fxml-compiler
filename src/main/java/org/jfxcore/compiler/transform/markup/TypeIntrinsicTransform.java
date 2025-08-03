// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;

/**
 * Transforms the fx:type intrinsic into a class literal.
 */
public class TypeIntrinsicTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!node.typeEquals(ObjectNode.class) || !((ObjectNode)node).isIntrinsic(Intrinsics.TYPE)){
            return node;
        }

        PropertyNode propertyNode = ((ObjectNode)node).getProperty("name");
        Resolver resolver = new Resolver(propertyNode.getTextSourceInfo(context));
        CtClass clazz = resolver.resolveClassAgainstImports(propertyNode.getTextValue(context));
        TypeInstance typeInstance = resolver.getTypeInstance(clazz);

        return new EmitLiteralNode(
            resolver.getTypeInstance(Classes.ClassType(), List.of(typeInstance)),
            clazz.getName(),
            node.getSourceInfo());
    }

}
