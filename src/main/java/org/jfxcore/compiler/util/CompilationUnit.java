// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import java.util.Objects;

public record CompilationUnit(CompilationUnitDescriptor descriptor, String generatedSourceText) {

    public CompilationUnit {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(generatedSourceText, "generatedSourceText");
    }
}
