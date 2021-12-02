// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import javafx.scene.paint.Color;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.ParameterAnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitCollectionAdderNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitMapAdderNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.PropertyHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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

        return newLiteralValue(
            findAndRemoveId(node),
            text,
            Collections.emptyList(),
            TypeHelper.getTypeInstance(node),
            node.getSourceInfo());
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
                return new EmitLiteralNode(id, new TypeInstance(CtClass.booleanType), true, sourceInfo);
            } else if (trimmedValue.equalsIgnoreCase("false")) {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.booleanType), false, sourceInfo);
            }

            break;
        case "char":
        case Classes.CharacterName:
            if (trimmedValue.length() == 1) {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.charType), trimmedValue.charAt(0), sourceInfo);
            }

            break;
        case "byte":
        case Classes.ByteName:
            try {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.byteType), Byte.parseByte(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "short":
        case Classes.ShortName:
            try {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.shortType), Short.parseShort(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "int":
        case Classes.IntegerName:
            try {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.intType), Integer.parseInt(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "long":
        case Classes.LongName:
            try {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.longType), Long.parseLong(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "float":
        case Classes.FloatName:
            try {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.floatType), Float.parseFloat(trimmedValue), sourceInfo);
            } catch (NumberFormatException ex) {
                break;
            }
        case "double":
        case Classes.DoubleName:
            try {
                return new EmitLiteralNode(id, new TypeInstance(CtClass.doubleType), Double.parseDouble(trimmedValue), sourceInfo);
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
            return new EmitLiteralNode(id, new Resolver(sourceInfo).getTypeInstance(Classes.StringType()), value, sourceInfo);
        }

        if (unchecked(sourceInfo, () -> Classes.ColorType().subtypeOf(targetType.jvmType()))) {
            try {
                Color color = Color.valueOf(value);
                Field colorField = findColorField(color);

                if (colorField != null) {
                    return new EmitClassConstantNode(
                        id,
                        new TypeInstance(Classes.ColorType()),
                        Classes.ColorType(),
                        colorField.getName(),
                        sourceInfo);
                } else {
                    return new EmitObjectNode(
                        null,
                        new TypeInstance(Classes.ColorType()),
                        null,
                        List.of(new EmitLiteralNode(new TypeInstance(Classes.StringType()), value, sourceInfo)),
                        Collections.emptyList(),
                        EmitObjectNode.CreateKind.VALUE_OF,
                        sourceInfo);
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
                var fieldType = resolver.getTypeInstance(field, List.of(declaringType));
                if (targetType.isAssignableFrom(fieldType)) {
                    return new EmitClassConstantNode(id, targetType, boxedDeclaringType, field.getName(), sourceInfo);
                }
            }
        }

        return null;
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of a constructor
     * where all parameters are annotated with {@link javafx.beans.NamedArg}.
     *
     * @return {@link EmitObjectNode} if successful; <code>null</code> otherwise.
     */
    public static EmitObjectNode newObjectWithNamedParams(ObjectNode objectNode, List<DiagnosticInfo> diagnostics) {
        TypeInstance type = TypeHelper.getTypeInstance(objectNode);
        NamedArgsConstructor[] namedArgsConstructors = findNamedArgsConstructors(objectNode);

        outer:
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

                // If a primitive-type property was not specified, we synthesize a literal node
                // with the default value for the primitive type.
                if (propertyNode == null) {
                    if (constructorParam.type().isPrimitive()) {
                        arguments.add(
                            new EmitLiteralNode(
                                constructorParam.type(),
                                TypeHelper.getDefaultValue(constructorParam.type().jvmType()),
                                objectNode.getSourceInfo()));
                        continue;
                    } else {
                        break outer;
                    }
                }

                // For scalar properties, check whether the property type is assignable to the
                // corresponding formal parameter of the current constructor.
                if (propertyNode.getValues().size() == 1) {
                    ValueNode argument = acceptArgument(
                        propertyNode.getValues().get(0), constructorParam.type(), type, vararg);

                    if (argument != null) {
                        arguments.add(argument);
                    } else {
                        diagnostics.add(
                            new DiagnosticInfo(
                                Diagnostic.newDiagnostic(
                                    ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
                                    namedArgsConstructor.constructor().getLongName(),
                                    argIndex + 1,
                                    TypeHelper.getTypeInstance(propertyNode.getValues().get(0)).getJavaName()),
                                propertyNode.getValues().get(0).getSourceInfo()));

                        break outer;
                    }
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

        return null;
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of a constructor
     * with arguments provided by the specified {@link ObjectNode#getChildren()}.
     *
     * @return {@link EmitObjectNode} if successful; <code>null</code> otherwise.
     */
    public static EmitObjectNode newObjectWithArguments(ObjectNode objectNode, List<DiagnosticInfo> diagnostics) {
        TypeInstance type = TypeHelper.getTypeInstance(objectNode);
        List<Node> children = objectNode.getChildren();
        List<Node> argList = new ArrayList<>(children);
        ListIterator<Node> arg = argList.listIterator();

        while (arg.hasNext()) {
            Node node = arg.next();

            if (!(node instanceof ValueNode)) {
                return null;
            }

            if (node instanceof ListNode) {
                arg.remove();

                for (Node child : ((ListNode)children.get(0)).getValues()) {
                    arg.add(child);
                }
            }
        }

        CtConstructor constructor = new MethodFinder(type, type.jvmType()).findConstructor(
            argList.stream().map(TypeHelper::getTypeInstance).collect(Collectors.toList()),
            argList.stream().map(Node::getSourceInfo).collect(Collectors.toList()),
            diagnostics,
            objectNode.getSourceInfo());

        if (constructor != null) {
            TypeInstance[] paramTypes = new Resolver(objectNode.getSourceInfo())
                .getParameterTypes(constructor, Collections.emptyList());

            for (int i = 0; i < argList.size(); ++i) {
                if (argList.get(i) instanceof TextNode) {
                    ValueEmitterNode literalArg = newLiteralValue(
                        ((TextNode)argList.get(i)).getText(), paramTypes[i], argList.get(i).getSourceInfo());

                    if (literalArg == null) {
                        diagnostics.add(
                            new DiagnosticInfo(
                                Diagnostic.newDiagnostic(
                                    ErrorCode.CANNOT_ASSIGN_FUNCTION_ARGUMENT,
                                    constructor.getLongName(),
                                    i + i,
                                    paramTypes[i].getJavaName()),
                                argList.get(i).getSourceInfo()));

                        return null;
                    }

                    argList.set(i, literalArg);
                }
            }

            children.clear();

            return createObjectNode(
                objectNode,
                constructor,
                argList.stream().map(n -> (ValueNode)n).collect(Collectors.toList()));
        }

        return null;
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the creation of a collection type,
     * followed by calling {@link java.util.Collection#add(Object)} for each child.
     *
     * @return {@link EmitObjectNode} if successful; <code>null</code> otherwise.
     */
    public static EmitObjectNode newCollection(ObjectNode node, List<DiagnosticInfo> diagnostics) {
        if (!TypeHelper.getTypeInstance(node).subtypeOf(Classes.CollectionType())) {
            return null;
        }

        List<Node> children = new ArrayList<>(node.getChildren());
        node.getChildren().clear();
        EmitObjectNode newObjectNode = ValueEmitterFactory.newObjectWithArguments(node, diagnostics);
        if (newObjectNode == null) {
            node.getChildren().addAll(children);
            return null;
        }

        Resolver resolver = new Resolver(node.getSourceInfo());
        List<TypeInstance> typeArgs = TypeHelper.getTypeInstance(node).getArguments();
        TypeInstance itemType = typeArgs.size() > 0 ? typeArgs.get(0) : resolver.getTypeInstance(Classes.ObjectType());

        for (Node child : children) {
            if (child instanceof TextNode textNode) {
                if (textNode.isRawText()) {
                    ValueEmitterNode value = ValueEmitterFactory.newLiteralValue(
                        textNode.getText(), itemType, child.getSourceInfo());

                    if (value == null) {
                        throw GeneralErrors.cannotAddItemIncompatibleType(
                            child.getSourceInfo(),
                            TypeHelper.getJvmType(node), TypeHelper.getJvmType(child), itemType.jvmType());
                    }

                    newObjectNode.getChildren().add(new EmitCollectionAdderNode(value));
                } else {
                    for (String part : textNode.getText().split(",|\\R")) {
                        if (part.isBlank()) {
                            continue;
                        }

                        ValueEmitterNode value = ValueEmitterFactory.newLiteralValue(
                            part.trim(), itemType, child.getSourceInfo());

                        if (value == null) {
                            throw GeneralErrors.cannotAddItemIncompatibleType(
                                child.getSourceInfo(),
                                TypeHelper.getJvmType(node), TypeHelper.getJvmType(child), itemType.jvmType());
                        }

                        newObjectNode.getChildren().add(new EmitCollectionAdderNode(value));
                    }
                }
            } else if (!(child instanceof ObjectNode)) {
                throw GeneralErrors.cannotAddItemIncompatibleValue(
                    child.getSourceInfo(), TypeHelper.getJvmType(node), child.getSourceInfo().getText());
            } else if (!TypeHelper.getTypeInstance(child).subtypeOf(itemType)) {
                throw GeneralErrors.cannotAddItemIncompatibleType(
                    child.getSourceInfo(),
                    TypeHelper.getJvmType(node), TypeHelper.getJvmType(child), itemType.jvmType());
            } else {
                newObjectNode.getChildren().add(new EmitCollectionAdderNode((ValueNode)child));
            }
        }

        return newObjectNode;
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the creation of a map type,
     * followed by calling {@link java.util.Map#put(Object, Object)} for each child.
     *
     * @return {@link EmitObjectNode} if successful; <code>null</code> otherwise.
     */
    public static EmitObjectNode newMap(ObjectNode node, List<DiagnosticInfo> diagnostics) {
        if (!TypeHelper.getTypeInstance(node).subtypeOf(Classes.MapType())) {
            return null;
        }

        List<Node> children = new ArrayList<>(node.getChildren());
        node.getChildren().clear();
        EmitObjectNode newObjectNode = ValueEmitterFactory.newObjectWithArguments(node, diagnostics);
        if (newObjectNode == null) {
            node.getChildren().addAll(children);
            return null;
        }

        Resolver resolver = new Resolver(node.getSourceInfo());
        List<TypeInstance> typeArgs = TypeHelper.getTypeInstance(node).getArguments();
        TypeInstance keyType = typeArgs.size() > 0 ? typeArgs.get(0) : resolver.getTypeInstance(Classes.ObjectType());
        TypeInstance itemType = typeArgs.size() > 0 ? typeArgs.get(1) : resolver.getTypeInstance(Classes.ObjectType());

        for (Node child : children) {
            if (!(child instanceof ObjectNode)) {
                throw GeneralErrors.cannotAddItemIncompatibleValue(
                    child.getSourceInfo(), TypeHelper.getJvmType(node), child.getSourceInfo().getText());
            }

            if (!TypeHelper.getTypeInstance(child).subtypeOf(itemType)) {
                throw GeneralErrors.cannotAddItemIncompatibleType(
                    child.getSourceInfo(),
                    TypeHelper.getJvmType(node), TypeHelper.getJvmType(child), itemType.jvmType());
            }

            if (!keyType.equals(Classes.StringType()) && !keyType.equals(Classes.ObjectType())) {
                throw GeneralErrors.unsupportedMapKeyType(node.getSourceInfo(), TypeHelper.getJvmType(node));
            }

            newObjectNode.getChildren().add(
                new EmitMapAdderNode(createKey((ObjectNode)child, keyType), (ObjectNode)child));
        }

        return newObjectNode;
    }

    private static ValueNode createKey(ObjectNode node, TypeInstance keyType) {
        Resolver resolver = new Resolver(node.getSourceInfo());
        PropertyNode id = node.findIntrinsicProperty(Intrinsics.ID);
        if (id != null) {
            return newLiteralValue(
                ((TextNode)id.getValues().get(0)).getText(),
                resolver.getTypeInstance(Classes.StringType()),
                node.getSourceInfo());
        }

        if (keyType.equals(Classes.StringType())) {
            return ValueEmitterFactory.newLiteralValue(
                NameHelper.getUniqueName(
                    UUID.nameUUIDFromBytes(TypeHelper.getJvmType(node).getName().getBytes()).toString(), node),
                resolver.getTypeInstance(Classes.StringType()),
                node.getSourceInfo());
        }

        return new EmitObjectNode(
            null,
            resolver.getTypeInstance(Classes.ObjectType()),
            unchecked(node.getSourceInfo(), () -> Classes.ObjectType().getConstructor("()V")),
            Collections.emptyList(),
            Collections.emptyList(),
            EmitObjectNode.CreateKind.CONSTRUCTOR,
            node.getSourceInfo());
    }

    /**
     * Determines whether the input argument is acceptable for the target type and returns
     * a new node that will emit the input argument.
     */
    private static ValueNode acceptArgument(
            Node argumentNode, TypeInstance targetType, TypeInstance invokingType, boolean vararg) {
        SourceInfo sourceInfo = argumentNode.getSourceInfo();
        ValueNode value;

        if (argumentNode instanceof BindingNode bindingNode) {
            if (bindingNode.getMode() == BindingMode.ONCE) {
                value = bindingNode.toEmitter(invokingType).getValue();
            } else {
                throw GeneralErrors.expressionNotApplicable(sourceInfo, true);
            }
        } else if (argumentNode instanceof TextNode textNode) {
            value = newObjectByCoercion(targetType, textNode);
            return value != null ? value :
                newLiteralValue(textNode.getText(), List.of(targetType), targetType, sourceInfo);
        } else if (argumentNode instanceof ValueNode valueNode) {
            value = valueNode;
        } else {
            return null;
        }

        var valueType = TypeHelper.getTypeInstance(value);
        if (targetType.isConvertibleFrom(valueType) ||
                vararg && targetType.getComponentType().isConvertibleFrom(valueType)) {
            return value;
        }

        return null;
    }

    private static EmitObjectNode createObjectNode(
            ObjectNode objectNode, CtConstructor constructor, List<ValueNode> arguments) {
        return new EmitObjectNode(
            findAndRemoveId(objectNode),
            TypeHelper.getTypeInstance(objectNode),
            constructor,
            arguments,
            PropertyHelper.getSorted(objectNode, objectNode.getProperties()),
            EmitObjectNode.CreateKind.CONSTRUCTOR,
            objectNode.getSourceInfo());
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of a constructor where,
     * given a list of string literals, all literals can be coerced to their respective constructor parameters
     * as if by calling {@link ValueEmitterFactory#newLiteralValue(String, TypeInstance, SourceInfo)} for each literal.
     *
     * @return {@link EmitObjectNode} if successful, <code>null</code> otherwise.
     */
    public static EmitObjectNode newObjectByCoercion(ObjectNode objectNode) {
        if (objectNode.getChildren().size() != 1) {
            return null;
        }

        TypeInstance targetType = TypeHelper.getTypeInstance(objectNode);
        TextNode childText = objectNode.getChildren().get(0).as(TextNode.class);
        if (childText == null) {
            return null;
        }

        return newObjectByCoercion(objectNode, childText, objectNode.getProperties(), targetType);
    }

    /**
     * Tries to create a {@link EmitObjectNode} that represents the invocation of a constructor where,
     * given a list of string literals, all literals can be coerced to their respective constructor parameters
     * as if by calling {@link ValueEmitterFactory#newLiteralValue(String, TypeInstance, SourceInfo)} for each literal.
     *
     * @return {@link EmitObjectNode} if successful, <code>null</code> otherwise.
     */
    public static EmitObjectNode newObjectByCoercion(TypeInstance targetType, TextNode textNode) {
        return newObjectByCoercion(null, textNode, Collections.emptyList(), targetType);
    }

    private static EmitObjectNode newObjectByCoercion(
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
                return null;
            }
        }

        return new EmitObjectNode(
            objectNode != null ? findAndRemoveId(objectNode) : null,
            targetType,
            constructor.constructor(),
            constructor.params(),
            children,
            EmitObjectNode.CreateKind.CONSTRUCTOR,
            textNode.getSourceInfo());
    }

    private static ConstructorWithParams findConstructor(TypeInstance type, String[] literals, SourceInfo sourceInfo) {
        Resolver resolver = new Resolver(sourceInfo);

        outer: for (CtConstructor constructor : type.jvmType().getConstructors()) {
            if (resolver.getParameterTypes(constructor, List.of(type)).length != literals.length) {
                continue;
            }

            List<ValueEmitterNode> params = new ArrayList<>();

            for (int i = 0; i < literals.length; ++i) {
                ValueEmitterNode param = newLiteralValue(
                    literals[i], resolver.getParameterTypes(constructor, List.of(type))[i], sourceInfo);

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

    /**
     * Tries to find constructors for the specified type where all formal parameters are annotated with
     * the @NamedArg annotation, so they can be correlated to properties specified as FXML attributes.
     *
     * @return The constructor array, sorted by preference, or an empty array if no matching constructor was found.
     */
    private static NamedArgsConstructor[] findNamedArgsConstructors(ObjectNode objectNode) {
        List<NamedArgsConstructor> namedArgsConstructors = new ArrayList<>();
        TypeInstance type = TypeHelper.getTypeInstance(objectNode);
        Resolver resolver = new Resolver(objectNode.getSourceInfo());

        // Enumerate all constructors that have named arguments
        for (CtConstructor constructor : type.jvmType().getDeclaredConstructors()) {
            List<NamedArgParam> namedArgs = new ArrayList<>();

            ParameterAnnotationsAttribute attr = (ParameterAnnotationsAttribute)constructor
                .getMethodInfo2().getAttribute(ParameterAnnotationsAttribute.visibleTag);

            if (attr == null) {
                continue;
            }

            TypeInstance[] constructorParamTypes = resolver.getParameterTypes(constructor, List.of(type));
            Annotation[][] annotations = attr.getAnnotations();

            for (int i = 0; i < annotations.length; ++i) {
                Annotation namedArgAnnotation = null;
                for (Annotation item : annotations[i]) {
                    if (item.getTypeName().equals(Classes.NamedArgAnnotationName)) {
                        namedArgAnnotation = item;
                        break;
                    }
                }

                if (namedArgAnnotation != null) {
                    String value = TypeHelper.getAnnotationString(namedArgAnnotation, "value");

                    if (value != null) {
                        TypeInstance argType = constructorParamTypes[i];
                        namedArgs.add(new NamedArgParam(value, argType));
                    }
                }
            }

            if (!namedArgs.isEmpty() && namedArgs.size() == constructorParamTypes.length) {
                namedArgsConstructors.add(
                    new NamedArgsConstructor(constructor, namedArgs.toArray(new NamedArgParam[0])));
            }
        }

        // Find the constructor that best fits the properties specified on the AstElementNode.
        // To determine which constructor is the best fit, we use a scoring system where each specified parameter
        // adds 256 points, and every missing parameter removes 1 point. The result is that the highest
        // number of specified parameters always wins. If it's a tie, the least number of missing parameters wins.
        //
        Map<Integer, NamedArgsConstructor> constructorOrder = new TreeMap<>(Comparator.reverseOrder());
        outer: for (NamedArgsConstructor constructor : namedArgsConstructors) {
            NamedArgParam[] namedArgs = constructor.namedArgs();
            int matches = 0;

            for (NamedArgParam param : namedArgs) {
                if (objectNode.getProperties().stream().anyMatch(n -> n.getName().equals(param.name()))) {
                    ++matches;
                } else if (!param.type().isPrimitive()) {
                    continue outer;
                }
            }

            if (matches > 0) {
                constructorOrder.put((matches - namedArgs.length) + matches * 256, constructor);
            }
        }

        if (!constructorOrder.isEmpty()) {
            return constructorOrder.values().toArray(new NamedArgsConstructor[0]);
        }

        return new NamedArgsConstructor[0];
    }

    private record NamedArgParam(String name, TypeInstance type) {}

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

}
