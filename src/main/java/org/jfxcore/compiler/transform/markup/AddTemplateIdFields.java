// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import java.lang.reflect.Modifier;

public class AddTemplateIdFields implements Transform {

    @Override
    @SuppressWarnings("ConstantConditions")
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode) || !context.isTemplate()) {
            return node;
        }

        PropertyNode idNode = objectNode.findIntrinsicProperty(Intrinsics.ID);
        if (idNode == null) {
            return node;
        }

        if (context.getParent() instanceof TemplateContentNode) {
            throw GeneralErrors.unexpectedIntrinsic(idNode.getSourceInfo(), idNode.getMarkupName());
        }

        String id = idNode.getTrimmedTextNotEmpty(context);

        if (context.getIds().contains(id)) {
            throw GeneralErrors.duplicateId(idNode.getTrimmedTextSourceInfo(context), id);
        }

        context.getIds().add(id);

        TypeDeclaration bindingContextClass = context.getCodeBehindOrMarkupClass();

        for (FieldDeclaration field : bindingContextClass.fields()) {
            if (field.name().equals(id)) {
                throw GeneralErrors.invalidId(idNode.getSourceInfo(), id);
            }
        }

        bindingContextClass.createField(id, TypeHelper.getTypeDeclaration(objectNode))
                           .setModifiers(Modifier.PUBLIC);

        return node;
    }
}
