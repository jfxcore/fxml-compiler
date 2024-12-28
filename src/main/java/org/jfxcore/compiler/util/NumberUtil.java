// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

public class NumberUtil {

    public static Number parse(String value) throws NumberFormatException {
        if (value.endsWith("L") || value.endsWith("l")) {
            return Long.parseLong(value.substring(0, value.length() - 1));
        }

        if (value.endsWith("D") || value.endsWith("d")) {
            return Double.parseDouble(value.substring(0, value.length() - 1));
        }

        if (value.endsWith("F") || value.endsWith("f")) {
            return Float.parseFloat(value.substring(0, value.length() - 1));
        }

        try {
            long number = Long.parseLong(value);
            if (number <= Integer.MAX_VALUE && number >= Integer.MIN_VALUE) {
                return (int)number;
            }

            return number;
        } catch (NumberFormatException ignored) {
        }

        return Double.parseDouble(value);
    }

    public static TypeInstance parseType(String value) throws NumberFormatException {
        Number number = parse(value);

        if (number instanceof Integer) {
            return TypeInstance.intType();
        }

        if (number instanceof Long) {
            return TypeInstance.longType();
        }

        if (number instanceof Float) {
            return TypeInstance.floatType();
        }

        if (number instanceof Double) {
            return TypeInstance.doubleType();
        }

        if (number instanceof Short) {
            return TypeInstance.shortType();
        }

        throw new IllegalArgumentException("value");
    }

}
