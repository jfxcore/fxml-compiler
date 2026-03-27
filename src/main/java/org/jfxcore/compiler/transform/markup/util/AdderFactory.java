// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.transform.markup.util;

import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.ast.PropertyNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitCollectionAdderNode;
import org.jfxcore.compiler.ast.emit.EmitMapAdderNode;
import org.jfxcore.compiler.ast.emit.EmitObjectNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.intrinsic.Intrinsics;
import org.jfxcore.compiler.ast.text.ListNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class AdderFactory {

    public static List<EmitCollectionAdderNode> newCollectionAdders(ValueNode collection, ValueNode child) {
        if (!TypeHelper.getTypeInstance(collection).subtypeOf(CollectionDecl())) {
            throw new IllegalArgumentException();
        }

        List<TypeInstance> typeArgs = TypeHelper.getTypeInstance(collection).arguments();
        TypeInstance itemType = !typeArgs.isEmpty() ? typeArgs.get(0) : TypeInstance.ObjectType();

        if (child instanceof ListNode listNode) {
            List<EmitCollectionAdderNode> adders = new ArrayList<>();

            for (ValueNode item : listNode.getValues()) {
                boolean error = false;

                if (item instanceof TextNode textItem) {
                    ValueEmitterNode value = ValueEmitterFactory.newLiteralValue(
                        textItem, itemType, item.getSourceInfo());

                    if (value == null) {
                        error = true;
                    } else {
                        adders.add(new EmitCollectionAdderNode(value));
                    }
                } else {
                    error = true;
                }

                if (error) {
                    throw GeneralErrors.cannotAddItemIncompatibleType(
                        item.getSourceInfo(),
                        TypeHelper.getTypeInstance(collection),
                        TypeHelper.getTypeInstance(item),
                        itemType);
                }
            }

            return adders;
        }

        if (child instanceof TextNode textNode) {
            ValueEmitterNode value = ValueEmitterFactory.newLiteralValue(
                textNode, itemType, child.getSourceInfo());

            if (value == null) {
                throw GeneralErrors.cannotAddItemIncompatibleType(
                    child.getSourceInfo(),
                    TypeHelper.getTypeInstance(collection),
                    TypeHelper.getTypeInstance(child),
                    itemType);
            }

            return List.of(new EmitCollectionAdderNode(value));
        }

        if (!(child instanceof ObjectNode)) {
            throw GeneralErrors.cannotAddItemIncompatibleValue(
                child.getSourceInfo(), TypeHelper.getTypeInstance(collection), child.getSourceInfo().getText());
        }

        if (!TypeHelper.getTypeInstance(child).subtypeOf(itemType)) {
            throw GeneralErrors.cannotAddItemIncompatibleType(
                child.getSourceInfo(),
                TypeHelper.getTypeInstance(collection),
                TypeHelper.getTypeInstance(child),
                itemType);
        }

        return List.of(new EmitCollectionAdderNode(child));
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
            return ValueEmitterFactory.newLiteralValue(
                (TextNode)id.getValues().get(0),
                TypeInstance.StringType(),
                node.getSourceInfo());
        }

        if (keyType.equals(StringDecl())) {
            return ValueEmitterFactory.newLiteralValue(
                NameHelper.getUniqueName(
                    UUID.nameUUIDFromBytes(TypeHelper.getTypeDeclaration(node).name().getBytes()).toString(), node),
                TypeInstance.StringType(),
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
