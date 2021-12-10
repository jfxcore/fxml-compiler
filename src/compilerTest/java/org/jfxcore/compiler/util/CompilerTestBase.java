// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.ClassPool;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerTestBase extends NestedTestBase {

    private String cachedFileName;

    public CompilerTestBase() {
        super(null);
    }

    public CompilerTestBase(Object outerTest) {
        super(outerTest);
    }

    @BeforeEach
    public void clearCachedFileName() {
        cachedFileName = null;
    }

    public <T> Class<T> compile(String fxml) {
        return new TestCompiler().compileClass(getFileName(), getAdditionalImports() + fxml);
    }

    public <T> T compileAndRun(String fxml) {
        return TestCompiler.newInstance(getFileName(), getAdditionalImports() + fxml);
    }

    private String getAdditionalImports() {
        Object importClass = this;
        var imports = new StringBuilder();

        do {
            imports.append("<?import %s.*?>\r\n".formatted(importClass.getClass().getCanonicalName()));
            importClass = importClass instanceof NestedTestBase n ? n.getOuterTest() : null;
        } while (importClass != null);

        return imports.toString();
    }

    private String getMethodName() {
        String methodName = null;

        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            try {
                Class<?> declaringClass = Class.forName(element.getClassName());
                Method method = declaringClass.getMethod(element.getMethodName());

                if (method.getAnnotation(Test.class) != null) {
                    methodName = method.getName();
                    break;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            }
        }

        if (methodName == null) {
            throw new RuntimeException("No test method found.");
        }

        return methodName;
    }

    private String getFileName() {
        if (cachedFileName != null) {
            return cachedFileName;
        }

        var names = new ArrayList<String>();
        Object testClass = this;

        do {
            names.add(0, testClass.getClass().getSimpleName());
            testClass = testClass instanceof NestedTestBase n ? n.getOuterTest() : null;
        } while (testClass != null);

        names.add(getMethodName());

        return cachedFileName = String.join("_", names);
    }

    public void assertReferenced(Object root, String methodName) {
        assertTrue(isMethodCalled(root, methodName));
    }

    public void assertNotReferenced(Object root, String methodName) {
        assertFalse(isMethodCalled(root, methodName));
    }

    private synchronized boolean isMethodCalled(Object root, String methodName) {
        try {
            if (classPool == null) {
                classPool = new ClassPool();
                classPool.appendSystemPath();
                classPool.appendClassPath(root.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm());
            }

            List<CtMethod> methodCalls = new ArrayList<>();

            classPool
                .get(root.getClass().getName())
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
