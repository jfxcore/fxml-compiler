// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import java.util.List;

import static org.jfxcore.compiler.type.TypeSymbols.*;

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
        SourceInfo sourceInfo = propertyNode.getTrimmedTextSourceInfo(context);
        Resolver resolver = new Resolver(sourceInfo);
        TypeInvoker invoker = new TypeInvoker(sourceInfo);
        TypeDeclaration clazz = resolver.resolveClassAgainstImports(propertyNode.getTrimmedTextNotEmpty(context));
        TypeInstance typeInstance = invoker.invokeType(clazz);

        return new EmitLiteralNode(
            invoker.invokeType(ClassDecl(), List.of(typeInstance)),
            clazz.name(),
            node.getSourceInfo());
    }
}
