// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.TypeHelper;
import java.util.Arrays;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public final class SharedMethodImpls {

    private SharedMethodImpls() {}

    public interface BytecodeWriter {
        void write(Bytecode code) throws Exception;
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
    public static void createListenerMethods(
            BytecodeEmitContext context, CtClass declaringClass, String fieldName, CtClass listenerType) throws Exception {
        CtMethod method = new CtMethod(
            CtClass.voidType, "addListener", new CtClass[] {listenerType}, declaringClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        declaringClass.addMethod(method);
        emitAddListener(context, declaringClass, method, listenerType, fieldName);

        method = new CtMethod(
            CtClass.voidType, "removeListener", new CtClass[] {listenerType}, declaringClass);
        method.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        declaringClass.addMethod(method);
        emitRemoveListener(context, declaringClass, method, listenerType, fieldName);
    }

    private static void emitAddListener(BytecodeEmitContext parentContext, CtClass declaringClass, CtMethod method,
                                        CtClass listenerType, String fieldName) throws Exception {
        var context = new BytecodeEmitContext(parentContext, declaringClass, 2, -1);
        Bytecode code = context.getOutput();

        // if (this.listener != null)
        code.aload(0)
            .getfield(declaringClass, fieldName, listenerType)
            .ifnonnull(
                () ->
                    // throw new RuntimeException()
                    code.anew(RuntimeExceptionType())
                        .dup()
                        .ldc("Cannot add multiple listeners to a compiled binding.")
                        .invokespecial(RuntimeExceptionType(), MethodInfo.nameInit, constructor(StringType()))
                        .athrow(),
                () -> {
                    code.aload(0)
                        .aload(1)
                        .putfield(declaringClass, fieldName, listenerType);
                })
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(declaringClass.getClassPool());
    }

    private static void emitRemoveListener(BytecodeEmitContext parentContext, CtClass declaringClass, CtMethod method,
                                           CtClass listenerType, String fieldName) throws Exception {
        var context = new BytecodeEmitContext(parentContext, declaringClass, 2, -1);
        Bytecode code = context.getOutput();

        // if (listener != null...
        code.aload(0)
            .getfield(declaringClass, fieldName, listenerType)
            .ifnonnull(() -> code
                // ... && listener.equals($1))
                .aload(0)
                .getfield(declaringClass, fieldName, listenerType)
                .aload(1)
                .invokevirtual(
                    ObjectType(), "equals", function(CtClass.booleanType, ObjectType()))
                .ifne(() -> code
                    // listener = null
                    .aload(0)
                    .aconst_null()
                    .putfield(declaringClass, fieldName, listenerType)))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(declaringClass.getClassPool());
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
    public static void createFieldDelegateMethod(BytecodeEmitContext context, CtClass declaringClass, CtClass retType,
                                                 String fieldName, CtClass fieldType, String methodName,
                                                 CtClass... params) throws Exception {
        createBehavior(
            context, declaringClass, new CtMethod(retType, methodName, params, declaringClass),
            Arrays.stream(params).mapToInt(TypeHelper::getSlots).sum() + 1,
            code -> {
                code.aload(0)
                    .getfield(declaringClass, fieldName, fieldType);

                int slot = 1;
                for (CtClass param : params) {
                    code.ext_load(param, slot);
                    slot += TypeHelper.getSlots(param);
                }

                if (fieldType.isInterface()) {
                    code.invokeinterface(fieldType, methodName, function(retType, params));
                } else {
                    code.invokevirtual(fieldType, methodName, function(retType, params));
                }

                if (retType == CtClass.voidType) {
                    code.vreturn();
                } else if (retType == CtClass.longType) {
                    code.lreturn();
                } else if (retType == CtClass.booleanType || TypeHelper.isIntegralPrimitive(retType)) {
                    code.ireturn();
                } else if (retType == CtClass.doubleType) {
                    code.dreturn();
                } else if (retType == CtClass.floatType) {
                    code.freturn();
                } else {
                    code.areturn();
                }
            });
    }

    /**
     * Creates a public method or constructor on the declaring class, and specifies its code attribute.
     */
    public static void createBehavior(BytecodeEmitContext parentContext, CtClass declaringClass, CtBehavior behavior,
                                      int occupiedLocals, BytecodeWriter writer) throws Exception {
        if (behavior instanceof CtConstructor constructor) {
            constructor.setModifiers(Modifier.PUBLIC);
            declaringClass.addConstructor(constructor);
        } else {
            behavior.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            declaringClass.addMethod((CtMethod)behavior);
        }

        var context = new BytecodeEmitContext(parentContext, declaringClass, occupiedLocals, -1);
        writer.write(context.getOutput());
        behavior.getMethodInfo().setCodeAttribute(context.getOutput().toCodeAttribute());
        behavior.getMethodInfo().rebuildStackMap(declaringClass.getClassPool());
    }

}
