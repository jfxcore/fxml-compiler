// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import java.util.List;

public class TypeHelper {

    public static List<TypeInstance> getTypeArguments(TypeInstance type, TypeDeclaration targetType) {
        if (type.equals(targetType)) {
            return type.arguments();
        }

        for (TypeInstance superType : type.superTypes()) {
            List<TypeInstance> arguments = getTypeArguments(superType, targetType);
            if (!arguments.isEmpty()) {
                return arguments;
            }
        }

        return List.of();
    }

    public static TypeInstance getTypeInstance(Node node) {
        if (!(node instanceof ValueNode)) {
            throw new RuntimeException("Expected " + ValueNode.class.getSimpleName());
        }

        TypeNode typeNode = ((ValueNode)node).getType();
        if (!(typeNode instanceof ResolvedTypeNode)) {
            throw new RuntimeException("Expected " + ResolvedTypeNode.class.getSimpleName());
        }

        return ((ResolvedTypeNode)typeNode).getTypeInstance();
    }

    public static TypeDeclaration getTypeDeclaration(Node node) {
        return getTypeInstance(node).declaration();
    }
}
