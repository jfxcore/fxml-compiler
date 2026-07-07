// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

public final class CompilationUnitWrapper extends ReflectiveWrapper {

    private static final MethodRequirement[] REQUIRED_METHODS = {
        new MethodRequirement("descriptor", Object.class),
        new MethodRequirement("generatedSourceText", String.class)
    };

    public CompilationUnitWrapper(Object target) {
        super(requireCompatible(target, CompilationUnitWrapper.class.getSimpleName(), REQUIRED_METHODS));
    }

    public CompilationUnitDescriptorWrapper descriptor() {
        return new CompilationUnitDescriptorWrapper(invoke("descriptor"));
    }

    public String generatedSourceText() {
        return (String)invoke("generatedSourceText");
    }
}
