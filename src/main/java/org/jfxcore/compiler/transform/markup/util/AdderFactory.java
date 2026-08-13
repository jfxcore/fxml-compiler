// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jfxcore.compiler.ast.AttributeValueNode;
import org.jfxcore.compiler.ast.LiteralValueNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitCollectionAdderNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitMapAdderNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.transform.TransformContext;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class AdderFactory {

    public static List<EmitCollectionAdderNode> newCollectionAdders(
            TransformContext context, ValueNode collection, ValueNode child) {
        if (!TypeHelper.getTypeInstance(collection).subtypeOf(CollectionDecl())) {
            throw new IllegalArgumentException();
        }

        List<TypeInstance> typeArgs = TypeHelper.getTypeInstance(collection).arguments();
        TypeInstance itemType = !typeArgs.isEmpty() ? typeArgs.get(0) : TypeInstance.ObjectType();

        List<? extends Node> items = child instanceof LiteralValueNode literal
            ? literal.hasCoercionParts() ? literal.getCoercionParts() : List.of(literal)
            : List.of(child);

        List<EmitCollectionAdderNode> adders = new ArrayList<>(items.size());
        TypeInstance collectionType = TypeHelper.getTypeInstance(collection);
        int parentsUnderInitialization = ValueEmitterFactory.getParentsUnderInitializationCount(context);

        for (Node item : items) {
            TargetValueResolver.TargetContext target =
                TargetValueResolver.TargetContext.collectionItem(
                    collectionType, itemType, collectionType,
                    parentsUnderInitialization, item.getSourceInfo());

            TargetValueResolver.ResolutionResult result = TargetValueResolver.resolve(context, item, target);
            if (result instanceof TargetValueResolver.ResolutionResult.Invalid invalid) {
                throw invalid.diagnostic();
            }

            if (result instanceof TargetValueResolver.ResolutionResult.NotApplicable notApplicable) {
                if (notApplicable.failure().diagnostic() != null) {
                    throw notApplicable.failure().diagnostic();
                }

                throw GeneralErrors.cannotAddItemIncompatibleType(
                    item.getSourceInfo(), collectionType,
                    notApplicable.failure().valueType() != null
                        ? Objects.requireNonNull(notApplicable.failure().valueType())
                        : TypeHelper.getTypeInstance(item),
                    itemType);
            }

            adders.add(new EmitCollectionAdderNode(
                ((TargetValueResolver.ResolutionResult.Applicable)result).plan().lowerValue()));
        }

        return adders;
    }

    public static EmitMapAdderNode newMapAdder(ValueNode map, ValueNode child) {
        if (!TypeHelper.getTypeInstance(map).subtypeOf(MapDecl())) {
            throw new IllegalArgumentException();
        }

        List<TypeInstance> typeArgs = TypeHelper.getTypeInstance(map).arguments();
        TypeInstance keyType = !typeArgs.isEmpty() ? typeArgs.get(0) : TypeInstance.ObjectType();
        TypeInstance itemType = !typeArgs.isEmpty() ? typeArgs.get(1) : TypeInstance.ObjectType();

        if (!(child instanceof ObjectNode)) {
            throw GeneralErrors.cannotAddItemIncompatibleValue(
                child.getSourceInfo(), TypeHelper.getTypeInstance(map), child.getSourceInfo().getText());
        }

        if (!TypeHelper.getTypeInstance(child).subtypeOf(itemType)) {
            throw GeneralErrors.cannotAddItemIncompatibleType(
                child.getSourceInfo(), TypeHelper.getTypeInstance(map), TypeHelper.getTypeInstance(child), itemType);
        }

        if (!keyType.equals(StringDecl()) && !keyType.equals(ObjectDecl())) {
            throw GeneralErrors.unsupportedMapKeyType(map.getSourceInfo(), TypeHelper.getTypeInstance(map));
        }

        return new EmitMapAdderNode(createKey((ObjectNode)child, keyType), child);
    }

    private static ValueNode createKey(ObjectNode node, TypeInstance keyType) {
        PropertyNode id = node.findIntrinsicProperty(Intrinsics.ID);
        if (id != null) {
            Node value = id.getValues().size() == 1 ? id.getValues().get(0) : null;
            if (value instanceof AttributeValueNode attributeValue
                    && attributeValue.getForm() == AttributeValueNode.Form.LITERAL) {
                value = attributeValue.getLiteral();
            }

            return new EmitLiteralNode(
                TypeInstance.StringType(),
                ((LiteralValueNode)value).getText(),
                node.getSourceInfo());
        }

        if (keyType.equals(StringDecl())) {
            return new EmitLiteralNode(
                TypeInstance.StringType(),
                NameHelper.getUniqueName(UUID.nameUUIDFromBytes(
                    TypeHelper.getTypeDeclaration(node).name().getBytes()).toString(), node),
                node.getSourceInfo());
        }

        return EmitObjectNode
            .constructor(
                TypeInstance.ObjectType(),
                ObjectDecl().requireConstructor(),
                Collections.emptyList(),
                node.getSourceInfo())
            .create();
    }
}
