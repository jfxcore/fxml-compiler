// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.resource.EmbeddedResource;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record CompilationUnit(
        CompilationUnitDescriptor descriptor,
        String generatedSourceText,
        List<EmbeddedResource> embeddedResources) {

    public CompilationUnit {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(generatedSourceText, "generatedSourceText");

        embeddedResources = embeddedResources.stream()
            .sorted(Comparator.comparing(EmbeddedResource::logicalPath))
            .toList();
    }
}
