// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.runner;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public final class CompilationUnitDescriptorWrapper extends ReflectiveWrapper {

    private static final MethodRequirement[] REQUIRED_METHODS = {
        new MethodRequirement("writeTo", void.class, Path.class),
        new MethodRequirement("resolveMarkupFile", Path.class, Path.class, String.class)
    };

    public CompilationUnitDescriptorWrapper(Object target) {
        super(requireCompatible(target, CompilationUnitDescriptorWrapper.class.getSimpleName(), REQUIRED_METHODS));
    }

    public void writeTo(File directory) {
        invoke("writeTo", new Class<?>[] {Path.class}, directory.toPath());
    }

    public File resolveMarkupFile(File baseDir, String extension) {
        Objects.requireNonNull(baseDir, "baseDir");
        Objects.requireNonNull(extension, "extension");
        return ((Path)invoke("resolveMarkupFile",
            new Class<?>[] {Path.class, String.class}, baseDir.toPath(), extension)).toFile();
    }
}
