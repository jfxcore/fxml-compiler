// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.markup.util.AdderFactory;
import org.jfxcore.compiler.transform.markup.util.ValueEmitterFactory;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.PropertyHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jfxcore.compiler.util.ExceptionHelper.*;

/**
 * Replaces all instances of {@link ObjectNode} in the AST with a node that represents one of the following:
 *     1. reference to an existing object ({@link EmitClassConstantNode})
 *     2. instantiation of a new object ({@link EmitObjectNode})
 */
public class ObjectTransform implements Transform {

    @Override
    public Set<Class<? extends Transform>> getDependsOn() {
        return Set.of(
            AddTemplateIdFields.class,
            IdPropertyTransform.class,
            DefaultPropertyTransform.class,
            DefineBlockTransform.class,
            StylesheetTransform.class,
            NullIntrinsicTransform.class,
            TypeIntrinsicTransform.class,
            ConstantIntrinsicTransform.class,
            ValueIntrinsicTransform.class,
            UrlIntrinsicTransform.class,
            BindingTransform.class);
    }

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return node;
        }

        if (objectNode.getType().isIntrinsic()) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), objectNode.getType().getMarkupName());
        }

        Node parentNode = context.getParent(0);

        if (parentNode instanceof EmitInitializeRootNode || parentNode instanceof TemplateContentNode) {
            if (objectNode.getChildren().size() > 0) {
                throw ObjectInitializationErrors.objectCannotHaveContent(
                    node.getSourceInfo(), TypeHelper.getJvmType(node));
            }

            return EmitObjectNode
                .rootObject(TypeHelper.getTypeInstance(objectNode), objectNode.getSourceInfo())
                .withChildren(PropertyHelper.getSorted(objectNode, objectNode.getProperties()))
                .create();
        }

        ValueNode result = createNode(context, objectNode);

        // If we created a collection-type or map-type node, we need to add its children
        if (result instanceof EmitObjectNode emitObjectNode) {
            List<Node> adders = new ArrayList<>();

            if (TypeHelper.getTypeInstance(result).subtypeOf(Classes.CollectionType())) {
                ListIterator<Node> it = objectNode.getChildren().listIterator();
                while (it.hasNext()) {
                    if (it.next() instanceof ValueNode child) {
                        adders.addAll(AdderFactory.newCollectionAdders(result, child));
                        it.remove();
                    }
                }
            } else if (TypeHelper.getTypeInstance(result).subtypeOf(Classes.MapType())) {
                ListIterator<Node> it = objectNode.getChildren().listIterator();
                while (it.hasNext()) {
                    if (it.next() instanceof ValueNode child) {
                        adders.add(AdderFactory.newMapAdder(result, child));
                        it.remove();
                    }
                }
            }

            emitObjectNode.getChildren().addAll(adders);
        }

        if (objectNode.getChildren().size() > 0) {
            throw ObjectInitializationErrors.objectCannotHaveContent(
                node.getSourceInfo(), TypeHelper.getJvmType(node));
        }

        return result;
    }

    private ValueNode createNode(TransformContext context, ObjectNode node) {
        PropertyNode valueNode = node.findIntrinsicProperty(Intrinsics.VALUE);
        if (valueNode != null) {
            valueNode.remove();
            return createValueOfNode(context, node, valueNode, valueNode.getTextValue(context));
        }

        PropertyNode constantNode = node.findIntrinsicProperty(Intrinsics.CONSTANT);
        if (constantNode != null) {
            constantNode.remove();
            return createConstantNode(context, node, constantNode);
        }

        PropertyNode factoryNode = node.findIntrinsicProperty(Intrinsics.FACTORY);
        if (factoryNode != null) {
            factoryNode.remove();
            return createFactoryNode(context, node, factoryNode);
        }

        ValueNode newObjectNode = ValueEmitterFactory.newLiteralValue(node);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        MarkupException namedArgsException = null;

        try {
            newObjectNode = ValueEmitterFactory.newObjectWithNamedParams(node, diagnostics);
            if (newObjectNode != null) {
                return newObjectNode;
            }
        } catch (MarkupException ex) {
            // If the expression was not applicable for the constructor (for example, because
            // it was a binding expression), we don't bail out yet. If we don't find a better
            // way to instantiate the object, the exception will be thrown at the end.
            if (ex.getDiagnostic().getCode() == ErrorCode.EXPRESSION_NOT_APPLICABLE) {
                namedArgsException = ex;
            } else {
                throw ex;
            }
        }

        newObjectNode = ValueEmitterFactory.newObjectByCoercion(node);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        newObjectNode = ValueEmitterFactory.newDefaultObject(node);
        if (newObjectNode != null) {
            return newObjectNode;
        } else if (namedArgsException != null) {
            throw namedArgsException;
        }

        if (!diagnostics.isEmpty()) {
            throw ObjectInitializationErrors.constructorNotFound(
                node.getSourceInfo(),
                TypeHelper.getJvmType(node),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        throw ObjectInitializationErrors.constructorNotFound(node.getSourceInfo(), TypeHelper.getJvmType(node));
    }

    private ValueNode createValueOfNode(
            TransformContext context, ObjectNode objectNode, PropertyNode propertyNode, String textValue) {
        TypeInstance nodeType = TypeHelper.getTypeInstance(objectNode);

        if (!objectNode.getChildren().isEmpty()) {
            throw ObjectInitializationErrors.valueOfCannotHaveContent(
                propertyNode.getSourceInfo(), nodeType.jvmType(), propertyNode.getMarkupName());
        }

        Resolver resolver = new Resolver(propertyNode.getSourceInfo());
        boolean hasValueOfMethod = resolver.tryResolveValueOfMethod(nodeType.jvmType()) != null;

        if (!hasValueOfMethod) {
            TypeInstance supertype = resolver.tryResolveSupertypeWithValueOfMethod(nodeType);
            throw ObjectInitializationErrors.valueOfMethodNotFound(
                propertyNode.getSourceInfo(), nodeType.jvmType(), supertype != null ? supertype.jvmType() :  null);
        }

        return EmitObjectNode
            .valueOf(nodeType, propertyNode.getSourceInfo())
            .withTextValue(textValue)
            .withChildren(
                Stream.concat(objectNode.getChildren().stream(), objectNode.getProperties().stream())
                      .collect(Collectors.toList()))
            .storeInField(findAndRemoveId(context, objectNode))
            .create();
    }

    private ValueNode createConstantNode(
            TransformContext context, ObjectNode objectNode, PropertyNode constantProperty) {
        if (objectNode.getProperties().stream().anyMatch(
                p -> !p.isIntrinsic(Intrinsics.ID) && !p.isIntrinsic(Intrinsics.CONSTANT))) {
            throw ObjectInitializationErrors.constantCannotBeModified(constantProperty.getSourceInfo());
        }

        CtClass declaringType;
        String[] segments = constantProperty.getTextValueNotEmpty(context).split("\\.");

        if (segments.length == 1) {
            declaringType = TypeHelper.getJvmType(objectNode);
        } else if (segments.length == 2) {
            declaringType = new Resolver(objectNode.getSourceInfo()).resolveClassAgainstImports(segments[0]);
        } else {
            String className = Arrays.stream(segments)
                .limit(segments.length - 1)
                .collect(Collectors.joining("."));

            declaringType = new Resolver(objectNode.getSourceInfo()).resolveClass(className);
        }

        String fieldName = segments[segments.length - 1];
        TypeInstance fieldType;

        try {
            Resolver resolver = new Resolver(constantProperty.getSourceInfo());
            fieldType = resolver.getTypeInstance(declaringType.getField(fieldName), Collections.emptyList());
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.memberNotFound(constantProperty.getSourceInfo(), declaringType, fieldName);
        }

        ValueNode constantNode = new EmitClassConstantNode(
            findAndRemoveId(context, objectNode),
            fieldType,
            declaringType,
            fieldName,
            constantProperty.getSourceInfo());

        TypeInstance objectType = TypeHelper.getTypeInstance(objectNode);

        if (!objectType.isAssignableFrom(fieldType)) {
            throw ObjectInitializationErrors.cannotAssignConstant(constantNode.getSourceInfo(), objectType, fieldType);
        }

        return constantNode;
    }

    private ValueNode createFactoryNode(
            TransformContext context, ObjectNode objectNode, PropertyNode factoryProperty) {
        String factoryMethodName = factoryProperty.getTextValueNotEmpty(context);
        Resolver resolver = new Resolver(factoryProperty.getSourceInfo());
        CtClass declaringClass;
        String methodName;

        String[] path = factoryMethodName.split("\\.");
        if (path.length == 1) {
            declaringClass = TypeHelper.getJvmType(objectNode);
            methodName = factoryMethodName;
        } else {
            String className = String.join(".", Arrays.copyOf(path, path.length - 1));
            declaringClass = resolver.resolveClassAgainstImports(className);
            methodName = path[path.length - 1];
        }

        CtMethod factoryMethod = resolver.tryResolveMethod(declaringClass, m -> {
            int modifiers = m.getModifiers();
            return Modifier.isStatic(modifiers)
                && Modifier.isPublic(modifiers)
                && m.getName().equals(methodName)
                && unchecked(factoryProperty.getSourceInfo(), m::getParameterTypes).length == 0;
        });

        if (factoryMethod == null) {
            throw SymbolResolutionErrors.methodNotFound(factoryProperty.getSourceInfo(), declaringClass, methodName);
        }

        return EmitObjectNode
            .factory(
                TypeHelper.getTypeInstance(objectNode),
                factoryMethod,
                factoryProperty.getSourceInfo())
            .storeInField(findAndRemoveId(context, objectNode))
            .withChildren(objectNode.getProperties().stream().filter(p -> !p.isIntrinsic(Intrinsics.FACTORY)).toList())
            .create();
    }

    private String findAndRemoveId(TransformContext context, ObjectNode node) {
        PropertyNode propertyNode = node.findIntrinsicProperty(Intrinsics.ID);
        if (propertyNode != null) {
            propertyNode.remove();
            return propertyNode.getTextValue(context);
        }

        return null;
    }

}
