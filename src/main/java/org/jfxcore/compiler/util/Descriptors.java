// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.CtClass;
import javassist.bytecode.Descriptor;

public class Descriptors {

    public static String types(String... types) {
        StringBuilder stringBuilder = new StringBuilder();

        for (String type : types) {
            stringBuilder.append(Descriptor.of(type));
        }

        return stringBuilder.toString();
    }

    public static String types(CtClass... types) {
        StringBuilder stringBuilder = new StringBuilder();

        for (CtClass type : types) {
            stringBuilder.append(Descriptor.of(type));
        }

        return stringBuilder.toString();
    }

    public static String types(Iterable<CtClass> types) {
        StringBuilder stringBuilder = new StringBuilder();

        for (CtClass type : types) {
            stringBuilder.append(Descriptor.of(type));
        }

        return stringBuilder.toString();
    }

    public static String function(CtClass ret, CtClass... args) {
        if (args.length == 0) {
            return "()" + Descriptor.of(ret);
        }

        return "(" + Descriptors.types(args) + ")" + Descriptor.of(ret);
    }

    public static String constructor(CtClass... args) {
        if (args.length == 0) {
            return "()V";
        }

        return "(" + Descriptors.types(args) + ")V";
    }

}
