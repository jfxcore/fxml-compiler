// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.resource;

import org.jfxcore.compiler.diagnostic.errors.ResourceErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class EmbeddedResourceTable {

    private final List<EmbeddedResource> declarations = new ArrayList<>();
    private final Map<String, EmbeddedResource> collisionIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public void register(EmbeddedResource resource) {
        Objects.requireNonNull(resource, "resource");
        EmbeddedResource previous = collisionIndex.get(resource.logicalName());
        if (previous != null) {
            throw ResourceErrors.duplicateDeclaration(resource.nameSourceInfo(), resource.logicalName(), previous);
        }

        declarations.add(resource);
        collisionIndex.put(resource.logicalName(), resource);
    }

    public List<EmbeddedResource> declarations() {
        return List.copyOf(declarations);
    }
}
