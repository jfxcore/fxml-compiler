// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

public class AdderFactory {

    public static List<EmitCollectionAdderNode> newCollectionAdders(ValueNode collection, ValueNode child) {
        if (!TypeHelper.getTypeInstance(collection).subtypeOf(Classes.CollectionType())) {
            throw new IllegalArgumentException();
        }

        List<TypeInstance> typeArgs = TypeHelper.getTypeInstance(collection).getArguments();
        TypeInstance itemType = typeArgs.size() > 0 ? typeArgs.get(0) : TypeInstance.ObjectType();

        if (child instanceof TextNode textNode) {
            if (textNode.isRawText()) {
                ValueEmitterNode value = ValueEmitterFactory.newLiteralValue(
                    textNode.getText(), itemType, child.getSourceInfo());

                if (value == null) {
                    throw GeneralErrors.cannotAddItemIncompatibleType(
                        child.getSourceInfo(),
                        TypeHelper.getJvmType(collection),
                        TypeHelper.getJvmType(child),
                        itemType.jvmType());
                }

                return List.of(new EmitCollectionAdderNode(value));
            }

            List<EmitCollectionAdderNode> adders = new ArrayList<>();

            for (String part : textNode.getText().split(",|\\R")) {
                if (part.isBlank()) {
                    continue;
                }

                ValueEmitterNode value = ValueEmitterFactory.newLiteralValue(
                    part.trim(), itemType, child.getSourceInfo());

                if (value == null) {
                    throw GeneralErrors.cannotAddItemIncompatibleType(
                        child.getSourceInfo(),
                        TypeHelper.getJvmType(collection),
                        TypeHelper.getJvmType(child),
                        itemType.jvmType());
                }

                adders.add(new EmitCollectionAdderNode(value));
            }

            return adders;
        } else if (!(child instanceof ObjectNode)) {
            throw GeneralErrors.cannotAddItemIncompatibleValue(
                child.getSourceInfo(), TypeHelper.getJvmType(collection), child.getSourceInfo().getText());
        } else if (!TypeHelper.getTypeInstance(child).subtypeOf(itemType)) {
            throw GeneralErrors.cannotAddItemIncompatibleType(
                child.getSourceInfo(), TypeHelper.getJvmType(collection), TypeHelper.getJvmType(child), itemType.jvmType());
        }

        return List.of(new EmitCollectionAdderNode(child));
    }

    public static EmitMapAdderNode newMapAdder(ValueNode map, ValueNode child) {
        if (!TypeHelper.getTypeInstance(map).subtypeOf(Classes.MapType())) {
            throw new IllegalArgumentException();
        }

        List<TypeInstance> typeArgs = TypeHelper.getTypeInstance(map).getArguments();
        TypeInstance keyType = typeArgs.size() > 0 ? typeArgs.get(0) : TypeInstance.ObjectType();
        TypeInstance itemType = typeArgs.size() > 0 ? typeArgs.get(1) : TypeInstance.ObjectType();

        if (!(child instanceof ObjectNode)) {
            throw GeneralErrors.cannotAddItemIncompatibleValue(
                child.getSourceInfo(), TypeHelper.getJvmType(map), child.getSourceInfo().getText());
        }

        if (!TypeHelper.getTypeInstance(child).subtypeOf(itemType)) {
            throw GeneralErrors.cannotAddItemIncompatibleType(
                child.getSourceInfo(), TypeHelper.getJvmType(map), TypeHelper.getJvmType(child), itemType.jvmType());
        }

        if (!keyType.equals(Classes.StringType()) && !keyType.equals(Classes.ObjectType())) {
            throw GeneralErrors.unsupportedMapKeyType(map.getSourceInfo(), TypeHelper.getJvmType(map));
        }

        return new EmitMapAdderNode(createKey((ObjectNode)child, keyType), child);
    }

    private static ValueNode createKey(ObjectNode node, TypeInstance keyType) {
        PropertyNode id = node.findIntrinsicProperty(Intrinsics.ID);
        if (id != null) {
            return ValueEmitterFactory.newLiteralValue(
                ((TextNode)id.getValues().get(0)).getText(),
                TypeInstance.StringType(),
                node.getSourceInfo());
        }

        if (keyType.equals(Classes.StringType())) {
            return ValueEmitterFactory.newLiteralValue(
                NameHelper.getUniqueName(
                    UUID.nameUUIDFromBytes(TypeHelper.getJvmType(node).getName().getBytes()).toString(), node),
                TypeInstance.StringType(),
                node.getSourceInfo());
        }

        return EmitObjectNode
            .constructor(
                TypeInstance.ObjectType(),
                unchecked(node.getSourceInfo(), () -> Classes.ObjectType().getConstructor("()V")),
                Collections.emptyList(),
                node.getSourceInfo())
            .create();
    }

}
