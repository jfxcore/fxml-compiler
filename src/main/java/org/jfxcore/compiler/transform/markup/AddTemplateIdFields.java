// Copyright (c) 2021, 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.TypeHelper;

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

        String id = idNode.getTextValueNotEmpty(context);

        if (context.getIds().contains(id)) {
            throw GeneralErrors.duplicateId(idNode.getSingleValue(context).getSourceInfo(), id);
        }

        context.getIds().add(id);

        ExceptionHelper.unchecked(
            idNode.getSourceInfo(), () -> addField(
                context.getBindingContextClass(), TypeHelper.getJvmType(objectNode),
                id, idNode.getSourceInfo()));

        return node;
    }

    private void addField(CtClass bindingContextClass, CtClass fieldType, String fieldName, SourceInfo sourceInfo)
            throws Exception {
        for (CtField field : bindingContextClass.getFields()) {
            if (field.getName().equals(fieldName)) {
                throw GeneralErrors.invalidId(sourceInfo, fieldName);
            }
        }

        CtField field = new CtField(fieldType, fieldName, bindingContextClass);
        field.setModifiers(Modifier.PUBLIC);
        bindingContextClass.addField(field);
    }

}
