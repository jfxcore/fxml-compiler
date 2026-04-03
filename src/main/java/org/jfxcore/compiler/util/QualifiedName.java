// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.nio.file.Path;
import java.util.Arrays;

public record QualifiedName(String[] parts) {

    public static QualifiedName of(String name) {
        return new QualifiedName(name.split("\\."));
    }

    public static QualifiedName ofPath(Path path) {
        String[] parts = new String[path.getNameCount()];

        for (int i = 0; i < path.getNameCount(); i++) {
            String part = path.getName(i).toString();

            if (i < path.getNameCount() - 1) {
                parts[i] = part;
            } else {
                int index = part.lastIndexOf('.');
                parts[i] = index > 0 ? part.substring(0, index) : part;
            }
        }

        return new QualifiedName(parts);
    }

    public String fullName() {
        return String.join(".", parts);
    }

    public String simpleName() {
        return parts[parts.length - 1];
    }

    public QualifiedName packageName() {
        return new QualifiedName(Arrays.copyOf(parts, parts.length - 1));
    }

    @Override
    public String toString() {
        return fullName();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(parts);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof QualifiedName other && Arrays.equals(parts, other.parts);
    }
}
