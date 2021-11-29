// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

public final class CompilationScope implements AutoCloseable {

    public CompilationScope(CompilationContext context) {
        CompilationContext.setCurrent(context);
    }

    @Override
    public void close() {
        CompilationContext.setCurrent(null);
    }

}
