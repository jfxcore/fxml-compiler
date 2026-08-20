// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import java.util.List;

public final class CompilationResultWrapper extends ReflectiveWrapper {

    private static final MethodRequirement[] REQUIRED_METHODS = {
        new MethodRequirement("getEmbeddedResources", List.class)
    };

    public CompilationResultWrapper(Object target) {
        super(requireCompatible(target, CompilationResultWrapper.class.getSimpleName(), REQUIRED_METHODS));
    }

    public List<EmbeddedResourceWrapper> getEmbeddedResources() {
        @SuppressWarnings("unchecked")
        List<Object> resources = (List<Object>)invoke("getEmbeddedResources");
        return resources.stream().map(EmbeddedResourceWrapper::new).toList();
    }
}
