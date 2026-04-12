// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.type.KnownSymbols;
import java.util.List;

import static org.jfxcore.compiler.ast.intrinsic.Intrinsic.*;

public class Intrinsics {

    public static final Intrinsic SUBCLASS = new Intrinsic(
        "subclass", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic CLASS_MODIFIER = new Intrinsic(
        "classModifier", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic CLASS_NAME = new Intrinsic(
        "className", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic CLASS_PARAMETERS = new Intrinsic(
        "classParameters", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic CONTEXT = new Intrinsic(
        "context", Kind.PROPERTY, Placement.ROOT, KnownSymbols::ObjectDecl);

    public static final Intrinsic ID = new Intrinsic(
        "id", Kind.PROPERTY, Placement.ANY, KnownSymbols::StringDecl);

    public static final Intrinsic VALUE = new Intrinsic(
        "value", Kind.PROPERTY, Placement.NOT_ROOT, KnownSymbols::BottomTypeDecl);

    public static final Intrinsic CONSTANT = new Intrinsic(
        "constant", Kind.PROPERTY, Placement.NOT_ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic FACTORY = new Intrinsic(
        "factory", Kind.PROPERTY, Placement.NOT_ROOT, KnownSymbols::BottomTypeDecl);

    public static final Intrinsic TYPE_ARGUMENTS = new Intrinsic(
        "typeArguments", Kind.PROPERTY, Placement.ANY, KnownSymbols::StringDecl);

    public static final Intrinsic ITEM_TYPE = new Intrinsic(
        "itemType", Kind.PROPERTY, Placement.ANY, KnownSymbols::StringDecl);

    public static final Intrinsic DEFINE = new Intrinsic(
        "define", Kind.PROPERTY, Placement.ANY, KnownSymbols::BottomTypeDecl);

    public static final Intrinsic NULL = new Intrinsic(
        "Null", Kind.OBJECT, Placement.ANY, KnownSymbols::NullTypeDecl);

    public static final Intrinsic TRUE = new Intrinsic(
        "True", Kind.OBJECT, Placement.ANY, KnownSymbols::booleanDecl);

    public static final Intrinsic FALSE = new Intrinsic(
        "False", Kind.OBJECT, Placement.ANY, KnownSymbols::booleanDecl);

    public static final Intrinsic CLASS = new Intrinsic(
        "Class", Kind.OBJECT, Placement.ANY, KnownSymbols::ClassDecl,
        new IntrinsicProperty("name", KnownSymbols::StringDecl, true));

    public static final Intrinsic STYLESHEET = new Intrinsic(
        "Stylesheet", Kind.OBJECT, Placement.ANY, KnownSymbols::StringDecl);

    public static final Intrinsic EVALUATE = new Intrinsic(
        "Evaluate", Kind.OBJECT, Placement.ANY, KnownSymbols::BottomTypeDecl,
        new IntrinsicProperty("path", KnownSymbols::StringDecl, true));

    public static final Intrinsic OBSERVE = new Intrinsic(
        "Observe", Kind.OBJECT, Placement.ANY, KnownSymbols::BottomTypeDecl,
        new IntrinsicProperty("path", KnownSymbols::StringDecl, true));

    public static final Intrinsic SYNCHRONIZE = new Intrinsic(
        "Synchronize", Kind.OBJECT, Placement.ANY, KnownSymbols::BottomTypeDecl,
        new IntrinsicProperty("path", KnownSymbols::StringDecl, true),
        new IntrinsicProperty("format", KnownSymbols::FormatDecl),
        new IntrinsicProperty("converter", KnownSymbols::StringConverterDecl),
        new IntrinsicProperty("inverseMethod", KnownSymbols::StringDecl));

    private static final List<Intrinsic> NODES = List.of(
        SUBCLASS, CLASS_MODIFIER, CLASS_NAME, CLASS_PARAMETERS, CONTEXT, ID, VALUE, CONSTANT, FACTORY,
        TYPE_ARGUMENTS, ITEM_TYPE, DEFINE, NULL, TRUE, FALSE, CLASS, STYLESHEET, EVALUATE, OBSERVE, SYNCHRONIZE);

    public static Intrinsic find(ObjectNode node) {
        if (node.getType().isIntrinsic()){
            return find(node.getType().getName());
        }

        return null;
    }

    public static Intrinsic find(String name) {
        for (Intrinsic node : NODES) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        return null;
    }
}
