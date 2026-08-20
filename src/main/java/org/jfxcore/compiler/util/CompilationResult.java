// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.resource.EmbeddedResource;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class CompilationResult {

    private final List<EmbeddedResource> resources;

    public CompilationResult(Collection<EmbeddedResource> resources) {
        this.resources = resources.stream()
            .sorted(Comparator.comparing(EmbeddedResource::logicalPath))
            .toList();
    }

    public List<EmbeddedResource> getEmbeddedResources() {
        return resources;
    }
}
