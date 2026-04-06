// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.parse;

import java.util.Locale;
import java.util.Map;

public enum FxmlNamespace {

    JAVAFX("http://javafx.com/javafx"),
    FXML("http://jfxcore.org/fxml/2.0");

    FxmlNamespace(String namespace) {
        this.namespace = namespace;
    }

    public static Map<String, String> getDefaultMap() {
        return Map.of("", JAVAFX.namespace, "fx", FXML.namespace);
    }

    public boolean isParentOf(String s) {
        boolean res = s.toLowerCase(Locale.ROOT).startsWith(namespace);
        if (res && s.length() > namespace.length()) {
            return s.charAt(namespace.length()) == '/';
        }

        return res;
    }

    public boolean equalsIgnoreCase(String s) {
        s = s.toLowerCase(Locale.ROOT);
        return s.equals(namespace) || s.equals(namespace + "/");
    }

    @Override
    public String toString() {
        return namespace;
    }

    private final String namespace;
}
