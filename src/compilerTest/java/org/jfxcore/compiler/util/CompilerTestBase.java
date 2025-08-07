// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.ClassPool;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

public class CompilerTestBase {

    private String cachedFileName;

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

    public void gc() {
        WeakReference<Object> wref = new WeakReference<>(new Object());

        for (int i = 0; i < 10 && wref.get() != null; ++i) {
            createGarbage();
            System.gc();

            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void createGarbage() {
        LinkedList<Integer> list = new LinkedList<>();
        int counter = 0;
        while (counter < 10) {
            counter += 1;
            list.add(1);
        }
    }

    private static Object getOuter(Object instance) {
        try {
            Field field = instance.getClass().getDeclaredField("this$0");
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private String getAdditionalImports() {
        Object importClass = this;
        var imports = new StringBuilder();

        do {
            imports.append("<?import %s.*?>\r\n".formatted(importClass.getClass().getCanonicalName()));
            importClass = getOuter(importClass);
        } while (importClass != null);

        return imports.toString();
    }

    private String getMethodName() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            try {
                Class<?> declaringClass = Class.forName(element.getClassName());
                if (Arrays.stream(declaringClass.getMethods()).anyMatch(m ->
                        m.getName().equals(element.getMethodName())
                        && (m.getAnnotation(Test.class) != null
                            || m.getAnnotation(ParameterizedTest.class) != null
                            || m.getAnnotation(BeforeAll.class) != null))) {
                    return element.getMethodName();
                }
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }

        throw new RuntimeException("No test method found.");
    }

    private String getFileName() {
        if (cachedFileName != null) {
            return cachedFileName;
        }

        var names = new ArrayList<String>();
        Object testClass = this;

        do {
            names.add(0, testClass.getClass().getSimpleName());
            testClass = getOuter(testClass);
        } while (testClass != null);

        names.add(getMethodName());

        return cachedFileName = String.join("_", names);
    }

    public void assertMethodCall(Object root, Predicate<List<CtMethod>> predicate) {
        assertTrue(testMethodExpr(root, predicate), "MethodCall assertion failed");
    }

    public void assertMethodCall(Object root, String... methodNames) {
        List<String> methodNameList = Arrays.asList(methodNames);
        assertMethodCall(root, list -> list.stream().anyMatch(m -> methodNameList.contains(m.getName())));
    }

    public void assertNotMethodCall(Object root, String... methodNames) {
        List<String> methodNameList = Arrays.asList(methodNames);
        assertMethodCall(root, list -> list.stream().noneMatch(m -> methodNameList.contains(m.getName())));
    }

    public void assertNewExpr(Object root, Predicate<List<CtConstructor>> predicate) {
        assertTrue(testNewExpr(root, predicate), "NewExpr assertion failed");
    }

    public void assertNewExpr(Object root, String... classNameFragments) {
        assertNewExpr(root, ctors -> ctors.stream().anyMatch(
            ctor -> Arrays.stream(classNameFragments).anyMatch(
                cn -> ctor.getDeclaringClass().getSimpleName().contains(cn))));
    }

    public void assertNotNewExpr(Object root, String... classNameFragments) {
        assertNewExpr(root, ctors -> ctors.stream().noneMatch(
            ctor -> Arrays.stream(classNameFragments).anyMatch(
                cn -> ctor.getDeclaringClass().getSimpleName().contains(cn))));
    }

    public void assertFieldAccess(Object root, String className, String fieldName, String signature) {
        assertTrue(testFieldAccess(root, className, fieldName, signature), "FieldAccess assertion failed");
    }

    private synchronized boolean testMethodExpr(Object root, Predicate<List<CtMethod>> predicate) {
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

            return predicate.test(methodCalls);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private synchronized boolean testNewExpr(Object root, Predicate<List<CtConstructor>> predicate) {
        try {
            if (classPool == null) {
                classPool = new ClassPool();
                classPool.appendSystemPath();
                classPool.appendClassPath(root.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm());
            }

            List<CtConstructor> constructorCalls = new ArrayList<>();

            classPool
                .get(root.getClass().getName())
                .getDeclaredMethod("initializeComponent").instrument(new ExprEditor() {
                    @Override
                    public void edit(NewExpr e) {
                        try {
                            constructorCalls.add(e.getConstructor());
                        } catch (NotFoundException ignored) {
                        }
                    }
                });

            return predicate.test(constructorCalls);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private synchronized boolean testFieldAccess(Object root, String className, String fieldName, String signature) {
        try {
            if (classPool == null) {
                classPool = new ClassPool();
                classPool.appendSystemPath();
                classPool.appendClassPath(root.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm());
            }

            record FieldInfo(String className, String fieldName, String signature) {}
            List<FieldInfo> fields = new ArrayList<>();

            classPool
                .get(root.getClass().getName())
                .getDeclaredMethod("initializeComponent").instrument(new ExprEditor() {
                    @Override
                    public void edit(FieldAccess f) {
                        fields.add(new FieldInfo(f.getClassName(), f.getFieldName(), f.getSignature()));
                    }
                });

            return fields.contains(new FieldInfo(className, fieldName, signature));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private ClassPool classPool;

}
