// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;

public enum ContextSelector {
    CONTEXT("context"),
    ELEMENT("element"),
    ROOT("root"),
    PARENT("parent");

    ContextSelector(String text) {
        this.text = text;
    }

    private final String text;

    public String getText() {
        return text;
    }

    public static @Nullable ContextSelector tryParse(String text) {
        return switch (text) {
            case "context" -> CONTEXT;
            case "element" -> ELEMENT;
            case "root" -> ROOT;
            case "parent" -> PARENT;
            default -> null;
        };
    }
}
