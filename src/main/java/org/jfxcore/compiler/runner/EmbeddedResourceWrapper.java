// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import java.nio.file.Path;

public final class EmbeddedResourceWrapper extends ReflectiveWrapper {

    private static final MethodRequirement[] REQUIRED_METHODS = {
        new MethodRequirement("logicalPath", String.class),
        new MethodRequirement("declaringSource", Path.class),
        new MethodRequirement("logicalName", String.class),
        new MethodRequirement("content", byte[].class)
    };

    public EmbeddedResourceWrapper(Object target) {
        super(requireCompatible(target, EmbeddedResourceWrapper.class.getSimpleName(), REQUIRED_METHODS));
    }

    public String logicalPath() {
        return (String)invoke("logicalPath");
    }

    public Path declaringSource() {
        return (Path)invoke("declaringSource");
    }

    public String logicalName() {
        return (String)invoke("logicalName");
    }

    public byte[] content() {
        return (byte[])invoke("content");
    }
}
