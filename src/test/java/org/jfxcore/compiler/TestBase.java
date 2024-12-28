// Copyright (c) 2021, 2024, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javassist.ClassPool;
import org.jfxcore.compiler.util.CompilationScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestBase {

    private CompilationScope scope;

    @BeforeAll
    public void init() {
        var classPool = new ClassPool();
        classPool.appendSystemPath();
        var context = new TestCompilationContext(classPool);
        context.setImports(List.of(getClass().getPackageName() + ".*"));
        scope = new CompilationScope(context);
    }

    @AfterAll
    public void shutdown() {
        scope.close();
    }

}
