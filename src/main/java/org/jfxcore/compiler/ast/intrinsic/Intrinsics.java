// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import javassist.CtClass;
import org.jfxcore.compiler.ast.ObjectNode;
import org.jfxcore.compiler.util.Classes;
import java.util.List;

public class Intrinsics {

    public static final Intrinsic NULL = new Intrinsic(
        "null", Usage.ELEMENT);

    public static final Intrinsic CLASS = new Intrinsic(
        "class", Usage.ROOT_ATTRIBUTE);

    public static final Intrinsic CLASS_MODIFIER = new Intrinsic(
        "classModifier", Usage.ROOT_ATTRIBUTE);

    public static final Intrinsic CLASS_PARAMETERS = new Intrinsic(
        "classParameters", Usage.ROOT_ATTRIBUTE);

    public static final Intrinsic MARKUP_CLASS_NAME = new Intrinsic(
        "markupClassName", Usage.ROOT_ATTRIBUTE);

    public static final Intrinsic ID = new Intrinsic(
        "id", Usage.CHILD_ATTRIBUTE);

    public static final Intrinsic VALUE = new Intrinsic(
        "value", new Usage(true, false, true));

    public static final Intrinsic CONSTANT = new Intrinsic(
        "constant", new Usage(true, false, true));

    public static final Intrinsic FACTORY = new Intrinsic(
        "factory", Usage.CHILD_ATTRIBUTE);

    public static final Intrinsic TYPE_ARGUMENTS = new Intrinsic(
        "typeArguments", Usage.ATTRIBUTE);

    public static final Intrinsic ITEM_TYPE = new Intrinsic(
        "itemType", Usage.CHILD_ATTRIBUTE);

    public static final Intrinsic DEFINE = new Intrinsic(
        "define", Usage.ATTRIBUTE);

    public static final Intrinsic STYLESHEET = new Intrinsic(
        "stylesheet", Classes::StringType, Usage.ELEMENT);

    public static final Intrinsic TYPE = new Intrinsic(
        "type", Classes::ClassType, Usage.ELEMENT,
        new IntrinsicProperty("name", Classes::StringType, true));

    public static final Intrinsic URL = new Intrinsic(
        "url", Classes::URLType, Usage.ELEMENT,
        new IntrinsicProperty("value", Classes::StringType, true));

    public static final Intrinsic ONCE = new Intrinsic(
        "once", Usage.ELEMENT,
        new IntrinsicProperty("path", Classes::StringType, true),
        new IntrinsicProperty("content", () -> CtClass.booleanType));

    public static final Intrinsic BIND = new Intrinsic(
        "bind", Usage.ELEMENT,
        new IntrinsicProperty("path", Classes::StringType, true),
        new IntrinsicProperty("content", () -> CtClass.booleanType));

    public static final Intrinsic SYNC = new Intrinsic(
        "sync", Usage.ELEMENT,
        new IntrinsicProperty("path", Classes::StringType, true),
        new IntrinsicProperty("content", () -> CtClass.booleanType),
        new IntrinsicProperty("inverseMethod", Classes::StringType));

    private static final List<Intrinsic> NODES = List.of(
        NULL, CLASS, CLASS_MODIFIER, CLASS_PARAMETERS, MARKUP_CLASS_NAME, ID, VALUE, CONSTANT, FACTORY,
        TYPE_ARGUMENTS, ITEM_TYPE, DEFINE, STYLESHEET, TYPE, URL, ONCE, BIND, SYNC);

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
