// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.ClassPool;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MethodReferencedSupport {

    private final String testClassName;

    protected MethodReferencedSupport(String testClassName) {
        this.testClassName = testClassName;
    }

    public void assertReferenced(String fileName, Object root, String methodName) {
        assertTrue(isMethodCalled(fileName, root, methodName));
    }

    public void assertNotReferenced(String fileName, Object root, String methodName) {
        assertFalse(isMethodCalled(fileName, root, methodName));
    }

    private synchronized boolean isMethodCalled(String fileName, Object root, String methodName) {
        try {
            if (classPool == null) {
                classPool = new ClassPool();
                classPool.appendSystemPath();
                classPool.appendClassPath(root.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm());
            }

            List<CtMethod> methodCalls = new ArrayList<>();

            classPool
                .get(testClassName + "_" + fileName)
                .getDeclaredMethod("initializeComponent").instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall c) {
                    try {
                        methodCalls.add(c.getMethod());
                    } catch (NotFoundException ignored) {
                    }
                }
            });

            return methodCalls.stream().anyMatch(m -> m.getName().equals(methodName));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private ClassPool classPool;

}
