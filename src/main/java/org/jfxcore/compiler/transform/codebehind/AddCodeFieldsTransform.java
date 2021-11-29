// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.codebehind;

import javassist.Modifier;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.codebehind.AddCodeFieldNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;

/**
 * For every object in the AST that has a fx:id property, adds a corresponding {@link AddCodeFieldNode}
 * to the root element. Templates are skipped.
 */
public class AddCodeFieldsTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        ObjectNode parentNode = context.getParent() != null ? context.getParent().as(ObjectNode.class) : null;
        if (parentNode != null
                && Classes.Core.TemplateType() != null
                && parentNode.getType() instanceof ResolvedTypeNode
                && ((ResolvedTypeNode)parentNode.getType()).getTypeInstance().subtypeOf(Classes.Core.TemplateType())) {
            return Visitor.STOP_SUBTREE;
        }

        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        PropertyNode idNode = objectNode.findIntrinsicProperty(Intrinsics.ID);
        if (idNode == null) {
            return node;
        }

        if (context.getParent() instanceof DocumentNode) {
            throw GeneralErrors.unexpectedIntrinsic(idNode.getSourceInfo(), idNode.getMarkupName());
        }

        String id = idNode.getTextValueNotEmpty(context);

        if (context.getIds().contains(id)) {
            throw GeneralErrors.duplicateId(idNode.getSourceInfo(), id);
        }

        context.getIds().add(id);

        ObjectNode root = (ObjectNode)context.getDocument().getRoot();
        TextNode valueNode = new TextNode(objectNode.getType().getMarkupName(), idNode.getSourceInfo());
        root.getProperties().add(new AddCodeFieldNode(id, valueNode, Modifier.PROTECTED, idNode.getSourceInfo()));

        return node;
    }

}
