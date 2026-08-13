// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.intrinsic;

import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.function.Supplier;

public class IntrinsicProperty {

    public enum Syntax {
        GENERIC,
        EXPRESSION,
        PATH_REFERENCE
    }

    private final String name;
    private final boolean isDefault;
    private final Syntax syntax;
    Intrinsic intrinsic;

    public IntrinsicProperty(String name, Supplier<TypeDeclaration> type, boolean isDefault) {
        this(name, type, isDefault, Syntax.GENERIC);
    }

    public IntrinsicProperty(String name, Supplier<TypeDeclaration> type, Syntax syntax) {
        this(name, type, false, syntax);
    }

    public IntrinsicProperty(String name, Supplier<TypeDeclaration> type, boolean isDefault, Syntax syntax) {
        this.name = name;
        this.isDefault = isDefault;
        this.syntax = syntax;
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

    public Syntax getSyntax() {
        return syntax;
    }
}
