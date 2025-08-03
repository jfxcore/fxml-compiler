// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.EmitUnwrapObservableNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.parse.TypeParser;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.markup.util.AdderFactory;
import org.jfxcore.compiler.transform.markup.util.ValueEmitterFactory;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.PropertyHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
                .loadRoot(TypeHelper.getTypeInstance(objectNode), objectNode.getSourceInfo())
                .children(PropertyHelper.getSorted(objectNode, objectNode.getProperties()))
                .create();
        }

        // Check that we don't have wildcard type arguments, since the JLS doesn't allow
        // direct instantiation of wildcard types.
        for (TypeInstance typeArg : TypeHelper.getTypeInstance(objectNode).getArguments()) {
            if (typeArg.getWildcardType() != TypeInstance.WildcardType.NONE) {
                throw ObjectInitializationErrors.wildcardCannotBeInstantiated(
                    (SourceInfo)objectNode.getNodeData(NodeDataKey.TYPE_ARGUMENTS_SOURCE_INFO));
            }
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

            if (objectNode.getNodeData(NodeDataKey.CONSTRUCTOR_ARGUMENT) == Boolean.TRUE) {
                emitObjectNode.setNodeData(NodeDataKey.CONSTRUCTOR_ARGUMENT, true);
            }
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
            return createValueOfNode(context, node, valueNode);
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

        newObjectNode = ValueEmitterFactory.newObjectByCoercion(node);
        if (newObjectNode != null) {
            return newObjectNode;
        }

        // Check whether all properties specified in FXML for the node are JavaFX properties on the declared
        // class. If this is the case, and we have a default constructor, we can create the object using the
        // default constructor and later set all properties in PropertyAssignmentTransform.
        boolean allPropertiesMatch = node.getProperties().stream().allMatch(propertyNode ->
            new Resolver(propertyNode.getSourceInfo()).tryResolveProperty(
                TypeHelper.getTypeInstance(node), false, propertyNode.getNames()) != null);

        if (allPropertiesMatch) {
            newObjectNode = ValueEmitterFactory.newDefaultObject(node);
            if (newObjectNode != null) {
                return newObjectNode;
            }
        }

        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        MarkupException namedArgsException = null;

        try {
            // If we either don't have a default constructor, or we can't set all properties (because some
            // of the properties are actually named arguments instead of JavaFX properties), we need to
            // see whether we can create the object using named arguments.
            newObjectNode = ValueEmitterFactory.newObjectWithNamedParams(context, node, diagnostics);
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
            TransformContext context, ObjectNode objectNode, PropertyNode propertyNode) {
        TypeInstance nodeType = TypeHelper.getTypeInstance(objectNode);

        if (!objectNode.getChildren().isEmpty()) {
            throw ObjectInitializationErrors.objectCannotHaveContent(
                propertyNode.getSourceInfo(), nodeType.jvmType(), propertyNode.getMarkupName());
        }

        Node propertyValue = propertyNode.getSingleValue(context);
        ValueEmitterNode argumentValue;

        if (propertyValue instanceof BindingNode bindingNode) {
            if (bindingNode.getMode() != BindingMode.ONCE) {
                throw GeneralErrors.expressionNotApplicable(bindingNode.getSourceInfo(), true);
            }

            BindingEmitterInfo emitterInfo = bindingNode.toEmitter(nodeType, null);
            argumentValue = emitterInfo.getObservableType() != null
                ? new EmitUnwrapObservableNode(emitterInfo.getValue())
                : emitterInfo.getValue();
        } else if (propertyValue instanceof ValueEmitterNode valueEmitterNode) {
            argumentValue = valueEmitterNode;
        } else if (propertyValue instanceof ValueNode valueNode
                   && transform(context, valueNode) instanceof ValueEmitterNode valueEmitterNode) {
            argumentValue = valueEmitterNode;
        } else if (propertyValue instanceof TextNode textNode) {
            argumentValue = new EmitLiteralNode(TypeInstance.StringType(), textNode.getText(), textNode.getSourceInfo());
        } else {
            throw GeneralErrors.expressionNotApplicable(propertyValue.getSourceInfo(), false);
        }

        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        CtMethod valueOfMethod = new MethodFinder(nodeType, nodeType.jvmType())
            .findMethod("valueOf", true, nodeType, List.of(), List.of(TypeHelper.getTypeInstance(argumentValue)),
                        List.of(propertyNode.getSourceInfo()), diagnostics, propertyNode.getSourceInfo());

        if (valueOfMethod == null) {
            throw ObjectInitializationErrors.valueOfMethodNotFound(
                propertyNode.getSourceInfo(), nodeType.jvmType(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        } else {
            AccessVerifier.verifyAccessible(valueOfMethod, context.getMarkupClass(), propertyNode.getSourceInfo());
        }

        return EmitObjectNode
            .valueOf(nodeType, valueOfMethod, propertyNode.getSourceInfo())
            .value(argumentValue)
            .children(
                Stream.concat(objectNode.getChildren().stream(), objectNode.getProperties().stream())
                      .collect(Collectors.toList()))
            .backingField(findAndRemoveId(context, objectNode))
            .create();
    }

    private ValueNode createConstantNode(
            TransformContext context, ObjectNode objectNode, PropertyNode constantProperty) {
        SourceInfo valueSourceInfo = constantProperty.getSingleValue(context).getSourceInfo();
        CtClass declaringType = (CtClass)objectNode.getNodeData(NodeDataKey.CONSTANT_DECLARING_TYPE);
        String fieldName = constantProperty.getTextValueNotEmpty(context);

        try {
            CtField field = declaringType.getField(fieldName);
            AccessVerifier.verifyAccessible(field, context.getMarkupClass(), valueSourceInfo);
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.memberNotFound(valueSourceInfo, declaringType, fieldName);
        }

        return new EmitClassConstantNode(
            findAndRemoveId(context, objectNode),
            TypeHelper.getTypeInstance(objectNode),
            declaringType,
            fieldName,
            constantProperty.getSourceInfo());
    }

    private ValueNode createFactoryNode(
            TransformContext context, ObjectNode objectNode, PropertyNode factoryProperty) {
        Node factoryMethodNode = factoryProperty.getSingleValue(context);
        String factoryMethodName = factoryProperty.getTextValueNotEmpty(context);
        TypeParser typeParser = new TypeParser(factoryMethodName, factoryMethodNode.getSourceInfo().getStart());
        TypeParser.MethodInfo methodInfo = typeParser.parseMethod();
        CtClass declaringClass = (CtClass)objectNode.getType().getNodeData(NodeDataKey.FACTORY_DECLARING_TYPE);
        CtMethod factoryMethod = new Resolver(methodInfo.sourceInfo()).tryResolveMethod(declaringClass, m ->
            m.getName().equals(methodInfo.methodName())
            && unchecked(methodInfo.sourceInfo(), m::getParameterTypes).length == 0);

        if (factoryMethod == null) {
            throw SymbolResolutionErrors.memberNotFound(
                factoryProperty.getSourceInfo(), declaringClass, methodInfo.methodName());
        }

        if (!Modifier.isStatic(factoryMethod.getModifiers())) {
            throw SymbolResolutionErrors.instanceMemberReferencedFromStaticContext(
                methodInfo.sourceInfo(), factoryMethod);
        }

        AccessVerifier.verifyAccessible(factoryMethod, context.getMarkupClass(), methodInfo.sourceInfo());

        return EmitObjectNode
            .factory(
                TypeHelper.getTypeInstance(objectNode),
                factoryMethod,
                methodInfo.sourceInfo())
            .children(objectNode.getProperties().stream().filter(p -> !p.isIntrinsic(Intrinsics.FACTORY)).toList())
            .backingField(findAndRemoveId(context, objectNode))
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
