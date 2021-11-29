// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.common.ResolveTypeTransform;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Set;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

/**
 * Replaces an &lt;fx:define> property with &lt;properties>.
 * If the &lt;fx:define> property is not declared on a {@link javafx.scene.Node} element, a diagnostic is generated.
 */
public class DefineBlockTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(ResolveTypeTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof PropertyNode propertyNode) || !propertyNode.isIntrinsic(Intrinsics.DEFINE)) {
            return node;
        }

        CtClass parentType = TypeHelper.getJvmType(context.getParent());

        if (unchecked(node.getSourceInfo(), () -> !parentType.subtypeOf(Classes.NodeType()))) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), Intrinsics.DEFINE.getName());
        }

        return new PropertyNode(
            new String[] {"properties"},
            propertyNode.getMarkupName(),
            propertyNode.getValues(),
            false,
            propertyNode.getSourceInfo());
    }

}
