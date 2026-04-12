// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import javafx.scene.paint.Color;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.ast.emit.EmitApplyMarkupExtensionNode;
import org.jfxcore.compiler.ast.emit.EmitArrayNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitGetParentNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.AccessModifier;
import org.jfxcore.compiler.type.AnnotationDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.PropertyHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Creates AST nodes that represent the creation of a new value.
 * This can happen in the form of a new object allocation represented by {@link EmitObjectNode},
 * or in the form of a value literal represented by {@link EmitLiteralNode}.
 */
public class ValueEmitterFactory {

    private static Map<Color, Field> colorFields;

    private static Field findColorField(Color color) {
        if (colorFields == null) {
            colorFields = new HashMap<>();

            for (Field field : Color.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        || !Modifier.isPublic(field.getModifiers())
                        || !Modifier.isFinal(field.getModifiers())
                        || !field.getType().equals(Color.class)) {
                    continue;
                }

                try {
                    colorFields.put((Color)field.get(null), field);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return colorFields.get(color);
    }

    /**
     * Tries to convert the object node into a literal node.
     *
     * @return {@link EmitLiteralNode} if conversion was successful; <code>null</code> otherwise.
     */
    public static ValueEmitterNode newLiteralValue(ObjectNode node) {
        if (node.getChildren().size() != 1 || !(node.getChildren().get(0) instanceof TextNode textNode)) {
            return null;
        }

        ValueEmitterNode result = newLiteralValue(
            findAndRemoveId(node),
            textNode.getText(),
            Collections.emptyList(),
            TypeHelper.getTypeInstance(node),
            node.getSourceInfo());

        if (result != null) {
            node.getChildren().clear();
        }

        return result;
    }

    /**
     * Tries to convert the string value to the target type.
     *
     * @return {@link EmitLiteralNode} if conversion was successful; <code>null</code> otherwise.
     */
    public static ValueEmitterNode newLiteralValue(String value, TypeInstance targetType, SourceInfo sourceInfo) {
        return newLiteralValue(null, value, Collections.emptyList(), targetType, sourceInfo);
    }

    /**
     * Tries to convert the {@link TextNode} to the target type.
     *
     * @return {@link EmitLiteralNode} if conversion was successful; <code>null</code> otherwise.
     */
    public static ValueEmitterNode newLiteralValue(TextNode node, TypeInstance targetType, SourceInfo sourceInfo) {
        return newLiteralValue(null, node.getText(), Collections.emptyList(), targetType, sourceInfo);
    }

    /**
     * Tries to convert the {@link TextNode} to the target type.
     *
     * @return {@link EmitLiteralNode} if conversion was successful; <code>null</code> otherwise.
     */
    public static ValueEmitterNode newLiteralValue(
            TextNode node, List<TypeInstance> declaringTypes, TypeInstance targetType, SourceInfo sourceInfo) {
        return newLiteralValue(null, node.getText(), declaringTypes, targetType, sourceInfo);
    }

    private static ValueEmitterNode newLiteralValue(
            String id, String value, List<TypeInstance> declaringTypes, TypeInstance targetType, SourceInfo sourceInfo) {
        TypeInstance boxedTargetType = targetType.boxed();
        String trimmedValue = value.trim();

        switch (targetType.name()) {
        case "boolean":
        case BooleanName:
            if (trimmedValue.equals("true")) {
                return new EmitLiteralNode(id, TypeInstance.booleanType(), true, sourceInfo);
            } else if (trimmedValue.equals("false")) {
                return new EmitLiteralNode(id, TypeInstance.booleanType(), false, sourceInfo);
            }

            break;
        case "char":
        case CharacterName:
            if (trimmedValue.length() == 1) {
                return new EmitLiteralNode(id, TypeInstance.charType(), trimmedValue.charAt(0), sourceInfo);
            }

            break;
        case "byte":
        case ByteName:
            try {
                return new EmitLiteralNode(id, TypeInstance.byteType(), Byte.parseByte(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "short":
        case ShortName:
            try {
                return new EmitLiteralNode(id, TypeInstance.shortType(), Short.parseShort(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "int":
        case IntegerName:
            try {
                return new EmitLiteralNode(id, TypeInstance.intType(), Integer.parseInt(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "long":
        case LongName:
            try {
                return new EmitLiteralNode(id, TypeInstance.longType(), Long.parseLong(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "float":
        case FloatName:
            try {
                return new EmitLiteralNode(id, TypeInstance.floatType(), Float.parseFloat(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "double":
        case DoubleName:
            try {
                return new EmitLiteralNode(id, TypeInstance.doubleType(), Double.parseDouble(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        }

        if (targetType.declaration().isEnum()) {
            return targetType.declaration().field(trimmedValue)
                .map(field -> new EmitLiteralNode(id, targetType, field, sourceInfo))
                .orElseThrow(() -> SymbolResolutionErrors.memberNotFound(sourceInfo, targetType.declaration(), value));
        }

        if (TypeInstance.of(ColorDecl()).subtypeOf(targetType)) {
            try {
                Color color = Color.valueOf(trimmedValue);
                Field colorField = findColorField(color);

                if (colorField != null) {
                    return new EmitClassConstantNode(
                        id,
                        TypeInstance.of(ColorDecl()),
                        ColorDecl(),
                        colorField.getName(),
                        sourceInfo);
                } else {
                    MethodDeclaration valueOfMethod = new Resolver(sourceInfo).tryResolveMethod(
                        ColorDecl(), m -> "valueOf".equals(m.name()));

                    return EmitObjectNode
                        .valueOf(TypeInstance.of(ColorDecl()), valueOfMethod, sourceInfo)
                        .textValue(trimmedValue)
                        .create();
                }
            } catch (NullPointerException | IllegalArgumentException ex) {
                if (boxedTargetType.subtypeOf(TypeInstance.of(ColorDecl()))) {
                    return null;
                }
            }
        }

        for (TypeInstance declaringType : declaringTypes) {
            // Always use boxed type to support static fields on primitive wrapper classes.
            TypeDeclaration boxedDeclaringType = declaringType.boxed().declaration();

            var resolver = new Resolver(sourceInfo);
            FieldDeclaration field = resolver.tryResolveField(boxedDeclaringType, trimmedValue);

            if (field != null) {
                var fieldType = new TypeInvoker(sourceInfo).invokeFieldType(field, List.of(declaringType));
                if (targetType.isAssignableFrom(fieldType)) {
                    return new EmitClassConstantNode(id, targetType, boxedDeclaringType, field.name(), sourceInfo);
                }
            }
        }

        if (TypeInstance.of(StringDecl()).subtypeOf(targetType)) {
            return new EmitLiteralNode(id, TypeInstance.StringType(), value, sourceInfo);
        }

        return null;
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of the default constructor.
     *
     * @return {@link EmitObjectNode} if successful; <code>null</code> otherwise.
     */
    public static EmitObjectNode newDefaultObject(ObjectNode objectNode) {
        TypeInstance type = TypeHelper.getTypeInstance(objectNode);
        return type.declaration().declaredConstructor()
            .filter(c -> c.accessModifier() == AccessModifier.PUBLIC)
            .map(c -> createObjectNode(objectNode, c, Collections.emptyList()))
            .orElse(null);
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of a constructor
     * where all parameters are annotated with {@link javafx.beans.NamedArg}.
     *
     * @return {@link EmitObjectNode} if successful; <code>null</code> otherwise.
     */
    public static EmitObjectNode newObjectWithNamedParams(
            TransformContext context, ObjectNode objectNode, List<DiagnosticInfo> diagnostics) {
        // Include the current object in the under-initialization count, since the count is
        // relative to the current object's arguments.
        int parentsUnderInitializationCount = getParentsUnderInitializationCount(context) +
            (objectNode.getNodeData(NodeDataKey.CONSTRUCTOR_ARGUMENT) == Boolean.TRUE ? 1 : 0);

        TypeInstance type = TypeHelper.getTypeInstance(objectNode);
        NamedArgsConstructor[] namedArgsConstructors = findNamedArgsConstructors(objectNode, diagnostics);
        List<DiagnosticInfo> constructorDiagnostics = new ArrayList<>();

        for (NamedArgsConstructor namedArgsConstructor : namedArgsConstructors) {
            List<ValueNode> arguments = new ArrayList<>();
            int argIndex = -1;

            for (NamedArgParam constructorParam : namedArgsConstructor.namedArgs()) {
                argIndex++;

                boolean vararg = argIndex == namedArgsConstructor.namedArgs().length - 1
                        && namedArgsConstructor.constructor().isVarArgs();

                PropertyNode propertyNode = objectNode.getProperties().stream()
                    .filter(p -> p.getName().equals(constructorParam.name()))
                    .findFirst()
                    .orElse(null);

                // If an argument was not specified, we synthesize a node if the @NamedArg
                // annotation has a non-null 'defaultValue' parameter.
                if (propertyNode == null && constructorParam.isOptional()) {
                    var textNode = new TextNode(constructorParam.defaultValue(), SourceInfo.none());
                    ValueEmitterNode synthesizedNode = newObjectByCoercion(constructorParam.type(), textNode);
                    if (synthesizedNode == null) {
                        synthesizedNode = newLiteralValue(
                            constructorParam.defaultValue(), constructorParam.type(), SourceInfo.none());
                    }

                    if (synthesizedNode == null) {
                        break;
                    }

                    arguments.add(synthesizedNode);
                }

                AcceptArgumentResult result = null;

                // Check whether the property type is assignable to the corresponding formal parameter
                // of the current constructor.
                if (propertyNode != null && propertyNode.getValues().size() == 1) {
                    result = acceptArgument(
                        context, propertyNode.getValues().get(0), constructorParam.type(), constructorParam.name(),
                        type, vararg, parentsUnderInitializationCount);

                    if (result instanceof AcceptArgumentResult.Error error
                            && error.errorCode() == ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT
                            && vararg) {
                        result = acceptArrayArgument(
                            context, propertyNode.getValues(), type,
                            constructorParam.type(), parentsUnderInitializationCount);
                    }
                } else if (propertyNode != null && vararg) {
                    result = acceptArrayArgument(
                        context, propertyNode.getValues(), type,
                        constructorParam.type(), parentsUnderInitializationCount);
                } else if (propertyNode != null
                        && propertyNode.getValues().size() > 1
                        && constructorParam.type().isArray()) {
                    result = new AcceptArgumentResult.Error(
                        propertyNode, null, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
                        SourceInfo.span(propertyNode.getValues()));
                }

                if (result instanceof AcceptArgumentResult.Error error) {
                    var namedArgs = namedArgsConstructor.namedArgs();
                    var argTypes = Arrays.stream(namedArgs).map(NamedArgParam::type).toArray(TypeInstance[]::new);
                    var argNames = Arrays.stream(namedArgs).map(NamedArgParam::name).toArray(String[]::new);
                    var methodSignature = NameHelper.getDisplaySignature(
                        namedArgsConstructor.constructor(), argTypes, argNames);

                    switch (error.errorCode()) {
                        case CANNOT_ASSIGN_FUNCTION_ARGUMENT -> {
                            List<Node> values = propertyNode.getValues();
                            Diagnostic diagnostic;

                            if (values.size() > 1) {
                                diagnostic = Diagnostic.newDiagnosticVariant(
                                    ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, "variadic",
                                    methodSignature, propertyNode.getName());
                            } else {
                                String errorTypeString;
                                if (error.errorType() != null) {
                                    errorTypeString = error.errorType().javaName();
                                } else {
                                    errorTypeString = TypeHelper.getTypeInstance(values.get(0)).javaName();
                                }

                                diagnostic = Diagnostic.newDiagnosticVariant(
                                    ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, "named",
                                    methodSignature, propertyNode.getName(), errorTypeString);
                            }

                            constructorDiagnostics.add(new DiagnosticInfo(diagnostic, error.sourceInfo()));
                        }

                        case CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION -> {
                            BindingNode bindingNode = (BindingNode)error.argumentNode();
                            int bindingDistance = bindingNode.getBindingDistance();
                            if (bindingDistance == 0) {
                                constructorDiagnostics.add(
                                    new DiagnosticInfo(
                                        Diagnostic.newDiagnosticVariant(
                                            ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION,
                                            "argument",
                                            methodSignature,
                                            propertyNode.getName()),
                                        error.sourceInfo()));
                            } else {
                                List<Node> parents = context.getParents();
                                Node referencedParent = parents.get(parents.size() - bindingDistance);

                                constructorDiagnostics.add(
                                    new DiagnosticInfo(
                                        Diagnostic.newDiagnosticVariant(
                                            ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION,
                                            "parent",
                                            methodSignature,
                                            propertyNode.getName(),
                                            TypeHelper.getTypeInstance(referencedParent).javaName()),
                                        error.sourceInfo()));
                            }
                        }

                        default -> throw new AssertionError(error.errorCode().toString());
                    }

                    break;
                }

                if (result instanceof AcceptArgumentResult.Success<? extends ValueNode> success) {
                    arguments.add(success.value());
                }
            }

            // If we have arguments for all formal parameters, we can construct the object.
            if (arguments.size() == namedArgsConstructor.namedArgs().length) {
                objectNode.getProperties().removeIf(p ->
                    Arrays.stream(namedArgsConstructor.namedArgs())
                        .anyMatch(p2 -> p2.name().equals(p.getName())));

                return createObjectNode(objectNode, namedArgsConstructor.constructor(), arguments);
            }
        }

        if (!constructorDiagnostics.isEmpty()) {
            throw new MarkupException(
                constructorDiagnostics.get(0).getSourceInfo(),
                constructorDiagnostics.get(0).getDiagnostic());
        }

        return null;
    }

    public static int getParentsUnderInitializationCount(TransformContext context) {
        int depth = 0;
        var it = context.getParents().listIterator(context.getParents().size());

        while (it.hasPrevious()) {
            if (it.previous().getNodeData(NodeDataKey.CONSTRUCTOR_ARGUMENT) != Boolean.TRUE) {
                break;
            }

            ++depth;
        }

        return depth;
    }

    /**
     * Determines whether the input argument is acceptable for the target type and returns
     * a new node that will emit the input argument.
     */
    private static AcceptArgumentResult acceptArgument(
            TransformContext context, Node argumentNode, TypeInstance targetType, String targetName,
            TypeInstance invokingType, boolean vararg, int parentsUnderInitializationCount) {
        SourceInfo sourceInfo = argumentNode.getSourceInfo();
        ValueNode value = null;

        if (argumentNode instanceof BindingNode bindingNode) {
            var result = acceptBindingArgument(bindingNode, invokingType, targetType,
                                               parentsUnderInitializationCount);
            if (result instanceof AcceptArgumentResult.Error) {
                return result;
            } else if (result instanceof AcceptArgumentResult.Success<?> success) {
                value = success.value();
            }
        } else if (argumentNode instanceof ListNode listNode && vararg) {
            var result = acceptArrayArgument(context, listNode.getValues(), invokingType, targetType,
                                             parentsUnderInitializationCount);
            if (result instanceof AcceptArgumentResult.Error) {
                return result;
            } else if (result instanceof AcceptArgumentResult.Success<?> success) {
                value = success.value();
            }
        } else if (argumentNode instanceof TextNode textNode && (!targetType.isArray() || vararg)) {
            value = newObjectByCoercion(targetType, textNode);
            if (value == null) {
                value = newLiteralValue(textNode, List.of(targetType), targetType, sourceInfo);
            }
        } else if (MarkupExtensionInfo.of(argumentNode) instanceof MarkupExtensionInfo.Supplier supplierInfo) {
            var result = acceptSupplierArgument(context, targetType, targetName, (ObjectNode)argumentNode, supplierInfo);
            if (result instanceof AcceptArgumentResult.Error) {
                return result;
            } else if (result instanceof AcceptArgumentResult.Success<?> success) {
                value = success.value();
            }
        } else if (argumentNode instanceof ValueNode valueNode) {
            value = valueNode;
        }

        if (value == null) {
            return new AcceptArgumentResult.Error(argumentNode, null, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT);
        }

        TypeInstance valueType = TypeHelper.getTypeInstance(value);

        return targetType.isAssignableFrom(valueType)
            ? new AcceptArgumentResult.Success<>(value)
            : new AcceptArgumentResult.Error(argumentNode, valueType, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT);
    }

    private static AcceptArgumentResult acceptArrayArgument(TransformContext context,
                                                            Collection<? extends Node> argumentNodes,
                                                            TypeInstance invokingType,
                                                            TypeInstance arrayType,
                                                            int parentsUnderInitializationCount) {
        List<ValueNode> values = new ArrayList<>();

        for (Node item : argumentNodes) {
            var result = acceptArgument(context, item, arrayType.componentType(), null,
                                        invokingType, false, parentsUnderInitializationCount);

            if (result instanceof AcceptArgumentResult.Success<?> success) {
                values.add(success.value());
            } else if (result instanceof AcceptArgumentResult.Error) {
                return result;
            }
        }

        return new AcceptArgumentResult.Success<>(new EmitArrayNode(arrayType, values));
    }

    private static AcceptArgumentResult acceptBindingArgument(BindingNode bindingNode,
                                                              TypeInstance invokingType,
                                                              TypeInstance argumentType,
                                                              int parentsUnderInitializationCount) {
        if (bindingNode.getMode() != BindingMode.ONCE && bindingNode.getMode() != BindingMode.UNIDIRECTIONAL) {
            throw GeneralErrors.expressionNotApplicable(bindingNode.getSourceInfo(), true);
        }

        if (bindingNode.getBindingDistance() <= parentsUnderInitializationCount) {
            return new AcceptArgumentResult.Error(
                bindingNode, null, ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION);
        }

        ValueEmitterNode result = bindingNode.toPathEmitter(invokingType, argumentType).getValue();
        adjustParentIndex(result, parentsUnderInitializationCount + 1);

        return new AcceptArgumentResult.Success<>(result);
    }

    private static AcceptArgumentResult acceptSupplierArgument(TransformContext context,
                                                               TypeInstance targetType,
                                                               String targetName,
                                                               ObjectNode markupExtensionNode,
                                                               MarkupExtensionInfo.Supplier supplierInfo) {
        TypeInstance valueType = TypeHelper.getTypeInstance(markupExtensionNode);

        if (supplierInfo.providedTypes().stream().noneMatch(targetType::isAssignableFrom)) {
            return new AcceptArgumentResult.Error(
                markupExtensionNode, valueType, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT);
        }

        ValueEmitterNode argValueNode = newObjectWithNamedParams(context, markupExtensionNode, new ArrayList<>());
        if (argValueNode == null) {
            return new AcceptArgumentResult.Error(
                markupExtensionNode, valueType, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT);
        }

        return new AcceptArgumentResult.Success<>(new EmitApplyMarkupExtensionNode.Supplier(
                argValueNode, supplierInfo.markupExtensionInterface(), targetName,
                targetType, supplierInfo.returnType(), null));
    }

    private static void adjustParentIndex(ValueNode node, int adjustment) {
        node.accept(new Visitor() {
            @Override
            protected Node onVisited(Node node) {
                if (node instanceof EmitGetParentNode getParentNode) {
                    getParentNode.setParentIndexAdjustment(adjustment);
                }

                return node;
            }
        });
    }

    private static EmitObjectNode createObjectNode(
            ObjectNode objectNode, ConstructorDeclaration constructor, List<ValueNode> arguments) {
        return EmitObjectNode
            .constructor(TypeHelper.getTypeInstance(objectNode), constructor, arguments, objectNode.getSourceInfo())
            .children(PropertyHelper.getSorted(objectNode, objectNode.getProperties()))
            .backingField(findAndRemoveId(objectNode))
            .create();
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of a constructor where,
     * given a list of string literals, all literals can be coerced to their respective constructor parameters
     * as if by calling {@link ValueEmitterFactory#newLiteralValue(String, TypeInstance, SourceInfo)} for each literal.
     *
     * @return {@link EmitObjectNode} if successful, <code>null</code> otherwise.
     */
    public static ValueEmitterNode newObjectByCoercion(ObjectNode objectNode) {
        if (objectNode.getChildren().size() != 1) {
            return null;
        }

        TypeInstance targetType = TypeHelper.getTypeInstance(objectNode);
        TextNode childText = objectNode.getChildren().get(0).as(TextNode.class);
        if (childText == null) {
            return null;
        }

        ValueEmitterNode result = newObjectByCoercion(objectNode, childText, objectNode.getProperties(), targetType);
        if (result != null) {
            objectNode.getChildren().clear();
        }

        return result;
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of a constructor where,
     * given a list of string literals, all literals can be coerced to their respective constructor parameters
     * as if by calling {@link ValueEmitterFactory#newLiteralValue(String, TypeInstance, SourceInfo)} for each literal.
     *
     * @return {@link EmitObjectNode} if successful, <code>null</code> otherwise.
     */
    public static ValueEmitterNode newObjectByCoercion(TypeInstance targetType, TextNode textNode) {
        return newObjectByCoercion(null, textNode, Collections.emptyList(), targetType);
    }

    private static ValueEmitterNode newObjectByCoercion(
            ObjectNode objectNode, TextNode textNode, Collection<? extends Node> children, TypeInstance targetType) {
        if (targetType.declaration().isPrimitiveBox()) {
            return null;
        }

        TextNode[] literals;

        if (textNode instanceof ListNode listNode) {
            List<TextNode> list = new ArrayList<>();

            for (ValueNode node : listNode.getValues()) {
                if (!(node instanceof TextNode listTextNode)) {
                    return null;
                }

                list.add(listTextNode);
            }

            literals = list.toArray(TextNode[]::new);
        } else {
            literals = new TextNode[] { textNode };
        }

        ConstructorWithParams constructor = findConstructor(targetType, literals, textNode.getSourceInfo());
        if (constructor == null) {
            return newArray(literals, targetType, textNode.getSourceInfo());
        }

        SourceInfo sourceInfo = objectNode != null ? objectNode.getSourceInfo() : textNode.getSourceInfo();

        return EmitObjectNode
            .constructor(targetType, constructor.constructor(), constructor.params(), sourceInfo)
            .backingField(objectNode != null ? findAndRemoveId(objectNode) : null)
            .children(children)
            .create();
    }

    private static ConstructorWithParams findConstructor(TypeInstance type, TextNode[] literals, SourceInfo sourceInfo) {
        TypeInvoker invoker = new TypeInvoker(sourceInfo);

        outer: for (ConstructorDeclaration constructor : type.declaration().constructors()) {
            if (constructor.accessModifier() != AccessModifier.PUBLIC
                    || invoker.invokeParameterTypes(constructor, List.of(type)).length != literals.length) {
                continue;
            }

            // We only consider NamedArg-based constructors for value coercion, so skip this
            // constructor if we don't have a full set of named arguments.
            List<NamedArgParam> namedArgs = getNamedArgParams(type, constructor, sourceInfo);
            if (namedArgs.isEmpty()) {
                continue;
            }

            List<ValueEmitterNode> params = new ArrayList<>();
            TypeInstance[] paramTypes = invoker.invokeParameterTypes(constructor, List.of(type));

            for (int i = 0; i < literals.length; ++i) {
                ValueEmitterNode param = newLiteralValue(literals[i], paramTypes[i], sourceInfo);
                if (param != null) {
                    params.add(param);
                } else {
                    continue outer;
                }
            }

            return new ConstructorWithParams(constructor, params);
        }

        return null;
    }

    private record ConstructorWithParams(ConstructorDeclaration constructor, List<ValueEmitterNode> params) {}

    private static ValueEmitterNode newArray(TextNode[] literals, TypeInstance targetType, SourceInfo sourceInfo) {
        if (!targetType.isArray() || targetType.dimensions() != 1) {
            return null;
        }

        List<ValueEmitterNode> params = new ArrayList<>();

        for (TextNode literal : literals) {
            ValueEmitterNode param = newLiteralValue(literal, targetType.componentType(), sourceInfo);
            if (param != null) {
                params.add(param);
            } else {
                return null;
            }
        }

        return new EmitArrayNode(targetType, params);
    }

    /**
     * Tries to find constructors for the specified type where all formal parameters are annotated with
     * the @NamedArg annotation, so they can be correlated to properties specified as FXML attributes.
     *
     * @return The constructor array, sorted by preference, or an empty array if no matching constructor was found.
     */
    private static NamedArgsConstructor[] findNamedArgsConstructors(
            ObjectNode objectNode, List<DiagnosticInfo> diagnostics) {
        List<NamedArgsConstructor> namedArgsConstructors = new ArrayList<>();
        TypeInstance type = TypeHelper.getTypeInstance(objectNode);

        // Enumerate all constructors that have named arguments
        for (ConstructorDeclaration constructor : type.declaration().constructors()) {
            if (constructor.accessModifier() != AccessModifier.PUBLIC) {
                continue;
            }

            List<NamedArgParam> namedArgs = getNamedArgParams(type, constructor, objectNode.getSourceInfo());
            if (!namedArgs.isEmpty()) {
                namedArgsConstructors.add(
                    new NamedArgsConstructor(constructor, namedArgs.toArray(new NamedArgParam[0])));
            }
        }

        Map<Integer, List<NamedArgsConstructor>> constructorOrder = new TreeMap<>(Comparator.reverseOrder());

        for (NamedArgsConstructor constructor : namedArgsConstructors) {
            NamedArgParam[] namedArgs = constructor.namedArgs();
            int matches = 0;

            for (NamedArgParam param : namedArgs) {
                if (param.isOptional() || objectNode.getProperties().stream()
                        .anyMatch(n -> n.getName().equals(param.name()))) {
                    ++matches;
                }
            }

            if (matches == namedArgs.length) {
                constructorOrder.computeIfAbsent(matches, x -> new ArrayList<>()).add(constructor);
            } else {
                var argTypes = Arrays.stream(namedArgs).map(NamedArgParam::type).toArray(TypeInstance[]::new);
                var argNames = Arrays.stream(namedArgs).map(NamedArgParam::name).toArray(String[]::new);

                diagnostics.add(new DiagnosticInfo(
                    Diagnostic.newDiagnosticVariant(
                        ErrorCode.NUM_FUNCTION_ARGUMENTS_MISMATCH, "named",
                        NameHelper.getDisplaySignature(constructor.constructor(), argTypes, argNames),
                        namedArgs.length,
                        matches),
                    objectNode.getSourceInfo()));
            }
        }

        if (!constructorOrder.isEmpty()) {
            return constructorOrder.values().iterator().next().toArray(new NamedArgsConstructor[0]);
        }

        return new NamedArgsConstructor[0];
    }

    private static List<NamedArgParam> getNamedArgParams(
            TypeInstance type, ConstructorDeclaration constructor, SourceInfo sourceInfo) {
        TypeInvoker invoker = new TypeInvoker(sourceInfo);
        TypeInstance[] constructorParamTypes = invoker.invokeParameterTypes(constructor, List.of(type));
        List<NamedArgParam> namedArgs = new ArrayList<>();

        for (int i = 0; i < constructorParamTypes.length; ++i) {
            AnnotationDeclaration namedArgAnnotation = null;

            for (AnnotationDeclaration item : constructor.parameters().get(i).annotations()) {
                if (item.typeName().equals(NamedArgAnnotationName)) {
                    namedArgAnnotation = item;
                    break;
                }
            }

            if (namedArgAnnotation == null) {
                return List.of();
            }

            String value = namedArgAnnotation.getString("value");
            String defaultValue = namedArgAnnotation.getString("defaultValue");

            if (value != null) {
                TypeInstance argType = constructorParamTypes[i];
                namedArgs.add(new NamedArgParam(value, defaultValue, argType));
            }
        }

        return namedArgs;
    }

    private record NamedArgParam(String name, String defaultValue, TypeInstance type) {
        boolean isOptional() {
            return defaultValue != null;
        }
    }

    private record NamedArgsConstructor(ConstructorDeclaration constructor, NamedArgParam[] namedArgs) {}

    private static String findAndRemoveId(ObjectNode node) {
        PropertyNode propertyNode = node.findIntrinsicProperty(Intrinsics.ID);
        if (propertyNode == null) {
            return null;
        }

        propertyNode.remove();

        if (propertyNode.getValues().size() != 1 || !(propertyNode.getValues().get(0) instanceof TextNode)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                propertyNode.getSourceInfo(), TypeHelper.getTypeDeclaration(node), Intrinsics.ID.getName());
        }

        return ((TextNode)propertyNode.getValues().get(0)).getText();
    }

    private sealed interface AcceptArgumentResult {
        record Success<T extends ValueNode>(T value) implements AcceptArgumentResult {
            public Success {
                Objects.requireNonNull(value);
            }
        }

        record Error(Node argumentNode,
                     @Nullable TypeInstance errorType,
                     ErrorCode errorCode,
                     SourceInfo sourceInfo) implements AcceptArgumentResult {
            public Error {
                Objects.requireNonNull(argumentNode);
                Objects.requireNonNull(errorCode);
                Objects.requireNonNull(sourceInfo);
            }

            public Error(Node argumentNode, TypeInstance errorType, ErrorCode errorCode) {
                this(argumentNode, errorType, errorCode, argumentNode.getSourceInfo());
            }
        }
    }
}
