// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import javassist.CtClass;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.ContextNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitClassConstantNode;
import org.jfxcore.compiler.ast.emit.EmitEventHandlerNode;
import org.jfxcore.compiler.ast.emit.EmitInvokeGetterNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.EmitPropertyAdderNode;
import org.jfxcore.compiler.ast.emit.EmitPropertyPathNode;
import org.jfxcore.compiler.ast.emit.EmitPropertySetterNode;
import org.jfxcore.compiler.ast.emit.EmitSetFieldNode;
import org.jfxcore.compiler.ast.emit.EmitStaticPropertySetterNode;
import org.jfxcore.compiler.ast.emit.EmitTemplateContentNode;
import org.jfxcore.compiler.ast.emit.EmitUnwrapObservableNode;
import org.jfxcore.compiler.ast.emit.ReferenceableNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.markup.util.BindingEmitterFactory;
import org.jfxcore.compiler.transform.markup.util.ValueEmitterFactory;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.PropertyInfo;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.ExceptionHelper.*;

/**
 * Replaces all instances of {@link PropertyNode} in the AST with nodes that represent property assignments
 * ({@link EmitPropertySetterNode}) or static property assignments ({@link EmitStaticPropertySetterNode}).
 */
public class PropertyAssignmentTransform implements Transform {

    @Override
    public Node transform(TransformContext context, Node node) {
        if (!(node instanceof PropertyNode propertyNode)) {
            return node;
        }

        if (propertyNode.isIntrinsic(Intrinsics.CONTEXT)) {
            ContextNode contextNode = propertyNode.getSingleValue(context).as(ContextNode.class);
            if (contextNode == null) {
                throw ParserErrors.invalidExpression(propertyNode.getSingleValue(context).getSourceInfo());
            }

            return new EmitSetFieldNode(
                contextNode.getField(),
                (ValueEmitterNode)contextNode.getValue(),
                contextNode.getSourceInfo());
        }

        if (propertyNode.isIntrinsic()) {
            throw GeneralErrors.unexpectedIntrinsic(node.getSourceInfo(), propertyNode.getMarkupName());
        }

        ValueEmitterNode parentNode = context.findParent(ValueEmitterNode.class);
        Resolver resolver = new Resolver(propertyNode.getSourceInfo());
        TypeInstance declaringType = TypeHelper.getTypeInstance(parentNode);
        PropertyInfo propertyInfo = resolver.tryResolveProperty(
            declaringType, propertyNode.isAllowQualifiedName(), propertyNode.getNames());

        // A property assignment of the form foo.bar.baz="some value" must be resolved to a chain of
        // getter nodes until we arrive at the last path segment.
        if (propertyInfo == null && propertyNode.getNames().length > 1
                && Arrays.stream(propertyNode.getNames()).allMatch(s -> Character.isLowerCase(s.charAt(0)))) {
            SourceInfo sourceInfo = propertyNode.getSourceInfo();
            String[] names = propertyNode.getNames();
            List<ValueEmitterNode> nodes = new ArrayList<>();

            for (int i = 0; i < names.length - 1; ++i) {
                propertyInfo = resolver.tryResolveProperty(declaringType, false, names[i]);
                if (propertyInfo == null) {
                    // If we fail to resolve the first segment, format the error message to include the
                    // entire chain of names. This makes for a better diagnostic in case the user meant
                    // to specify the name of a static property that couldn't be resolved.
                    String name = i == 0 ? propertyNode.getName() : names[i];
                    throw SymbolResolutionErrors.propertyNotFound(sourceInfo, declaringType.jvmType(), name);
                }

                boolean hasGetter = propertyInfo.getGetter() != null;

                ValueEmitterNode emitter = new EmitInvokeGetterNode(
                    propertyInfo.getGetterOrPropertyGetter(),
                    hasGetter ? propertyInfo.getType() : propertyInfo.getObservableType(),
                    hasGetter ? ObservableKind.NONE : ObservableKind.FX_OBSERVABLE,
                    true,
                    sourceInfo);

                if (!hasGetter) {
                    emitter = new EmitUnwrapObservableNode(emitter);
                }

                nodes.add(emitter);
            }

            return new EmitPropertyPathNode(
                nodes,
                new PropertyNode(
                    new String[] {names[names.length - 1]},
                    names[names.length - 1],
                    propertyNode.getValues(),
                    false,
                    false,
                    sourceInfo),
                sourceInfo);
        }

        if (propertyInfo == null) {
            if (propertyNode.isAllowQualifiedName() && propertyNode.getNames().length > 1) {
                String[] names = propertyNode.getNames();
                String className = String.join(".", Arrays.copyOf(names, names.length - 1));
                CtClass type = resolver.tryResolveClassAgainstImports(className);

                if (type != null && TypeHelper.getTypeInstance(parentNode).subtypeOf(type)) {
                    throw SymbolResolutionErrors.propertyNotFound(
                        propertyNode.getSourceInfo(), className, names[names.length - 1]);
                }

                if (type == null) {
                    throw SymbolResolutionErrors.classNotFound(propertyNode.getSourceInfo(), className);
                }

                throw SymbolResolutionErrors.staticPropertyNotFound(
                    propertyNode.getSourceInfo(), type, names[names.length - 1]);
            }

            throw SymbolResolutionErrors.propertyNotFound(
                propertyNode.getSourceInfo(), declaringType.jvmType(), propertyNode.getName());
        }

        if (propertyNode.getValues().size() == 0) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                propertyNode.getSourceInfo(), declaringType.jvmType(), propertyNode.getMarkupName());
        }

        if (propertyNode.getValues().size() == 1) {
            Node child = propertyNode.getValues().get(0);

            if (child instanceof BindingNode bindingNode) {
                return BindingEmitterFactory.createBindingEmitter(context, propertyNode, bindingNode, propertyInfo);
            }

            if (child instanceof ValueNode valueNode) {
                Node result = trySetValue(context, valueNode, propertyInfo);
                if (result != null) {
                    return result;
                }
            }
        }

        Node result = tryAddValue(context, propertyNode, propertyInfo, declaringType);
        if (result != null) {
            return result;
        }

        if (propertyNode.getValues().size() > 1) {
            throw PropertyAssignmentErrors.propertyCannotHaveMultipleValues(
                propertyNode.getSourceInfo(), declaringType.jvmType(), propertyNode.getMarkupName());
        }

        if (propertyInfo.isReadOnly()) {
            throw PropertyAssignmentErrors.cannotModifyReadOnlyProperty(propertyNode.getSourceInfo(), propertyInfo);
        }

        if (propertyNode.getValues().size() == 1) {
            if (propertyNode.getValues().get(0) instanceof TextNode textNode) {
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    propertyNode.getValues().get(0).getSourceInfo(), propertyInfo,
                    textNode.getText(), textNode.isRawText());
            }

            throw PropertyAssignmentErrors.incompatiblePropertyType(
                propertyNode.getValues().get(0).getSourceInfo(), propertyInfo,
                TypeHelper.getTypeInstance(propertyNode.getValues().get(0)));
        }

        throw PropertyAssignmentErrors.incompatiblePropertyItems(propertyNode.getSourceInfo(), propertyInfo);
    }

    private @Nullable Node trySetValue(TransformContext context, @Nullable ValueNode node, PropertyInfo propertyInfo) {
        if (node == null || propertyInfo.isReadOnly()) {
            return null;
        }

        ValueEmitterNode value = createEventHandlerNode(context, node, propertyInfo.getType());
        if (value == null) {
            value = createTemplateContentNode(node, propertyInfo.getType());
            if (value == null) {
                value = createValueNode(
                    node, propertyInfo.getDeclaringType(), propertyInfo.getType());
            }
        }

        if (value != null) {
            if (propertyInfo.isStatic()) {
                return new EmitStaticPropertySetterNode(
                    propertyInfo.getDeclaringType(), propertyInfo, value, node.getSourceInfo());
            } else {
                return new EmitPropertySetterNode(propertyInfo, value, false, node.getSourceInfo());
            }
        }

        return null;
    }

    private Node tryAddValue(
            TransformContext context, PropertyNode propertyNode, PropertyInfo propertyInfo, TypeInstance declaringType) {
        boolean isMap = propertyInfo.getType().subtypeOf(MapType());
        if (!isMap && !propertyInfo.getType().subtypeOf(CollectionType())) {
            return null;
        }

        TypeInstance keyType, itemType;

        if (isMap) {
            List<TypeInstance> itemTypes = TypeHelper.getTypeArguments(propertyInfo.getType(), MapType());
            keyType = itemTypes.size() != 0 ? itemTypes.get(0) : TypeInstance.ObjectType();
            itemType = itemTypes.size() != 0 ? itemTypes.get(1) : TypeInstance.ObjectType();
        } else {
            List<TypeInstance> itemTypes = TypeHelper.getTypeArguments(propertyInfo.getType(), CollectionType());
            keyType = null;
            itemType = itemTypes.size() != 0 ? itemTypes.get(0) : TypeInstance.ObjectType();
        }

        List<ValueNode> keys = new ArrayList<>();
        List<ValueNode> values = new ArrayList<>();

        for (Node child : propertyNode.getValues()) {
            boolean error = false;
            TypeInstance childType = TypeHelper.getTypeInstance(child);

            if (!isMap && child instanceof TextNode textNode) {
                if (textNode.isRawText()) {
                    ValueNode valueNode = ValueEmitterFactory.newLiteralValue(
                        textNode.getText(), itemType, child.getSourceInfo());

                    if (valueNode == null) {
                        error = true;
                    } else {
                        values.add(valueNode);
                    }
                } else {
                    for (String value : textNode.getText().split(",|\\R")) {
                        if (value.isBlank()) {
                            continue;
                        }

                        ValueNode valueNode = ValueEmitterFactory.newLiteralValue(
                            value.trim(), itemType, child.getSourceInfo());

                        if (valueNode == null) {
                            error = true;
                            break;
                        }

                        values.add(valueNode);
                    }
                }
            } else if (itemType.isAssignableFrom(childType)) {
                if (isMap) {
                    if (child instanceof EmitLiteralNode
                            || child instanceof EmitObjectNode
                            || child instanceof EmitClassConstantNode) {
                        ValueNode key = tryCreateKey(context, (ValueEmitterNode)child, keyType.jvmType());
                        if (key == null) {
                            throw GeneralErrors.unsupportedMapKeyType(child.getSourceInfo(), propertyInfo);
                        }

                        keys.add(key);
                    } else {
                        throw GeneralErrors.cannotAddItemIncompatibleValue(
                            child.getSourceInfo(), declaringType.jvmType(), propertyNode.getMarkupName(),
                            child.getSourceInfo().getText());
                    }
                }

                values.add((ValueNode)child);
            } else {
                error = true;
            }

            if (error) {
                throw GeneralErrors.cannotAddItemIncompatibleType(
                    child.getSourceInfo(), propertyInfo, TypeHelper.getTypeInstance(child), itemType);
            }
        }

        return new EmitPropertyAdderNode(propertyInfo, keys, values, itemType, propertyNode.getSourceInfo());
    }

    private ValueNode tryCreateKey(TransformContext context, ValueEmitterNode node, CtClass keyType) {
        if (!TypeHelper.equals(keyType, StringType()) && !TypeHelper.equals(keyType, ObjectType())) {
            return null;
        }

        if (node instanceof ReferenceableNode refNode) {
            if (refNode.getId() != null) {
                return ValueEmitterFactory.newLiteralValue(
                    refNode.getId(), TypeInstance.StringType(), node.getSourceInfo());
            }
        }

        if (TypeHelper.equals(keyType, StringType())) {
            StringBuilder builder = new StringBuilder();
            for (Node parent : context.getParents()) {
                if (parent instanceof ValueNode) {
                    builder.append(((ValueNode)parent).getType().getMarkupName());
                }
            }

            String id = NameHelper.getUniqueName(
                UUID.nameUUIDFromBytes(builder.toString().getBytes()).toString(), this);

            return ValueEmitterFactory.newLiteralValue(id, TypeInstance.StringType(), node.getSourceInfo());
        }

        // The key of unnamed templates is their data item class literal, which is used by the
        // templating system to match templates to data items at runtime.
        TypeInstance nodeType = TypeHelper.getTypeInstance(node);
        if (Core.TemplateType() != null && nodeType.subtypeOf(Core.TemplateType())) {
            Resolver resolver = new Resolver(node.getSourceInfo());
            TypeInstance itemType = resolver.tryFindArgument(nodeType, Core.TemplateType());

            return new EmitLiteralNode(
                resolver.getTypeInstance(ClassType(), List.of(itemType)),
                itemType.getName(),
                node.getSourceInfo());
        }

        return EmitObjectNode
            .constructor(
                TypeInstance.ObjectType(),
                unchecked(node.getSourceInfo(), () -> ObjectType().getConstructor("()V")),
                Collections.emptyList(),
                node.getSourceInfo())
            .create();
    }

    private ValueEmitterNode createEventHandlerNode(TransformContext context, ValueNode node, TypeInstance targetType) {
        if (targetType.subtypeOf(EventHandlerType()) && node instanceof TextNode textNode) {
            String text = textNode.getText().trim();
            if (!text.startsWith("#")) {
                return null;
            }

            return new EmitEventHandlerNode(
                context.getCodeBehindOrMarkupClass(),
                targetType.getArguments().get(0),
                text.substring(1),
                textNode.getSourceInfo().getTrimmed());
        }

        return null;
    }

    private ValueEmitterNode createTemplateContentNode(ValueNode node, TypeInstance targetType) {
        if (node instanceof TemplateContentNode templateContentNode
                && Core.TemplateContentType() != null
                && targetType.subtypeOf(Core.TemplateContentType())) {
            return new EmitTemplateContentNode(
                targetType,
                templateContentNode.getItemType(),
                templateContentNode.getBindingContextClass(),
                (EmitObjectNode)templateContentNode.getContent(),
                node.getSourceInfo());
        }

        return null;
    }

    private ValueEmitterNode createValueNode(ValueNode node, TypeInstance declaringType, TypeInstance targetType) {
        TypeInstance valueType = TypeHelper.getTypeInstance(node);

        if (node instanceof TextNode textNode) {
            ValueEmitterNode coercedValue = ValueEmitterFactory.newLiteralValue(
                textNode.getText(), List.of(targetType, declaringType), targetType, node.getSourceInfo());

            if (coercedValue != null) {
                return coercedValue;
            }

            return ValueEmitterFactory.newObjectByCoercion(targetType, (TextNode)node);
        }

        return node instanceof ValueEmitterNode valueEmitterNode && targetType.isAssignableFrom(valueType) ?
            valueEmitterNode : null;
    }

}
