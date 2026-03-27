// Copyright (c) 2023, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.type.KnownSymbols.*;

public class ReferenceTrackerGenerator implements Generator {

    public static final String CLEAR_STALE_REFERENCES_METHOD = NameHelper.getMangledMethodName("clearStaleReferences");
    public static final String ADD_REFERENCE_METHOD = NameHelper.getMangledMethodName("addReference");

    private static final String REFERENCES_FIELD = NameHelper.getMangledFieldName("references");
    private static final String REFERENCE_QUEUE_FIELD = NameHelper.getMangledFieldName("referenceQueue");

    private MethodDeclaration addReferenceMethod;
    private MethodDeclaration clearWeaklyReachableMethod;

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
    public void emitFields(BytecodeEmitContext context) {
        context.getMarkupClass()
            .createField(REFERENCES_FIELD, SetDecl())
            .setModifiers(Modifier.PRIVATE);

        context.getMarkupClass()
            .createField(REFERENCE_QUEUE_FIELD, ReferenceQueueDecl())
            .setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        addReferenceMethod = context.getMarkupClass()
            .createMethod(ADD_REFERENCE_METHOD, voidDecl(), ObjectDecl(), ObjectDecl())
            .setModifiers(Modifier.FINAL);

        clearWeaklyReachableMethod = context.getMarkupClass()
            .createMethod(CLEAR_STALE_REFERENCES_METHOD, voidDecl())
            .setModifiers(Modifier.FINAL);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitAddReferenceMethod(context, addReferenceMethod);
        emitClearWeaklyReachableMethod(context, clearWeaklyReachableMethod);
    }

    private void emitAddReferenceMethod(BytecodeEmitContext context, MethodDeclaration method) {
        TypeDeclaration weakEntryClass = context.getNestedClasses().find(WeakEntryGenerator.CLASS_NAME);
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .getfield(context.getMarkupClass().requireDeclaredField(REFERENCES_FIELD))
            .ifnull(() -> code
                .aload(0)
                .anew(HashSetDecl())
                .dup()
                .invoke(HashSetDecl().requireConstructor())
                .putfield(context.getMarkupClass().requireDeclaredField(REFERENCES_FIELD))
                .aload(0)
                .anew(ReferenceQueueDecl())
                .dup()
                .invoke(ReferenceQueueDecl().requireConstructor())
                .putfield(context.getMarkupClass().requireDeclaredField(REFERENCE_QUEUE_FIELD))
            )
            .aload(0)
            .getfield(context.getMarkupClass().requireDeclaredField(REFERENCES_FIELD))
            .anew(weakEntryClass)
            .dup()
            .aload(2)
            .aload(1)
            .aload(0)
            .getfield(context.getMarkupClass().requireDeclaredField(REFERENCE_QUEUE_FIELD))
            .invoke(weakEntryClass.requireConstructor(ObjectDecl(), ObjectDecl(), ReferenceQueueDecl()))
            .invoke(SetDecl().requireDeclaredMethod("add", ObjectDecl()))
            .pop()
            .vreturn();

        method.setCode(code);
    }

    private void emitClearWeaklyReachableMethod(BytecodeEmitContext context, MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .getfield(context.getMarkupClass().requireDeclaredField(REFERENCE_QUEUE_FIELD))
            .ifnonnull(() -> {
                Local refLocal = code.acquireLocal(false);
                int position = code.position() + 1;

                code.aload(0)
                    .getfield(context.getMarkupClass().requireDeclaredField(REFERENCE_QUEUE_FIELD))
                    .invoke(ReferenceQueueDecl().requireDeclaredMethod("poll"))
                    .astore(refLocal)
                    .aload(refLocal)
                    .ifnonnull(() -> code
                        .aload(0)
                        .getfield(context.getMarkupClass().requireDeclaredField(REFERENCES_FIELD))
                        .aload(refLocal)
                        .invoke(SetDecl().requireDeclaredMethod("remove", ObjectDecl()))
                        .pop()
                        .goto_position(position)
                    );

                code.releaseLocal(refLocal);
            })
            .vreturn();

        method.setCode(code);
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
        public TypeDeclaration emitClass(BytecodeEmitContext context) {
            return super.emitClass(context)
                .setModifiers(Modifier.PRIVATE | Modifier.FINAL | Modifier.STATIC)
                .setSuperClass(WeakReferenceDecl());
        }

        @Override
        public void emitFields(BytecodeEmitContext context) {
            createField("$0", ObjectDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            createField("$1", intDecl()).setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        }

        @Override
        public void emitCode(BytecodeEmitContext context) {
            createBehavior(createConstructor(ObjectDecl(), ObjectDecl(), ReferenceQueueDecl()), code -> code
                .aload(0)
                .aload(2)
                .aload(3)
                .invoke(requireSuperClass().requireDeclaredConstructor(ObjectDecl(), ReferenceQueueDecl()))
                .aload(0)
                .aload(1)
                .putfield(requireDeclaredField("$0"))
                .aload(0)
                .aload(2)
                .invoke(SystemDecl().requireDeclaredMethod("identityHashCode", ObjectDecl()))
                .putfield(requireDeclaredField("$1"))
                .vreturn()
            );

            createBehavior(createMethod("hashCode", intDecl()), code -> {
                code.aload(0)
                    .getfield(requireDeclaredField("$1"))
                    .ireturn();
            });

            createBehavior(createMethod("equals", booleanDecl(), ObjectDecl()), code -> code
                .aload(0)
                .aload(1)
                .if_acmpeq(() -> code
                    .iconst(1),
                /*else*/ () -> code
                    .aload(1)
                    .isinstanceof(getGeneratedClass())
                    .ifeq(() -> {
                        code.iconst(0);
                    }, () -> {
                        Local referentLocal = code.acquireLocal(false);
                        code.aload(0)
                            .invoke(ReferenceDecl().requireDeclaredMethod("get"))
                            .astore(referentLocal)
                            .aload(referentLocal)
                            .ifnonnull(() -> {
                                code.aload(1)
                                    .checkcast(WeakReferenceDecl())
                                    .aload(referentLocal)
                                    .invoke(ReferenceDecl().requireDeclaredMethod("refersTo", ObjectDecl()));
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
