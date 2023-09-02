// Copyright (c) 2021, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javassist.ClassPool;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationSource;
import java.util.Collections;

public class TestCompilationContext extends CompilationContext {

    private final Logger logger = new Logger() {
        @Override public void fine(String message) {}
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void error(String message) {}
    };

    public TestCompilationContext(ClassPool classPool) {
        super(new CompilationSource.InMemory(""));
        setClassPool(classPool);
        setImports(Collections.emptyList());
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

}
