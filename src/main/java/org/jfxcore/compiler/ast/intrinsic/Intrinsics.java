// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.util.Classes;
import java.util.List;

import static org.jfxcore.compiler.ast.intrinsic.Intrinsic.*;

public class Intrinsics {

    public static final Intrinsic NULL = new Intrinsic(
        "null", Kind.OBJECT, Placement.NOT_ROOT, Classes::NullType);

    public static final Intrinsic CLASS = new Intrinsic(
        "class", Kind.PROPERTY, Placement.ROOT, Classes::StringType);

    public static final Intrinsic CLASS_MODIFIER = new Intrinsic(
        "classModifier", Kind.PROPERTY, Placement.ROOT, Classes::StringType);

    public static final Intrinsic CLASS_PARAMETERS = new Intrinsic(
        "classParameters", Kind.PROPERTY, Placement.ROOT, Classes::StringType);

    public static final Intrinsic MARKUP_CLASS_NAME = new Intrinsic(
        "markupClassName", Kind.PROPERTY, Placement.ROOT, Classes::StringType);

    public static final Intrinsic ID = new Intrinsic(
        "id", Kind.PROPERTY, Placement.ANY, Classes::StringType);

    public static final Intrinsic VALUE = new Intrinsic(
        "value", Kind.PROPERTY, Placement.NOT_ROOT, Classes::StringType);

    public static final Intrinsic CONSTANT = new Intrinsic(
        "constant", Kind.PROPERTY, Placement.NOT_ROOT, Classes::StringType);

    public static final Intrinsic FACTORY = new Intrinsic(
        "factory", Kind.PROPERTY, Placement.NOT_ROOT, Classes::BottomType);

    public static final Intrinsic TYPE_ARGUMENTS = new Intrinsic(
        "typeArguments", Kind.PROPERTY, Placement.ANY, Classes::StringType);

    public static final Intrinsic ITEM_TYPE = new Intrinsic(
        "itemType", Kind.PROPERTY, Placement.ANY, Classes::StringType);

    public static final Intrinsic DEFINE = new Intrinsic(
        "define", Kind.PROPERTY, Placement.ANY, Classes::BottomType);

    public static final Intrinsic STYLESHEET = new Intrinsic(
        "stylesheet", Kind.OBJECT, Placement.ANY, Classes::StringType);

    public static final Intrinsic TYPE = new Intrinsic(
        "type", Kind.OBJECT, Placement.ANY, Classes::ClassType,
        new IntrinsicProperty("name", Classes::StringType, true));

    public static final Intrinsic URL = new Intrinsic(
        "url", Kind.OBJECT, Placement.ANY, Classes::URLType,
        new IntrinsicProperty("value", Classes::StringType, true));

    public static final Intrinsic ONCE = new Intrinsic(
        "once", Kind.OBJECT, Placement.ANY, Classes::BottomType,
        new IntrinsicProperty("path", Classes::StringType, true));

    public static final Intrinsic CONTENT = new Intrinsic(
        "content", Kind.OBJECT, Placement.ANY, Classes::BottomType,
        new IntrinsicProperty("path", Classes::StringType, true));

    public static final Intrinsic BIND = new Intrinsic(
        "bind", Kind.OBJECT, Placement.ANY, Classes::BottomType,
        new IntrinsicProperty("path", Classes::StringType, true));

    public static final Intrinsic BIND_CONTENT = new Intrinsic(
        "bindContent", Kind.OBJECT, Placement.ANY, Classes::BottomType,
        new IntrinsicProperty("path", Classes::StringType, true));

    public static final Intrinsic BIND_BIDIRECTIONAL = new Intrinsic(
        "bindBidirectional", Kind.OBJECT, Placement.ANY, Classes::BottomType,
        new IntrinsicProperty("path", Classes::StringType, true),
        new IntrinsicProperty("format", Classes::FormatType),
        new IntrinsicProperty("converter", Classes::StringConverterType),
        new IntrinsicProperty("inverseMethod", Classes::StringType));

    public static final Intrinsic BIND_CONTENT_BIDIRECTIONAL = new Intrinsic(
        "bindContentBidirectional", Kind.OBJECT, Placement.ANY, Classes::BottomType,
        new IntrinsicProperty("path", Classes::StringType, true));

    private static final List<Intrinsic> NODES = List.of(
        NULL, CLASS, CLASS_MODIFIER, CLASS_PARAMETERS, MARKUP_CLASS_NAME, ID, VALUE, CONSTANT, FACTORY,
        TYPE_ARGUMENTS, ITEM_TYPE, DEFINE, STYLESHEET, TYPE, URL, ONCE, CONTENT, BIND, BIND_CONTENT,
        BIND_BIDIRECTIONAL, BIND_CONTENT_BIDIRECTIONAL);

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
