// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public final class SharedMethodImpls {

    private SharedMethodImpls() {}

    public interface BytecodeWriter {
        void write(Bytecode code);
    }

    /**
     * Creates the following pair of methods:
     * <ol>
     *     <li>{@code javafx.beans.Observable#addListener(listenerType))}
     *     <li>{@code javafx.beans.Observable#removeListener(listenerType)}
     * </ol>
     * The implementation uses a field as the backing store for a single listener, and throws an
     * exception if more than one listener is added.
     */
    public static void createListenerMethods(ClassGenerator declaringClass,
                                             String fieldName,
                                             TypeDeclaration listenerType) {
        MethodDeclaration method = declaringClass.createMethod("addListener", voidDecl(), listenerType);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        emitAddListener(declaringClass, method, fieldName);

        method = declaringClass.createMethod("removeListener", voidDecl(), listenerType);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        emitRemoveListener(declaringClass, method, fieldName);
    }

    private static void emitAddListener(ClassGenerator declaringClass,
                                        MethodDeclaration method,
                                        String fieldName) {
        Bytecode code = new Bytecode(method);

        // if (this.listener != null)
        code.aload(0)
            .getfield(declaringClass.requireDeclaredField(fieldName))
            .ifnonnull(
                () ->
                    // throw new RuntimeException()
                    code.anew(RuntimeExceptionDecl())
                        .dup()
                        .ldc("Cannot add multiple listeners to a compiled binding.")
                        .invoke(RuntimeExceptionDecl().requireConstructor(StringDecl()))
                        .athrow(),
                () -> code.aload(0)
                    .aload(1)
                    .putfield(declaringClass.requireDeclaredField(fieldName)))
            .vreturn();

        method.setCode(code);
    }

    private static void emitRemoveListener(ClassGenerator declaringClass,
                                           MethodDeclaration method,
                                           String fieldName) {
        Bytecode code = new Bytecode(method);

        // if (listener != null...
        code.aload(0)
            .getfield(declaringClass.requireDeclaredField(fieldName))
            .ifnonnull(() -> code
                // ... && listener.equals($1))
                .aload(0)
                .getfield(declaringClass.requireDeclaredField(fieldName))
                .aload(1)
                .invoke(ObjectDecl().requireDeclaredMethod("equals", ObjectDecl()))
                .ifne(() -> code
                    // listener = null
                    .aload(0)
                    .aconst_null()
                    .putfield(declaringClass.requireDeclaredField(fieldName))))
            .vreturn();

        method.setCode(code);
    }

    /**
     * Creates a method that delegates the method call to an object stored in a field.
     * The implementation is similar to:
     * <pre>{@code
     *     public int doSomething(long param) {
     *         return <fieldName>.doSomething(param);
     *     }
     * }</pre>
     */
    public static void createFieldDelegateMethod(ClassGenerator declaringClass,
                                                 TypeDeclaration retType,
                                                 String fieldName,
                                                 TypeDeclaration fieldType,
                                                 String methodName,
                                                 TypeDeclaration... params) {
        createBehavior(
            declaringClass.createMethod(methodName, retType, params),
            code -> {
                code.aload(0)
                    .getfield(declaringClass.requireDeclaredField(fieldName));

                int slot = 1;
                for (TypeDeclaration param : params) {
                    code.load(param, slot);
                    slot += param.slots();
                }

                code.invoke(fieldType.requireMethod(methodName, params));

                if (retType.equals(voidDecl())) {
                    code.vreturn();
                } else if (retType.equals(longDecl())) {
                    code.lreturn();
                } else if (retType.equals(booleanDecl()) || retType.isIntegralPrimitive()) {
                    code.ireturn();
                } else if (retType.equals(doubleDecl())) {
                    code.dreturn();
                } else if (retType.equals(floatDecl())) {
                    code.freturn();
                } else {
                    code.areturn();
                }
            });
    }

    /**
     * Creates a public method or constructor on the declaring class, and specifies its code attribute.
     */
    public static void createBehavior(BehaviorDeclaration behavior, BytecodeWriter writer) {
        Bytecode code = new Bytecode(behavior);
        writer.write(code);

        if (behavior instanceof ConstructorDeclaration constructor) {
            constructor.setModifiers(Modifier.PUBLIC);
            constructor.setCode(code);
        } else if (behavior instanceof MethodDeclaration method) {
            method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            method.setCode(code);
        } else {
            throw new IllegalArgumentException("behavior");
        }
    }
}
