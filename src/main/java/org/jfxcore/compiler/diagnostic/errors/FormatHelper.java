// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.diagnostic.errors;

import javassist.CtClass;
import org.jfxcore.compiler.util.PropertyInfo;

class FormatHelper {

    static String formatPropertyName(PropertyInfo propertyInfo) {
        String propertyName = propertyInfo.getName();
        if (propertyName.contains(".")) {
            return propertyInfo.getDeclaringType().getSimpleName() + ".(" + propertyName + ")";
        }

        return propertyInfo.getDeclaringType().getSimpleName() + "." + propertyName;
    }

    static String formatPropertyName(CtClass declaringClass, String name) {
        if (name.contains(".")) {
            return declaringClass.getSimpleName() + ".(" + name + ")";
        }

        return declaringClass.getSimpleName() + "." + name;
    }

    static String formatPropertyName(String declaring, String name) {
        if (name.contains(".")) {
            return declaring + ".(" + name + ")";
        }

        return declaring + "." + name;
    }

}
