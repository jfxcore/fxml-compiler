// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.type.KnownSymbols;
import java.util.List;

import static org.jfxcore.compiler.ast.intrinsic.Intrinsic.*;

public class Intrinsics {

    public static final Intrinsic NULL = new Intrinsic(
        "null", Kind.OBJECT, Placement.NOT_ROOT, KnownSymbols::NullTypeDecl);

    public static final Intrinsic CLASS = new Intrinsic(
        "class", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic CLASS_MODIFIER = new Intrinsic(
        "classModifier", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic CLASS_PARAMETERS = new Intrinsic(
        "classParameters", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

    public static final Intrinsic MARKUP_CLASS_NAME = new Intrinsic(
        "markupClassName", Kind.PROPERTY, Placement.ROOT, KnownSymbols::StringDecl);

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

    public static final Intrinsic STYLESHEET = new Intrinsic(
        "stylesheet", Kind.OBJECT, Placement.ANY, KnownSymbols::StringDecl);

    public static final Intrinsic TYPE = new Intrinsic(
        "type", Kind.OBJECT, Placement.ANY, KnownSymbols::ClassDecl,
        new IntrinsicProperty("name", KnownSymbols::StringDecl, true));

    public static final Intrinsic EVALUATE = new Intrinsic(
        "evaluate", Kind.OBJECT, Placement.ANY, KnownSymbols::BottomTypeDecl,
        new IntrinsicProperty("path", KnownSymbols::StringDecl, true));

    public static final Intrinsic OBSERVE = new Intrinsic(
        "observe", Kind.OBJECT, Placement.ANY, KnownSymbols::BottomTypeDecl,
        new IntrinsicProperty("path", KnownSymbols::StringDecl, true));

    public static final Intrinsic SYNCHRONIZE = new Intrinsic(
        "synchronize", Kind.OBJECT, Placement.ANY, KnownSymbols::BottomTypeDecl,
        new IntrinsicProperty("path", KnownSymbols::StringDecl, true),
        new IntrinsicProperty("format", KnownSymbols::FormatDecl),
        new IntrinsicProperty("converter", KnownSymbols::StringConverterDecl),
        new IntrinsicProperty("inverseMethod", KnownSymbols::StringDecl));

    private static final List<Intrinsic> NODES = List.of(
        NULL, CLASS, CLASS_MODIFIER, CLASS_PARAMETERS, MARKUP_CLASS_NAME, CONTEXT, ID, VALUE, CONSTANT,
        FACTORY, TYPE_ARGUMENTS, ITEM_TYPE, DEFINE, STYLESHEET, TYPE, EVALUATE, OBSERVE, SYNCHRONIZE);

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
