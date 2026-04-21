// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class IntermediateSegmentGenerator extends SegmentGeneratorBase {

    public static final String OBSERVABLE_FIELD = "observable";
    private static final String WEAK_LISTENER_FIELD = "weakListener";

    private final TypeInvoker invoker;

    private TypeDeclaration observableType;
    private ConstructorDeclaration constructor;
    private MethodDeclaration updateMethod;
    private MethodDeclaration changedMethod;
    private MethodDeclaration invalidatedMethod;

    public IntermediateSegmentGenerator(SourceInfo sourceInfo, FoldedGroup[] path, int segment) {
        super(sourceInfo, path, segment);
        this.invoker = new TypeInvoker(sourceInfo);
    }

    @Override
    public TypeInstance getTypeInstance() {
        return invoker.invokeType(ChangeListenerDecl());
    }

    @Override
    public String getClassName() {
        return NameHelper.getMangledClassName(groups[segment].getName());
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        TypeDeclaration generatedClass = super.emitClass(context)
            .addInterface(ChangeListenerDecl())
            .addInterface(InvalidationListenerDecl())
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

        groups[segment].setCompiledClass(generatedClass);
        return generatedClass;
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        observableType = groups[segment].getObservableDependencyType();
        createField(NEXT_FIELD, groups[segment + 1].getCompiledClass()).setModifiers(Modifier.FINAL);
        createField(OBSERVABLE_FIELD, observableType).setModifiers(Modifier.PRIVATE);
        createField(
            WEAK_LISTENER_FIELD,
            groups[segment].getObservableDependencyKind() == ObservableDependencyKind.VALUE
                ? WeakChangeListenerDecl()
                : WeakInvalidationListenerDecl())
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createDefaultConstructor();

        updateMethod = createMethod(UPDATE_METHOD, voidDecl(), observableType)
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        changedMethod = createMethod(
            "changed", voidDecl(), ObservableValueDecl(), ObjectDecl(), ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        invalidatedMethod = createMethod("invalidated", voidDecl(), ObservableDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor(constructor);
        emitUpdateMethod(updateMethod);
        emitChangedMethod(changedMethod);
        emitInvalidatedMethod(invalidatedMethod);
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        Bytecode code = new Bytecode(constructor.declaringType(), 1);
        TypeDeclaration nextClass = groups[segment + 1].getCompiledClass();

        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor())
            .aload(0)
            .anew(requireDeclaredField(WEAK_LISTENER_FIELD).type())
            .dup()
            .aload(0)
            .invoke(requireDeclaredField(WEAK_LISTENER_FIELD).type().requireConstructor(
                groups[segment].getObservableDependencyKind() == ObservableDependencyKind.VALUE
                    ? ChangeListenerDecl()
                    : InvalidationListenerDecl()))
            .putfield(requireDeclaredField(WEAK_LISTENER_FIELD))
            .aload(0)
            .anew(nextClass)
            .dup()
            .invoke(nextClass.requireDeclaredConstructor())
            .putfield(requireDeclaredField(NEXT_FIELD))
            .vreturn();

        constructor.setCode(code);
    }

    private void emitUpdateMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);
        ObservableDependencyKind dependencyKind = groups[segment].getObservableDependencyKind();
        FieldDeclaration observableField = requireDeclaredField(OBSERVABLE_FIELD);
        Local newValueLocal = code.acquireLocal(false);
        Label L0, L1, L2;

        // if (this.observable != null)
        L0 = code
            .aload(0)
            .getfield(observableField)
            .ifnull();

        // this.observable.removeListener(this);
        code.aload(0)
            .getfield(observableField)
            .aload(0)
            .getfield(requireDeclaredField(WEAK_LISTENER_FIELD))
            .invoke(dependencyKind == ObservableDependencyKind.VALUE
                ? ObservableValueDecl().requireDeclaredMethod("removeListener", ChangeListenerDecl())
                : ObservableDecl().requireDeclaredMethod("removeListener", InvalidationListenerDecl()));

        // this.observable = $1
        L0.resume()
            .aload(0)
            .aload(1)
            .putfield(observableField);

        if (dependencyKind == ObservableDependencyKind.VALUE) {
            // if ($1 != null)
            L1 = code
                .aload(1)
                .ifnull();

            // $1.addListener(this);
            // newValue = $1.getValue();
            L2 = code
                .aload(1)
                .aload(0)
                .getfield(requireDeclaredField(WEAK_LISTENER_FIELD))
                .invoke(ObservableValueDecl().requireDeclaredMethod("addListener", ChangeListenerDecl()))
                .aload(1)
                .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                .astore(newValueLocal)
                .goto_label();

            // else newValue = null;
            L1.resume()
                .aconst_null()
                .astore(newValueLocal);

            // this.changed(null, oldValue, newValue);
            L2.resume()
                .aload(0)
                .aconst_null()
                .aconst_null()
                .aload(newValueLocal)
                .invoke(requireDeclaredMethod("changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl()))
                .vreturn();
        } else {
            // if ($1 != null) $1.addListener(this);
            code.aload(1)
                .ifnonnull(() -> code
                    .aload(1)
                    .aload(0)
                    .getfield(requireDeclaredField(WEAK_LISTENER_FIELD))
                    .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl())));

            // this.invalidated(null);
            code.aload(0)
                .aconst_null()
                .invoke(requireDeclaredMethod("invalidated", ObservableDecl()))
                .vreturn();
        }

        code.releaseLocal(newValueLocal);

        method.setCode(code);
    }

    private void emitInvalidatedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);
        TypeDeclaration nextClass = groups[segment + 1].getCompiledClass();
        TypeDeclaration nextObservableType = groups[segment + 1].getObservableDependencyType();
        TypeDeclaration firstValueType = groups[segment].getFirstPathSegment().getValueTypeInstance().declaration();
        Segment[] path = this.groups[segment].getPath();

        code.aload(0)
            .getfield(requireDeclaredField(OBSERVABLE_FIELD))
            .dup()
            .ifnull(
                () -> code.pop().aconst_null(),
                () -> {
                    if (path.length > 1) {
                        code.checkcast(firstValueType);
                        emitInvariants(firstValueType, path, code);
                    }
                })
            .checkcast(nextObservableType)
            .store(nextObservableType, 1)
            .aload(0)
            .getfield(requireDeclaredField(NEXT_FIELD))
            .aload(1)
            .invoke(nextClass.requireDeclaredMethod(UPDATE_METHOD, nextObservableType))
            .vreturn();

        method.setCode(code);
    }

    private void emitChangedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);
        TypeDeclaration nextClass = groups[segment + 1].getCompiledClass();
        TypeDeclaration nextObservableType = groups[segment + 1].getFirstPathSegment().getTypeInstance().declaration();
        TypeDeclaration firstValueType = groups[segment].getFirstPathSegment().getValueTypeInstance().declaration();
        Segment[] path = this.groups[segment].getPath();

        if (path.length > 1) {
            // if ($3 != null)
            Label L0 = code
                .aload(3)
                .aconst_null()
                .astore(3)
                .dup()
                .ifnull();

            // (T)$3
            code.dup()
                .checkcast(firstValueType);

            // .foo.bar().baz...
            emitInvariants(firstValueType, path, code);

            code.astore(3);

            // end if
            L0.resume().pop();
        }

        code.aload(0)
            .getfield(requireDeclaredField(NEXT_FIELD))
            .aload(3)
            .checkcast(nextObservableType)
            .invoke(nextClass.requireDeclaredMethod(UPDATE_METHOD, nextObservableType))
            .vreturn();

        method.setCode(code);
    }
}
