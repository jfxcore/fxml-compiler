// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import javassist.CtClass;
import java.util.function.Supplier;

public class IntrinsicProperty {

    private final String name;
    private final boolean isDefault;

    public IntrinsicProperty(String name, Supplier<CtClass> type) {
        this.name = name;
        this.isDefault = false;
    }

    public IntrinsicProperty(String name, Supplier<CtClass> type, boolean isDefault) {
        this.name = name;
        this.isDefault = isDefault;
    }

    public String getName() {
        return name;
    }

    public boolean isDefault() {
        return isDefault;
    }

}
