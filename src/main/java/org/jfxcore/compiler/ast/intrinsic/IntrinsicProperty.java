// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.function.Supplier;

public class IntrinsicProperty {

    private final String name;
    private final boolean isDefault;
    Intrinsic intrinsic;

    public IntrinsicProperty(String name, Supplier<TypeDeclaration> type) {
        this.name = name;
        this.isDefault = false;
    }

    public IntrinsicProperty(String name, Supplier<TypeDeclaration> type, boolean isDefault) {
        this.name = name;
        this.isDefault = isDefault;
    }

    public Intrinsic getIntrinsic() {
        return intrinsic;
    }

    public String getName() {
        return name;
    }

    public boolean isDefault() {
        return isDefault;
    }

}
