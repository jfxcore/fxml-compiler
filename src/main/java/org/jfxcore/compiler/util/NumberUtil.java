// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

public class NumberUtil {

    @SuppressWarnings("DuplicateExpressions")
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

        return Double.parseDouble(value.substring(0, value.length() - 1));
    }

}
