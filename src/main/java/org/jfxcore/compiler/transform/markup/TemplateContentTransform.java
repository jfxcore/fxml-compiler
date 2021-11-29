// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.Modifier;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;
import java.util.Set;

public class TemplateContentTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(DefaultPropertyTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode) || Classes.Core.TemplateType() == null) {
            return node;
        }

        PropertyNode contentPropertyNode = context.getParent().as(PropertyNode.class);
        if (contentPropertyNode == null) {
            return node;
        }

        ObjectNode parentNode = context.getParent(1).as(ObjectNode.class);
        if (parentNode == null) {
            return node;
        }

        TypeInstance parentType = TypeHelper.getTypeInstance(parentNode);
        if (!parentType.subtypeOf(Classes.Core.TemplateType())) {
            return node;
        }

        SourceInfo sourceInfo = node.getSourceInfo();
        Resolver resolver = new Resolver(sourceInfo);
        Node contentNode = contentPropertyNode.getSingleValue(context);
        TypeInstance assignType = TypeHelper.getTypeInstance(contentNode);

        if (!assignType.subtypeOf(Classes.NodeType())) {
            throw PropertyAssignmentErrors.incompatiblePropertyType(
                contentNode.getSourceInfo(),
                Classes.Core.TemplateType(),
                contentPropertyNode.getName(),
                Classes.NodeType(),
                assignType);
        }

        TypeInstance itemType = resolver.tryFindArgument(parentType, Classes.Core.TemplateType());
        if (itemType == null) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                parentNode.getSourceInfo(), parentType.jvmType(), 1, 0);
        }

        TypeInstance templateContentType = resolver.getTypeInstance(Classes.Core.TemplateContentType(), List.of(itemType));
        ResolvedTypeNode typeNode = new ResolvedTypeNode(templateContentType, sourceInfo);
        TypeInstance contextClass = ExceptionHelper.unchecked(sourceInfo, () -> getContextClass(context, (ObjectNode)node));

        return new TemplateContentNode(typeNode, (ObjectNode)node, itemType, contextClass, node.getSourceInfo());
    }

    private TypeInstance getContextClass(TransformContext context, ObjectNode node) throws Exception {
        CtClass superType = TypeHelper.getJvmType(node);
        String className = NameHelper.getUniqueName(superType.getSimpleName(), node);
        CtClass contextClass = findClass(context, className);

        if (contextClass == null) {
            if (Modifier.isFinal(superType.getModifiers())) {
                throw GeneralErrors.rootClassCannotBeFinal(node.getSourceInfo(), superType);
            }

            contextClass = context.getMarkupClass().jvmType().makeNestedClass(className, true);
            contextClass.setSuperclass(superType);
            contextClass.setModifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL);
        }

        return new Resolver(node.getSourceInfo()).getTypeInstance(contextClass);
    }

    private CtClass findClass(TransformContext context, String name) throws Exception {
        for (CtClass clazz : context.getMarkupClass().jvmType().getNestedClasses()) {
            if (clazz.getSimpleName().equals(name)) {
                return clazz;
            }
        }

        return null;
    }

}
