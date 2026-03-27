// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;

import static org.jfxcore.compiler.type.TypeSymbols.*;

/**
 * Replaces a {@code <fx:define>} property with {@code <properties>}.
 * If the {@code <fx:define>} property is not declared on a {@link javafx.scene.Node} element,
 * a diagnostic is generated.
 */
public class DefineBlockTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof PropertyNode propertyNode) || !propertyNode.isIntrinsic(Intrinsics.DEFINE)) {
            return node;
        }

        TypeDeclaration parentType = TypeHelper.getTypeDeclaration(context.getParent());

        if (!parentType.subtypeOf(NodeDecl())) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), Intrinsics.DEFINE.getName());
        }

        return new PropertyNode(
            new String[] {"properties"},
            propertyNode.getMarkupName(),
            propertyNode.getValues(),
            false,
            false,
            propertyNode.getSourceInfo());
    }
}
