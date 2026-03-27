// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

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
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class TemplateContentTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode) || Core.TemplateDecl() == null) {
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
        if (!parentType.subtypeOf(Core.TemplateDecl())) {
            return node;
        }

        SourceInfo sourceInfo = node.getSourceInfo();
        Resolver resolver = new Resolver(sourceInfo);
        Node contentNode = contentPropertyNode.getSingleValue(context);
        TypeInstance assignType = TypeHelper.getTypeInstance(contentNode);

        if (!assignType.subtypeOf(NodeDecl())) {
            throw PropertyAssignmentErrors.incompatiblePropertyType(
                contentNode.getSourceInfo(),
                Core.TemplateDecl(),
                contentPropertyNode.getName(),
                NodeDecl(),
                assignType);
        }

        TypeInstance itemType = resolver.tryFindArgument(parentType, Core.TemplateDecl());
        if (itemType == null) {
            throw GeneralErrors.numTypeArgumentsMismatch(
                parentNode.getSourceInfo(), parentType.declaration(), 1, 0);
        }

        TypeInvoker invoker = new TypeInvoker(sourceInfo);
        TypeInstance templateContentType = invoker.invokeType(Core.TemplateContentDecl(), List.of(itemType));
        ResolvedTypeNode typeNode = new ResolvedTypeNode(templateContentType, sourceInfo);
        TypeInstance contextClass = ExceptionHelper.unchecked(sourceInfo, () -> getContextClass(context, (ObjectNode)node));

        return new TemplateContentNode(typeNode, (ObjectNode)node, itemType, contextClass, node.getSourceInfo());
    }

    private TypeInstance getContextClass(TransformContext context, ObjectNode node) {
        TypeDeclaration superType = TypeHelper.getTypeDeclaration(node);
        String className = NameHelper.getUniqueName(superType.simpleName(), node);
        TypeDeclaration contextClass = findClass(context, className);

        if (contextClass == null) {
            if (superType.isFinal()) {
                throw GeneralErrors.rootClassCannotBeFinal(node.getSourceInfo(), superType);
            }

            contextClass = context.getMarkupClass().createNestedClass(className);
            contextClass.setSuperClass(superType);
            contextClass.setModifiers(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL);
        }

        return new TypeInvoker(node.getSourceInfo()).invokeType(contextClass);
    }

    private TypeDeclaration findClass(TransformContext context, String name) {
        for (TypeDeclaration clazz : context.getMarkupClass().nestedClasses()) {
            if (clazz.simpleName().equals(name)) {
                return clazz;
            }
        }

        return null;
    }
}
