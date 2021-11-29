// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javassist.ClassPool;
import org.jfxcore.compiler.util.CompilationScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TestBase {

    private static CompilationScope scope;

    @BeforeAll
    public static void init() {
        var classPool = new ClassPool();
        classPool.appendSystemPath();
        scope = new CompilationScope(new TestCompilationContext(classPool));
    }

    @AfterAll
    public static void shutdown() {
        scope.close();
    }

}
