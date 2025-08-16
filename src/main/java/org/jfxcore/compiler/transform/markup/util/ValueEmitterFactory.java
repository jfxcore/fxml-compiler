// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import javafx.scene.paint.Color;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.ParameterAnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
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
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.PropertyHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

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
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || !java.lang.reflect.Modifier.isPublic(field.getModifiers())
                        || !java.lang.reflect.Modifier.isFinal(field.getModifiers())
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
        if (node.getChildren().size() != 1 || !(node.getChildren().get(0) instanceof TextNode)) {
            return null;
        }

        String text = ((TextNode)node.getChildren().get(0)).getText();

        ValueEmitterNode result = newLiteralValue(
            findAndRemoveId(node),
            text,
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
     * Tries to convert the string value to the target type.
     *
     * @return {@link EmitLiteralNode} if conversion was successful; <code>null</code> otherwise.
     */
    public static ValueEmitterNode newLiteralValue(
            String value, List<TypeInstance> declaringTypes, TypeInstance targetType, SourceInfo sourceInfo) {
        return newLiteralValue(null, value, declaringTypes, targetType, sourceInfo);
    }

    private static ValueEmitterNode newLiteralValue(
            String id, String value, List<TypeInstance> declaringTypes, TypeInstance targetType, SourceInfo sourceInfo) {
        String trimmedValue = value.trim();

        switch (targetType.getName()) {
        case "boolean":
        case Classes.BooleanName:
            if (trimmedValue.equalsIgnoreCase("true")) {
                return new EmitLiteralNode(id, TypeInstance.booleanType(), true, sourceInfo);
            } else if (trimmedValue.equalsIgnoreCase("false")) {
                return new EmitLiteralNode(id, TypeInstance.booleanType(), false, sourceInfo);
            }

            break;
        case "char":
        case Classes.CharacterName:
            if (trimmedValue.length() == 1) {
                return new EmitLiteralNode(id, TypeInstance.charType(), trimmedValue.charAt(0), sourceInfo);
            }

            break;
        case "byte":
        case Classes.ByteName:
            try {
                return new EmitLiteralNode(id, TypeInstance.byteType(), Byte.parseByte(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "short":
        case Classes.ShortName:
            try {
                return new EmitLiteralNode(id, TypeInstance.shortType(), Short.parseShort(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "int":
        case Classes.IntegerName:
            try {
                return new EmitLiteralNode(id, TypeInstance.intType(), Integer.parseInt(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "long":
        case Classes.LongName:
            try {
                return new EmitLiteralNode(id, TypeInstance.longType(), Long.parseLong(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "float":
        case Classes.FloatName:
            try {
                return new EmitLiteralNode(id, TypeInstance.floatType(), Float.parseFloat(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "double":
        case Classes.DoubleName:
            try {
                return new EmitLiteralNode(id, TypeInstance.doubleType(), Double.parseDouble(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        }

        if (targetType.jvmType().isEnum()) {
            try {
                return new EmitLiteralNode(id, targetType, targetType.jvmType().getField(trimmedValue), sourceInfo);
            } catch (NotFoundException ex) {
                throw SymbolResolutionErrors.memberNotFound(sourceInfo, targetType.jvmType(), value);
            }
        }

        if (unchecked(sourceInfo, () -> Classes.StringType().subtypeOf(targetType.jvmType()))) {
            return new EmitLiteralNode(id, TypeInstance.StringType(), value, sourceInfo);
        }

        if (unchecked(sourceInfo, () -> Classes.ColorType().subtypeOf(targetType.jvmType()))) {
            try {
                Color color = Color.valueOf(value);
                Field colorField = findColorField(color);

                if (colorField != null) {
                    return new EmitClassConstantNode(
                        id,
                        TypeInstance.of(Classes.ColorType()),
                        Classes.ColorType(),
                        colorField.getName(),
                        sourceInfo);
                } else {
                    CtMethod valueOfMethod = new Resolver(sourceInfo).tryResolveMethod(
                        Classes.ColorType(), m -> "valueOf".equals(m.getName()));

                    return EmitObjectNode
                        .valueOf(TypeInstance.of(Classes.ColorType()), valueOfMethod, sourceInfo)
                        .textValue(value)
                        .create();
                }
            } catch (NullPointerException | IllegalArgumentException ex) {
                return null;
            }
        }

        for (TypeInstance declaringType : declaringTypes) {
            // Always use boxed type to support static fields on primitive wrapper classes.
            CtClass boxedDeclaringType = TypeHelper.getBoxedType(declaringType.jvmType());

            var resolver = new Resolver(sourceInfo);
            CtField field = resolver.tryResolveField(boxedDeclaringType, trimmedValue);

            if (field != null) {
                var fieldType = new TypeInvoker(sourceInfo).invokeFieldType(field, List.of(declaringType));
                if (targetType.isAssignableFrom(fieldType)) {
                    return new EmitClassConstantNode(id, targetType, boxedDeclaringType, field.getName(), sourceInfo);
                }
            }
        }

        return null;
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of the default constructor.
     *
     * @return {@link EmitObjectNode} if successful; <code>null</code> otherwise.
     */
    public static EmitObjectNode newDefaultObject(ObjectNode objectNode) {
        try {
            TypeInstance type = TypeHelper.getTypeInstance(objectNode);
            CtConstructor constructor = type.jvmType().getConstructor(Descriptors.constructor());

            if (Modifier.isPublic(constructor.getModifiers())) {
                return createObjectNode(objectNode, constructor, Collections.emptyList());
            }
        } catch (NotFoundException ignored) {
        }

        return null;
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
                        && Modifier.isVarArgs(namedArgsConstructor.constructor().getModifiers());

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

                // For scalar properties, check whether the property type is assignable to the
                // corresponding formal parameter of the current constructor.
                if (propertyNode != null && propertyNode.getValues().size() == 1) {
                    AcceptArgumentResult<? extends ValueNode> result = acceptArgument(
                        context, propertyNode.getValues().get(0), constructorParam.type(), constructorParam.name(),
                        type, vararg, parentsUnderInitializationCount);

                    if (result.errorCode() != null) {
                        var namedArgs = namedArgsConstructor.namedArgs();
                        var argTypes = Arrays.stream(namedArgs).map(NamedArgParam::type).toArray(TypeInstance[]::new);
                        var argNames = Arrays.stream(namedArgs).map(NamedArgParam::name).toArray(String[]::new);
                        var methodSignature = NameHelper.getShortMethodSignature(
                            namedArgsConstructor.constructor(), argTypes, argNames);

                        switch (result.errorCode()) {
                            case CANNOT_ASSIGN_FUNCTION_ARGUMENT -> {
                                var errorType = propertyNode.getValues().get(0) instanceof ValueNode valueNode ?
                                    TypeHelper.getTypeInstance(valueNode) : result.errorType();

                                constructorDiagnostics.add(
                                    new DiagnosticInfo(
                                        Diagnostic.newDiagnosticVariant(
                                            ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, "named",
                                            methodSignature,
                                            propertyNode.getName(),
                                            errorType != null ? errorType.getJavaName() : "<error-type>"),
                                        result.sourceInfo()));
                            }

                            case CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION -> {
                                BindingNode bindingNode = (BindingNode)propertyNode.getValues().get(0);
                                int bindingDistance = bindingNode.getBindingDistance();
                                if (bindingDistance == 0) {
                                    constructorDiagnostics.add(
                                        new DiagnosticInfo(
                                            Diagnostic.newDiagnosticVariant(
                                                ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION,
                                                "argument",
                                                methodSignature,
                                                propertyNode.getName()),
                                            result.sourceInfo()));
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
                                                TypeHelper.getTypeInstance(referencedParent).getJavaName()),
                                            result.sourceInfo()));
                                }
                            }

                            default -> throw new AssertionError(result.errorCode().toString());
                        }

                        break;
                    }

                    arguments.add(result.value());
                }

                // TODO: We currently don't support collection-type properties in named-arg constructors
            }

            // If we have arguments for all formal parameters, we can construct the object.
            if (arguments.size() == namedArgsConstructor.namedArgs().length) {
                objectNode.getProperties().removeIf(p ->
                    Arrays.stream(namedArgsConstructor.namedArgs())
                        .anyMatch(p2 -> p2.name().equals(p.getName())));

                return createObjectNode(objectNode, namedArgsConstructor.constructor(), arguments);
            }
        }

        if (constructorDiagnostics.size() > 0) {
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
    private static AcceptArgumentResult<? extends ValueNode> acceptArgument(
            TransformContext context, Node argumentNode, TypeInstance targetType, String targetName,
            TypeInstance invokingType, boolean vararg, int parentsUnderInitializationCount) {
        SourceInfo sourceInfo = argumentNode.getSourceInfo();
        ValueNode value = null;

        if (argumentNode instanceof BindingNode bindingNode) {
            var result = acceptBindingArgument(bindingNode, invokingType, targetType,
                                               parentsUnderInitializationCount);
            if (result.errorCode() != null) {
                return result;
            } else {
                value = result.value();
            }
        } else if (argumentNode instanceof ListNode listNode && targetType.isArray()) {
            var result = acceptArrayArgument(context, listNode, invokingType, targetType,
                                             parentsUnderInitializationCount);
            if (result.errorCode() != null) {
                return result;
            } else {
                value = result.value();
            }
        } else if (argumentNode instanceof TextNode textNode) {
            value = newObjectByCoercion(targetType, textNode);
            if (value == null) {
                value = newLiteralValue(textNode.getText(), List.of(targetType), targetType, sourceInfo);
            }
        } else if (MarkupExtensionInfo.of(argumentNode) instanceof MarkupExtensionInfo.Supplier supplierInfo) {
            var result = acceptSupplierArgument(context, targetType, targetName,
                                                (ObjectNode)argumentNode, supplierInfo);
            if (result.errorCode() != null) {
                return result;
            } else {
                value = result.value();
            }
        } else if (argumentNode instanceof ValueNode valueNode) {
            value = valueNode;
        }

        if (value == null) {
            return AcceptArgumentResult.error(
                null, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, argumentNode.getSourceInfo());
        }

        TypeInstance valueType = TypeHelper.getTypeInstance(value);

        if (targetType.isAssignableFrom(valueType) ||
                vararg && targetType.getComponentType().isAssignableFrom(valueType)) {
            return AcceptArgumentResult.value(value);
        }

        return AcceptArgumentResult.error(valueType, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, value.getSourceInfo());
    }

    private static AcceptArgumentResult<? extends ValueNode> acceptArrayArgument(TransformContext context,
                                                                                 ListNode listNode,
                                                                                 TypeInstance invokingType,
                                                                                 TypeInstance arrayType,
                                                                                 int parentsUnderInitializationCount) {
        List<ValueEmitterNode> values = new ArrayList<>();

        for (ValueNode item : listNode.getValues()) {
            var result = acceptArgument(context, item, arrayType.getComponentType(), null,
                                        invokingType, false, parentsUnderInitializationCount);

            if (result.errorCode() != null) {
                return result;
            } else if (result.value() instanceof ValueEmitterNode valueEmitterNode) {
                values.add(valueEmitterNode);
            } else {
                return AcceptArgumentResult.error(
                    arrayType.getComponentType(), ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, item.getSourceInfo());
            }
        }

        return AcceptArgumentResult.value(new EmitArrayNode(arrayType, values));
    }

    private static AcceptArgumentResult<ValueEmitterNode> acceptBindingArgument(BindingNode bindingNode,
                                                                                TypeInstance invokingType,
                                                                                TypeInstance argumentType,
                                                                                int parentsUnderInitializationCount) {
        if (bindingNode.getMode() != BindingMode.ONCE && bindingNode.getMode() != BindingMode.UNIDIRECTIONAL) {
            throw GeneralErrors.expressionNotApplicable(bindingNode.getSourceInfo(), true);
        }

        if (bindingNode.getBindingDistance() <= parentsUnderInitializationCount) {
            return AcceptArgumentResult.error(
                null, ErrorCode.CANNOT_REFERENCE_NODE_UNDER_INITIALIZATION, bindingNode.getSourceInfo());
        }

        ValueEmitterNode result = bindingNode.toPathEmitter(invokingType, argumentType).getValue();
        adjustParentIndex(result, parentsUnderInitializationCount + 1);

        return AcceptArgumentResult.value(result);
    }

    private static AcceptArgumentResult<ValueEmitterNode> acceptSupplierArgument(TransformContext context,
                                                                                 TypeInstance targetType,
                                                                                 String targetName,
                                                                                 ObjectNode markupExtensionNode,
                                                                                 MarkupExtensionInfo.Supplier supplierInfo) {
        TypeInstance valueType = TypeHelper.getTypeInstance(markupExtensionNode);

        if (supplierInfo.providedTypes().stream().noneMatch(targetType::isAssignableFrom)) {
            return AcceptArgumentResult.error(
                valueType, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, markupExtensionNode.getSourceInfo());
        }

        ValueEmitterNode argValueNode = newObjectWithNamedParams(context, markupExtensionNode, List.of());
        if (argValueNode == null) {
            return AcceptArgumentResult.error(
                valueType, ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT, markupExtensionNode.getSourceInfo());
        }

        return AcceptArgumentResult.value(new EmitApplyMarkupExtensionNode.Supplier(
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
            ObjectNode objectNode, CtConstructor constructor, List<ValueNode> arguments) {
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
        if (TypeHelper.isPrimitiveBox(targetType.jvmType())) {
            return null;
        }

        String[] literals = new String[] {textNode.getText()};

        if (textNode instanceof ListNode listNode) {
            List<String> list = new ArrayList<>();

            for (ValueNode node : listNode.getValues()) {
                if (!(node instanceof TextNode listTextNode)) {
                    return null;
                }

                list.add(listTextNode.getText());
            }

            literals = list.toArray(new String[0]);
        }

        ConstructorWithParams constructor = findConstructor(targetType, literals, textNode.getSourceInfo());

        if (constructor == null) {
            literals = Arrays.stream(textNode.getText().split(",")).map(String::trim).toArray(String[]::new);
            constructor = findConstructor(targetType, literals, textNode.getSourceInfo());

            if (constructor == null) {
                return newArray(literals, targetType, textNode.getSourceInfo());
            }
        }

        SourceInfo sourceInfo = objectNode != null ? objectNode.getSourceInfo() : textNode.getSourceInfo();

        return EmitObjectNode
            .constructor(targetType, constructor.constructor(), constructor.params(), sourceInfo)
            .backingField(objectNode != null ? findAndRemoveId(objectNode) : null)
            .children(children)
            .create();
    }

    private static ConstructorWithParams findConstructor(TypeInstance type, String[] literals, SourceInfo sourceInfo) {
        TypeInvoker invoker = new TypeInvoker(sourceInfo);

        outer: for (CtConstructor constructor : type.jvmType().getConstructors()) {
            if (!Modifier.isPublic(constructor.getModifiers())
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

    private record ConstructorWithParams(CtConstructor constructor, List<ValueEmitterNode> params) {}

    private static ValueEmitterNode newArray(String[] literals, TypeInstance targetType, SourceInfo sourceInfo) {
        if (!targetType.isArray() || targetType.getDimensions() != 1) {
            return null;
        }

        List<ValueEmitterNode> params = new ArrayList<>();

        for (String literal : literals) {
            ValueEmitterNode param = newLiteralValue(literal, targetType.getComponentType(), sourceInfo);
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
        for (CtConstructor constructor : type.jvmType().getConstructors()) {
            if (!Modifier.isPublic(constructor.getModifiers())) {
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
                        NameHelper.getShortMethodSignature(constructor.constructor(), argTypes, argNames),
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
            TypeInstance type, CtConstructor constructor, SourceInfo sourceInfo) {
        ParameterAnnotationsAttribute attr = (ParameterAnnotationsAttribute)constructor
            .getMethodInfo2().getAttribute(ParameterAnnotationsAttribute.visibleTag);

        if (attr == null) {
            return List.of();
        }

        TypeInvoker invoker = new TypeInvoker(sourceInfo);
        TypeInstance[] constructorParamTypes = invoker.invokeParameterTypes(constructor, List.of(type));
        Annotation[][] annotations = attr.getAnnotations();

        if (annotations.length != constructorParamTypes.length) {
            return List.of();
        }

        List<NamedArgParam> namedArgs = new ArrayList<>();

        for (int i = 0; i < annotations.length; ++i) {
            Annotation namedArgAnnotation = null;
            for (Annotation item : annotations[i]) {
                if (item.getTypeName().equals(Classes.NamedArgAnnotationName)) {
                    namedArgAnnotation = item;
                    break;
                }
            }

            if (namedArgAnnotation == null) {
                return List.of();
            }

            String value = TypeHelper.getAnnotationString(namedArgAnnotation, "value");
            String defaultValue = TypeHelper.getAnnotationString(namedArgAnnotation, "defaultValue");

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

    private record NamedArgsConstructor(CtConstructor constructor, NamedArgParam[] namedArgs) {}

    private static String findAndRemoveId(ObjectNode node) {
        PropertyNode propertyNode = node.findIntrinsicProperty(Intrinsics.ID);
        if (propertyNode == null) {
            return null;
        }

        propertyNode.remove();

        if (propertyNode.getValues().size() != 1 || !(propertyNode.getValues().get(0) instanceof TextNode)) {
            throw PropertyAssignmentErrors.propertyMustContainText(
                propertyNode.getSourceInfo(), TypeHelper.getJvmType(node), Intrinsics.ID.getName());
        }

        return ((TextNode)propertyNode.getValues().get(0)).getText();
    }

    private record AcceptArgumentResult<T extends ValueNode>(T value,
                                                             TypeInstance errorType,
                                                             ErrorCode errorCode,
                                                             SourceInfo sourceInfo) {
        static <T extends ValueNode> AcceptArgumentResult<T> value(T value) {
            return new AcceptArgumentResult<>(value, null, null, null);
        }

        static <T extends ValueNode> AcceptArgumentResult<T> error(TypeInstance errorType,
                                                                   ErrorCode errorCode,
                                                                   SourceInfo sourceInfo) {
            return new AcceptArgumentResult<>(null, errorType, errorCode, sourceInfo);
        }
    }
}
