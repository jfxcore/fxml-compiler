// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression;

public enum BindingContextSelector {
    STATIC(null),
    DEFAULT(""),
    SELF("self"),
    PARENT("parent"),
    TEMPLATED_ITEM("item");

    BindingContextSelector(String name) {
        this.name = name;
    }

    private final String name;

    public String getName() {
        return name;
    }

    public static BindingContextSelector parse(String name) {
        for (BindingContextSelector selector : values()) {
            if (selector.name != null && selector.name.equals(name)) {
                return selector;
            }
        }

        throw new IllegalArgumentException("name");
    }
}
