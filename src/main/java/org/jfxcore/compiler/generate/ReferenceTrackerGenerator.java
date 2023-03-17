// Copyright (c) 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.List;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class ReferenceTrackerGenerator implements Generator {

    public static final String CLEAR_STALE_REFERENCES_METHOD = NameHelper.getMangledMethodName("clearStaleReferences");
    private static final String ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");

    private static final String REFERENCES_FIELD = NameHelper.getMangledFieldName("references");
    private static final String REFERENCE_QUEUE_FIELD = NameHelper.getMangledFieldName("referenceQueue");

    private CtMethod addReferenceMethod;
    private CtMethod clearWeaklyReachableMethod;

    @Override
    public List<Generator> getSubGenerators() {
        return List.of(new WeakEntryGenerator());
    }

    @Override
    public boolean consume(BytecodeEmitContext context) {
        var compilationContext = CompilationContext.getCurrent();
        boolean active = (boolean)compilationContext.getOrDefault(ReferenceTrackerGenerator.class, true);
        if (active) {
            compilationContext.put(ReferenceTrackerGenerator.class, false);
            return true;
        }

        return false;
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(SetType(), REFERENCES_FIELD, context.getMarkupClass());
        field.setModifiers(Modifier.PRIVATE);
        context.getMarkupClass().addField(field);

        field = new CtField(ReferenceQueueType(), REFERENCE_QUEUE_FIELD, context.getMarkupClass());
        field.setModifiers(Modifier.PRIVATE);
        context.getMarkupClass().addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        addReferenceMethod = new CtMethod(
            CtClass.voidType, ADD_REFERENCE_METHOD, new CtClass[] {ObjectType(), ObjectType()}, context.getMarkupClass());
        addReferenceMethod.setModifiers(Modifier.FINAL);
        context.getMarkupClass().addMethod(addReferenceMethod);

        clearWeaklyReachableMethod = new CtMethod(
            CtClass.voidType, CLEAR_STALE_REFERENCES_METHOD, new CtClass[0], context.getMarkupClass());
        clearWeaklyReachableMethod.setModifiers(Modifier.FINAL);
        context.getMarkupClass().addMethod(clearWeaklyReachableMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        emitAddReferenceMethod(context, addReferenceMethod);
        emitClearWeaklyReachableMethod(context, clearWeaklyReachableMethod);
    }

    private void emitAddReferenceMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        CtClass weakEntryClass = parentContext.getNestedClasses().find(WeakEntryGenerator.CLASS_NAME);
        var context = new BytecodeEmitContext(parentContext, parentContext.getMarkupClass(), 3, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(context.getMarkupClass(), REFERENCES_FIELD, SetType())
            .ifnull(() -> code
                .aload(0)
                .anew(HashSetType())
                .dup()
                .invokespecial(HashSetType(), MethodInfo.nameInit, constructor())
                .putfield(context.getMarkupClass(), REFERENCES_FIELD, SetType())
                .aload(0)
                .anew(ReferenceQueueType())
                .dup()
                .invokespecial(ReferenceQueueType(), MethodInfo.nameInit, constructor())
                .putfield(context.getMarkupClass(), REFERENCE_QUEUE_FIELD, ReferenceQueueType())
            )
            .aload(0)
            .getfield(context.getMarkupClass(), REFERENCES_FIELD, SetType())
            .anew(weakEntryClass)
            .dup()
            .aload(2)
            .aload(1)
            .aload(0)
            .getfield(context.getMarkupClass(), REFERENCE_QUEUE_FIELD, ReferenceQueueType())
            .invokespecial(weakEntryClass, MethodInfo.nameInit,
                           constructor(ObjectType(), ObjectType(), ReferenceQueueType()))
            .invokeinterface(SetType(), "add", function(CtClass.booleanType, ObjectType()))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitClearWeaklyReachableMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        var context = new BytecodeEmitContext(parentContext, parentContext.getMarkupClass(), 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(context.getMarkupClass(), REFERENCE_QUEUE_FIELD, ReferenceQueueType())
            .ifnonnull(() -> {
                Local refLocal = code.acquireLocal(false);
                int position = code.position() + 1;

                code.aload(0)
                    .getfield(context.getMarkupClass(), REFERENCE_QUEUE_FIELD, ReferenceQueueType())
                    .invokevirtual(ReferenceQueueType(), "poll", function(ReferenceType()))
                    .astore(refLocal)
                    .aload(refLocal)
                    .ifnonnull(() -> code
                        .aload(0)
                        .getfield(context.getMarkupClass(), REFERENCES_FIELD, SetType())
                        .aload(refLocal)
                        .invokevirtual(SetType(), "remove", function(CtClass.booleanType, ObjectType()))
                        .pop()
                        .goto_position(position)
                    );

                code.releaseLocal(refLocal);
            })
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private static class WeakEntryGenerator extends ClassGenerator {
        static final String CLASS_NAME = NameHelper.getMangledClassName("WeakEntry");

        @Override
        public String getClassName() {
            return CLASS_NAME;
        }

        @Override
        public TypeInstance getTypeInstance() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void emitClass(BytecodeEmitContext context) throws Exception {
            generatedClass = context.getNestedClasses().create(getClassName());
            generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL | Modifier.STATIC);
            generatedClass.setSuperclass(WeakReferenceType());
        }

        @Override
        public void emitFields(BytecodeEmitContext context) throws Exception {
            CtField field = new CtField(ObjectType(), "$0", generatedClass);
            field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            generatedClass.addField(field);

            field = new CtField(CtClass.intType, "$1", generatedClass);
            field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            generatedClass.addField(field);
        }

        @Override
        public void emitCode(BytecodeEmitContext context) throws Exception {
            super.emitCode(context);

            var constructor = new CtConstructor(new CtClass[] {ObjectType(), ObjectType(), ReferenceQueueType()}, generatedClass);
            createBehavior(context, generatedClass, constructor, 4, code -> code
                .aload(0)
                .aload(2)
                .aload(3)
                .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit,
                               constructor(ObjectType(), ReferenceQueueType()))
                .aload(0)
                .aload(1)
                .putfield(generatedClass, "$0", ObjectType())
                .aload(0)
                .aload(2)
                .invokestatic("java.lang.System", "identityHashCode",
                              function(CtClass.intType, ObjectType()))
                .putfield(generatedClass, "$1", CtClass.intType)
                .vreturn()
            );

            var method = new CtMethod(CtClass.intType, "hashCode", new CtClass[0], generatedClass);
            createBehavior(context, generatedClass, method, 1, code -> {
                code.aload(0)
                    .getfield(generatedClass, "$1", CtClass.intType)
                    .ireturn();
            });

            method = new CtMethod(CtClass.booleanType, "equals", new CtClass[] {ObjectType()}, generatedClass);
            createBehavior(context, generatedClass, method, 2, code -> code
                .aload(0)
                .aload(1)
                .if_acmpeq(() -> code
                    .iconst(1),
                /*else*/ () -> code
                    .aload(1)
                    .isinstanceof(generatedClass)
                    .ifeq(() -> {
                        code.iconst(0);
                    }, () -> {
                        Local referentLocal = code.acquireLocal(false);
                        code.aload(0)
                            .invokevirtual(ReferenceType(), "get", function(ObjectType()))
                            .astore(referentLocal)
                            .aload(referentLocal)
                            .ifnonnull(() -> {
                                code.aload(1)
                                    .checkcast(WeakReferenceType())
                                    .aload(referentLocal)
                                    .invokevirtual(ReferenceType(), "refersTo",
                                                   function(CtClass.booleanType, ObjectType()));
                            }, () -> {
                                code.iconst(0);
                            })
                            .releaseLocal(referentLocal);
                    })
                )
                .ireturn()
            );
        }
    }

}
