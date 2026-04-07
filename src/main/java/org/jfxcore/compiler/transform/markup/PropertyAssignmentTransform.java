// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup;

import org.jfxcore.compiler.ast.BindingNode;
import org.jfxcore.compiler.ast.ContextNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.TemplateContentNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitApplyMarkupExtensionNode;
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
import org.jfxcore.compiler.ast.emit.EmitterNode;
import org.jfxcore.compiler.ast.emit.ReferenceableNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.PropertyAssignmentErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.Transform;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.transform.markup.util.BindingEmitterFactory;
import org.jfxcore.compiler.transform.markup.util.MarkupExtensionInfo;
import org.jfxcore.compiler.transform.markup.util.ValueEmitterFactory;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.type.KnownSymbols;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.jfxcore.compiler.type.KnownSymbols.*;

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
        PropertyInfo targetProperty = resolver.tryResolveProperty(
            declaringType, propertyNode.isAllowQualifiedName(), propertyNode.getNames());

        // A property assignment of the form foo.bar.baz="some value" must be resolved to a chain of
        // getter nodes until we arrive at the last path segment.
        if (targetProperty == null && propertyNode.getNames().length > 1
                && Arrays.stream(propertyNode.getNames()).allMatch(s -> Character.isLowerCase(s.charAt(0)))) {
            SourceInfo sourceInfo = propertyNode.getSourceInfo();
            String[] names = propertyNode.getNames();
            List<ValueEmitterNode> nodes = new ArrayList<>();

            for (int i = 0; i < names.length - 1; ++i) {
                targetProperty = resolver.tryResolveProperty(declaringType, false, names[i]);
                if (targetProperty == null) {
                    // If we fail to resolve the first segment, format the error message to include the
                    // entire chain of names. This makes for a better diagnostic in case the user meant
                    // to specify the name of a static property that couldn't be resolved.
                    String name = i == 0 ? propertyNode.getName() : names[i];
                    throw SymbolResolutionErrors.propertyNotFound(sourceInfo, declaringType.declaration(), name);
                }

                boolean hasGetter = targetProperty.getGetter() != null;

                ValueEmitterNode emitter = new EmitInvokeGetterNode(
                    targetProperty.getGetterOrPropertyGetter(),
                    hasGetter ? targetProperty.getType() : targetProperty.getObservableType(),
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

        if (targetProperty == null) {
            if (propertyNode.isAllowQualifiedName() && propertyNode.getNames().length > 1) {
                String[] names = propertyNode.getNames();
                String className = String.join(".", Arrays.copyOf(names, names.length - 1));
                TypeDeclaration type = resolver.tryResolveClassAgainstImports(className);

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
                propertyNode.getSourceInfo(), declaringType.declaration(), propertyNode.getName());
        }

        if (propertyNode.getValues().isEmpty()) {
            throw PropertyAssignmentErrors.propertyCannotBeEmpty(
                propertyNode.getSourceInfo(), declaringType.declaration(), propertyNode.getMarkupName());
        }

        MarkupException storedException = null;

        if (propertyNode.getValues().size() == 1) {
            Node child = propertyNode.getValues().get(0);

            if (child instanceof BindingNode bindingNode) {
                return BindingEmitterFactory.createBindingEmitter(context, propertyNode, bindingNode, targetProperty);
            }

            if (child instanceof ValueNode valueNode) {
                ValueAssignmentResolution res = resolveValueAssignment(context, valueNode, targetProperty);

                if (res instanceof ValueAssignmentResolution.Assign assign) {
                    return assign.node();
                }

                if (res instanceof ValueAssignmentResolution.Error error) {
                    storedException = error.error();
                }
            }
        }

        Node result = tryAddValue(context, propertyNode, targetProperty, declaringType);
        if (result != null) {
            return result;
        }

        if (storedException != null) {
            throw storedException;
        }

        if (propertyNode.getValues().size() > 1) {
            throw PropertyAssignmentErrors.propertyCannotHaveMultipleValues(
                propertyNode.getSourceInfo(), declaringType.declaration(), propertyNode.getMarkupName());
        }

        if (targetProperty.isReadOnly()) {
            throw PropertyAssignmentErrors.cannotModifyReadOnlyProperty(propertyNode.getSourceInfo(), targetProperty);
        }

        if (propertyNode.getValues().size() == 1) {
            if (propertyNode.getValues().get(0) instanceof TextNode textNode) {
                throw PropertyAssignmentErrors.cannotCoercePropertyValue(
                    propertyNode.getValues().get(0).getSourceInfo(), targetProperty,
                    textNode.getText(), textNode.isRawText());
            }

            throw PropertyAssignmentErrors.incompatiblePropertyType(
                propertyNode.getValues().get(0).getSourceInfo(), targetProperty,
                TypeHelper.getTypeInstance(propertyNode.getValues().get(0)));
        }

        throw PropertyAssignmentErrors.incompatiblePropertyItems(propertyNode.getSourceInfo(), targetProperty);
    }

    private ValueAssignmentResolution resolveValueAssignment(
            TransformContext context, ValueNode node, PropertyInfo propertyInfo) {
        ValueNode value = null;
        MarkupException exception = null;
        MarkupExtensionResolution res = resolveMarkupExtension(node, propertyInfo, propertyInfo.getType());

        if (res instanceof MarkupExtensionResolution.ProvideValue provideValue) {
            if (propertyInfo.isReadOnly()) {
                // Value-producing extensions cannot mutate a read-only property directly.
                // Let collection/map properties fall through to the add-item path instead.
                return new ValueAssignmentResolution.NotHandled();
            }

            value = provideValue.value();
        } else if (res instanceof MarkupExtensionResolution.ConsumeProperty consumeProperty) {
            return new ValueAssignmentResolution.Assign(consumeProperty.node());
        } else if (res instanceof MarkupExtensionResolution.Error error) {
            exception = error.error();
        } else if (propertyInfo.isReadOnly()) {
            // Only markup extensions that apply directly to the target property can be used with
            // read-only properties. Value-producing extensions must fall through so collection
            // properties can add items instead of attempting to invoke a setter.
            return new ValueAssignmentResolution.NotHandled();
        }

        if (value == null) {
            value = createEventHandlerNode(context, node, propertyInfo.getType());
            if (value == null) {
                value = createTemplateContentNode(node, propertyInfo.getType());
                if (value == null) {
                    value = createValueNode(
                        node, propertyInfo.getDeclaringType(), propertyInfo.getType());
                }
            }
        }

        if (value != null) {
            if (propertyInfo.isStatic()) {
                return new ValueAssignmentResolution.Assign(new EmitStaticPropertySetterNode(
                    propertyInfo.getDeclaringType(), propertyInfo, value, node.getSourceInfo()));
            } else {
                return new ValueAssignmentResolution.Assign(new EmitPropertySetterNode(
                    propertyInfo, value, false, node.getSourceInfo()));
            }
        }

        return exception != null
            ? new ValueAssignmentResolution.Error(exception)
            : new ValueAssignmentResolution.NotHandled();
    }

    private Node tryAddValue(TransformContext context,
                             PropertyNode propertyNode,
                             PropertyInfo targetProperty,
                             TypeInstance declaringType) {
        boolean isMap = targetProperty.getType().subtypeOf(MapDecl());
        if (!isMap && !targetProperty.getType().subtypeOf(CollectionDecl())) {
            return null;
        }

        TypeInstance keyType, itemType;

        if (isMap) {
            List<TypeInstance> itemTypes = TypeHelper.getTypeArguments(targetProperty.getType(), MapDecl());
            keyType = itemTypes.isEmpty() ? TypeInstance.ObjectType() : itemTypes.get(0);
            itemType = itemTypes.isEmpty() ? TypeInstance.ObjectType() : itemTypes.get(1);
        } else {
            List<TypeInstance> itemTypes = TypeHelper.getTypeArguments(targetProperty.getType(), CollectionDecl());
            keyType = null;
            itemType = itemTypes.isEmpty() ? TypeInstance.ObjectType() : itemTypes.get(0);
        }

        List<ValueNode> keys = new ArrayList<>();
        List<ValueNode> values = new ArrayList<>();

        for (Node child : propertyNode.getValues()) {
            boolean error = false;

            MarkupExtensionResolution result = resolveMarkupExtension(child, targetProperty, itemType);

            if (result instanceof MarkupExtensionResolution.ProvideValue provideValue) {
                child = provideValue.value();
            } else if (result instanceof MarkupExtensionResolution.Error err) {
                throw err.error();
            } else if (result instanceof MarkupExtensionResolution.ConsumeProperty) {
                throw GeneralErrors.cannotAddItemIncompatibleValue(
                    child.getSourceInfo(), declaringType.declaration(), propertyNode.getMarkupName(),
                    child.getSourceInfo().getText());
            }

            TypeInstance childType = TypeHelper.getTypeInstance(child);

            if (!isMap && child instanceof TextNode textNode) {
                if (textNode instanceof ListNode listNode) {
                    for (ValueNode item : listNode.getValues()) {
                        if (item instanceof TextNode textItem) {
                            ValueNode valueNode = ValueEmitterFactory.newLiteralValue(
                                textItem, itemType, item.getSourceInfo());

                            if (valueNode == null) {
                                error = true;
                                break;
                            }

                            values.add(valueNode);
                        } else {
                            error = true;
                            break;
                        }
                    }
                } else {
                    ValueNode valueNode = ValueEmitterFactory.newLiteralValue(
                        textNode, itemType, child.getSourceInfo());

                    if (valueNode == null) {
                        error = true;
                    } else {
                        values.add(valueNode);
                    }
                }
            } else if (itemType.isAssignableFrom(childType)) {
                if (isMap) {
                    if (child instanceof EmitLiteralNode
                            || child instanceof EmitObjectNode
                            || child instanceof EmitClassConstantNode) {
                        ValueNode key = tryCreateKey(context, (ValueEmitterNode)child, keyType);
                        if (key == null) {
                            throw GeneralErrors.unsupportedMapKeyType(child.getSourceInfo(), targetProperty);
                        }

                        keys.add(key);
                    } else {
                        throw GeneralErrors.cannotAddItemIncompatibleValue(
                            child.getSourceInfo(), declaringType.declaration(), propertyNode.getMarkupName(),
                            child.getSourceInfo().getText());
                    }
                }

                values.add((ValueNode)child);
            } else {
                error = true;
            }

            if (error) {
                throw GeneralErrors.cannotAddItemIncompatibleType(
                    child.getSourceInfo(), targetProperty, TypeHelper.getTypeInstance(child), itemType);
            }
        }

        return new EmitPropertyAdderNode(targetProperty, keys, values, itemType, propertyNode.getSourceInfo());
    }

    private ValueNode tryCreateKey(TransformContext context, ValueEmitterNode node, TypeInstance keyType) {
        if (!keyType.equals(StringDecl()) && !keyType.equals(ObjectDecl())) {
            return null;
        }

        if (node instanceof ReferenceableNode refNode) {
            if (refNode.getId() != null) {
                return ValueEmitterFactory.newLiteralValue(
                    refNode.getId(), TypeInstance.StringType(), node.getSourceInfo());
            }
        }

        if (keyType.equals(StringDecl())) {
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
        if (Core.TemplateDecl() != null && nodeType.subtypeOf(Core.TemplateDecl())) {
            Resolver resolver = new Resolver(node.getSourceInfo());
            TypeInstance itemType = resolver.tryFindArgument(nodeType, Core.TemplateDecl());

            return new EmitLiteralNode(
                new TypeInvoker(node.getSourceInfo()).invokeType(ClassDecl(), List.of(itemType)),
                itemType.name(),
                node.getSourceInfo());
        }

        return EmitObjectNode
            .constructor(
                TypeInstance.ObjectType(),
                KnownSymbols.ObjectDecl().requireDeclaredConstructor(),
                Collections.emptyList(),
                node.getSourceInfo())
            .create();
    }

    private MarkupExtensionResolution resolveMarkupExtension(
            Node node, PropertyInfo targetProperty, TypeInstance targetType) {
        if (!(node instanceof ValueEmitterNode valueEmitterNode)) {
            return new MarkupExtensionResolution.None();
        }

        var propertyConsumerInfo = MarkupExtensionInfo.of(node, MarkupExtensionInfo.PropertyConsumer.class);
        if (propertyConsumerInfo != null
                && targetProperty.isObservable()
                && propertyConsumerInfo.propertyType().isAssignableFrom(targetProperty.getObservableType())) {
            return new MarkupExtensionResolution.ConsumeProperty(new EmitApplyMarkupExtensionNode(
                valueEmitterNode, propertyConsumerInfo.markupExtensionInterface(), targetProperty.getName(),
                targetType, TypeInstance.voidType(), targetProperty));
        }

        var supplierInfo = MarkupExtensionInfo.of(node, MarkupExtensionInfo.Supplier.class);
        if (supplierInfo != null) {
            if (supplierInfo.providedTypes().stream().noneMatch(targetType::isAssignableFrom)) {
                return new MarkupExtensionResolution.Error(PropertyAssignmentErrors.markupExtensionNotApplicable(
                    node.getSourceInfo(), targetProperty, TypeHelper.getTypeDeclaration(node),
                    supplierInfo.providedTypes().toArray(TypeInstance[]::new)));
            }

            return new MarkupExtensionResolution.ProvideValue(new EmitApplyMarkupExtensionNode.Supplier(
                valueEmitterNode, supplierInfo.markupExtensionInterface(), targetProperty.getName(),
                targetType, supplierInfo.returnType(), targetProperty));
        }

        if (propertyConsumerInfo != null) {
            return new MarkupExtensionResolution.Error(PropertyAssignmentErrors.markupExtensionNotApplicable(
                node.getSourceInfo(), targetProperty, TypeHelper.getTypeDeclaration(node),
                new TypeInstance[] {propertyConsumerInfo.propertyType()}));
        }

        return new MarkupExtensionResolution.None();
    }

    private ValueEmitterNode createEventHandlerNode(TransformContext context, ValueNode node, TypeInstance targetType) {
        if (targetType.subtypeOf(EventHandlerDecl()) && node instanceof TextNode textNode) {
            String text = textNode.getText().trim();
            if (!text.startsWith("#")) {
                return null;
            }

            return new EmitEventHandlerNode(
                context.getCodeBehindOrMarkupClass(),
                targetType.arguments().get(0),
                text.substring(1),
                textNode.getSourceInfo().getTrimmed());
        }

        return null;
    }

    private ValueEmitterNode createTemplateContentNode(ValueNode node, TypeInstance targetType) {
        if (node instanceof TemplateContentNode templateContentNode
                && Core.TemplateContentDecl() != null
                && targetType.subtypeOf(Core.TemplateContentDecl())) {
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
                textNode, List.of(targetType, declaringType), targetType, node.getSourceInfo());

            if (coercedValue != null) {
                return coercedValue;
            }

            return ValueEmitterFactory.newObjectByCoercion(targetType, textNode);
        }

        return node instanceof ValueEmitterNode valueEmitterNode && targetType.isAssignableFrom(valueType) ?
            valueEmitterNode : null;
    }

    private sealed interface ValueAssignmentResolution {
        record NotHandled() implements ValueAssignmentResolution {}
        record Assign(EmitterNode node) implements ValueAssignmentResolution {}
        record Error(MarkupException error) implements ValueAssignmentResolution {}
    }

    private sealed interface MarkupExtensionResolution {
        record None() implements MarkupExtensionResolution {}
        record ProvideValue(ValueEmitterNode value) implements MarkupExtensionResolution {}
        record ConsumeProperty(EmitterNode node) implements MarkupExtensionResolution {}
        record Error(MarkupException error) implements MarkupExtensionResolution {}
    }
}
