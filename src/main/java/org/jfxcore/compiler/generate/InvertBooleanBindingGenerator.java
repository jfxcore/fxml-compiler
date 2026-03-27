// Copyright (c) 2025, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;

import static org.jfxcore.compiler.type.Types.*;

public class InvertBooleanBindingGenerator extends ClassGenerator {

    public static final String CLASS_NAME = NameHelper.getMangledClassName("InvertBooleanBinding");

    private static final String EXCEPTION_MESSAGE_OUTER =
            "Bidirectional binding failed, setting to the previous value.";

    private static final String EXCEPTION_MESSAGE_INNER =
            "Bidirectional binding failed together with an attempt"
            + " to restore the source property to the previous value."
            + " Removing the bidirectional binding from both properties.";

    private ConstructorDeclaration constructor;
    private MethodDeclaration getProperty1Method;
    private MethodDeclaration getProperty2Method;
    private MethodDeclaration wasGarbageCollectedMethod;
    private MethodDeclaration changedMethod;
    private MethodDeclaration bindMethod;
    private FieldDeclaration property1Field;
    private FieldDeclaration property2Field;
    private FieldDeclaration updatingField;

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return TypeInstance.booleanType();
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context)
            .addInterface(ChangeListenerDecl())
            .addInterface(WeakListenerDecl())
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        updatingField = createField("updating", booleanDecl()).setModifiers(Modifier.PRIVATE);
        property1Field = createField("property1", WeakReferenceDecl()).setModifiers(Modifier.PRIVATE);
        property2Field = createField("property2", WeakReferenceDecl()).setModifiers(Modifier.PRIVATE);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(PropertyDecl(), PropertyDecl()).setModifiers(Modifier.PUBLIC);
        getProperty1Method = createMethod("getProperty1", PropertyDecl()).setModifiers(Modifier.PRIVATE);
        getProperty2Method = createMethod("getProperty2", PropertyDecl()).setModifiers(Modifier.PRIVATE);

        wasGarbageCollectedMethod = createMethod("wasGarbageCollected", booleanDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        changedMethod = createMethod("changed", voidDecl(), ObservableValueDecl(), ObjectDecl(), ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        bindMethod = createMethod("bindBidirectional", voidDecl(), PropertyDecl(), PropertyDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.STATIC);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        emitConstructor(constructor);
        emitBindMethod(bindMethod);
        emitChangedMethod(changedMethod);
        emitWasGarbageCollectedMethod(wasGarbageCollectedMethod);
        emitGetPropertyMethod(getProperty1Method, property1Field);
        emitGetPropertyMethod(getProperty2Method, property2Field);
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        Bytecode code = new Bytecode(constructor);

        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor());

        // this.property1 = new WeakReference($1)
        code.aload(0)
            .anew(WeakReferenceDecl())
            .dup()
            .aload(1)
            .invoke(WeakReferenceDecl().requireDeclaredConstructor(ObjectDecl()))
            .putfield(property1Field);

        // this.property2 = new WeakReference($2)
        code.aload(0)
            .anew(WeakReferenceDecl())
            .dup()
            .aload(2)
            .invoke(WeakReferenceDecl().requireDeclaredConstructor(ObjectDecl()))
            .putfield(property2Field)
            .vreturn();

        constructor.setCode(code);
    }

    private void emitBindMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);
        Local bindingLocal = code.acquireLocal(false);

        // var binding = new InvertedBooleanBinding($0, $1)
        code.anew(getGeneratedClass())
            .dup()
            .aload(0)
            .aload(1)
            .invoke(getGeneratedClass().requireDeclaredConstructor(PropertyDecl(), PropertyDecl()))
            .astore(bindingLocal);

        // $0.setValue(!$1.getValue())
        code.aload(0)
            .aload(1)
            .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
            .checkcast(BooleanDecl())
            .unbox(BooleanDecl(), booleanDecl())
            .ifne(() -> code.iconst(0), () -> code.iconst(1))
            .box(booleanDecl())
            .invoke(WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl()));

        // $0.addListener(binding)
        code.aload(0)
            .aload(bindingLocal)
            .invoke(ObservableValueDecl().requireDeclaredMethod("addListener", ChangeListenerDecl()));

        // $1.addListener(binding)
        code.aload(1)
            .aload(bindingLocal)
            .invoke(ObservableValueDecl().requireDeclaredMethod("addListener", ChangeListenerDecl()))
            .vreturn();

        method.setCode(code);
    }

    private void emitWasGarbageCollectedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .invoke(requireDeclaredMethod("getProperty1"))
            .ifnull(() -> code.iconst(1).ireturn())
            .aload(0)
            .invoke(requireDeclaredMethod("getProperty2"))
            .ifnull(() -> code.iconst(1).ireturn())
            .iconst(0)
            .ireturn();

        method.setCode(code);
    }

    private void emitGetPropertyMethod(MethodDeclaration method, FieldDeclaration propertyField) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .getfield(propertyField)
            .invoke(ReferenceDecl().requireDeclaredMethod("get"))
            .checkcast(PropertyDecl())
            .areturn();

        method.setCode(code);
    }

    private void emitChangedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);
        Local property1Local = code.acquireLocal(false);
        Local property2Local = code.acquireLocal(false);
        Local anyNullLocal = code.acquireLocal(false);
        Local flippedNewValueLocal = code.acquireLocal(false);

        // if (updating) return
        code.aload(0)
            .getfield(updatingField)
            .ifne(code::vreturn);

        // Property property1 = getProperty1()
        code.aload(0)
            .invoke(requireDeclaredMethod("getProperty1"))
            .astore(property1Local);

        // Property property2 = getProperty2()
        code.aload(0)
            .invoke(requireDeclaredMethod("getProperty2"))
            .astore(property2Local);

        // boolean anyNull = property1 == null || property2 == null
        code.aload(property1Local)
            .ifnull(() -> code.iconst(1), () -> code.iconst(0))
            .ifeq(
                () -> code
                    .aload(property2Local)
                    .ifnull(() -> code.iconst(1), () -> code.iconst(0)),
                () -> code.iconst(1))
            .istore(anyNullLocal);

        Runnable emitRemoveListeners = () -> code
            // if (property1 == null) property1.removeListener(this)
            .aload(property1Local)
            .ifnonnull(() -> code
                .aload(property1Local)
                .aload(0)
                .invoke(ObservableValueDecl().requireDeclaredMethod("removeListener", ChangeListenerDecl())))

            // if (property2 == null) property2.removeListener(this)
            .aload(property2Local)
            .ifnonnull(() -> code
                .aload(property2Local)
                .aload(0)
                .invoke(ObservableValueDecl().requireDeclaredMethod("removeListener", ChangeListenerDecl())));

        Runnable emitUpdateValue = () -> {
            // this.updating = true
            code.aload(0)
                .iconst(1)
                .putfield(updatingField)

                // boolean flippedValue = !newValue
                .aload(3)
                .checkcast(BooleanDecl())
                .dup()
                .ifnull(
                    () -> code.pop().iconst(0),
                    () -> code.unbox(BooleanDecl(), booleanDecl()))
                .ifne(
                    () -> code.iconst(0),
                    () -> code.iconst(1))
                .box(booleanDecl())
                .astore(flippedNewValueLocal);

            int start = code.position();

            // if (property1 == sourceProperty)
            code.aload(property1Local)
                .aload(1)
                .if_acmpeq(
                    // property2.setValue(flippedValue)
                    () -> code
                        .aload(property2Local)
                        .aload(flippedNewValueLocal)
                        .invoke(WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl())),

                    // property1.setValue(flippedValue)
                    () -> code
                        .aload(property1Local)
                        .aload(flippedNewValueLocal)
                        .invoke(WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl())))

                // this.updating = false
                .aload(0)
                .iconst(0)
                .putfield(updatingField);

            int end = code.position();
            Label label = code.goto_label();
            int handler = code.position();
            CatchBlock innerBlock = emitOuterCatchBlock(code, property1Local, property2Local);
            code.addExtraStackSize(2);
            label.resume()
                .handleException(ThrowableDecl(), start, end, handler)
                .handleException(ThrowableDecl(), innerBlock.start, innerBlock.end, innerBlock.handler);
        };

        // if (anyNull)
        code.iload(anyNullLocal)
            .ifeq(emitUpdateValue, emitRemoveListeners)
            .vreturn();

        method.setCode(code);
    }

    private CatchBlock emitOuterCatchBlock(Bytecode code, Local property1Local, Local property2Local) {
        Local flippedOldValueLocal = code.acquireLocal(false);
        Local outerExceptionLocal = code.acquireLocal(false);

        // boolean flippedValue = !oldValue
        code.astore(outerExceptionLocal)
            .aload(2)
            .checkcast(BooleanDecl())
            .dup()
            .ifnull(
                () -> code.pop().iconst(0),
                () -> code.unbox(BooleanDecl(), booleanDecl()))
            .ifne(
                () -> code.iconst(0),
                () -> code.iconst(1))
            .box(booleanDecl())
            .astore(flippedOldValueLocal);

        int start = code.position();

        // if (property1 == sourceProperty)
        code.aload(property1Local)
            .aload(1)
            .if_acmpeq(
                // property1.setValue(flippedValue)
                () -> code
                    .aload(property1Local)
                    .aload(flippedOldValueLocal)
                    .invoke(WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl())),

                // property2.setValue(flippedValue)
                () -> code
                    .aload(property2Local)
                    .aload(flippedOldValueLocal)
                    .invoke(WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl())));

        // this.updating = false
        code.aload(0)
            .iconst(0)
            .putfield(updatingField);

        int end = code.position();
        Label label = code.goto_label();
        int handler = code.position();
        emitInnerCatchBlock(code, outerExceptionLocal, property1Local, property2Local);
        label.resume();

        // throw new RuntimeException(EXCEPTION_MESSAGE_OUTER, e1)
        code.anew(RuntimeExceptionDecl())
            .dup()
            .ldc(EXCEPTION_MESSAGE_OUTER)
            .aload(outerExceptionLocal)
            .invoke(RuntimeExceptionDecl().requireDeclaredConstructor(StringDecl(), ThrowableDecl()))
            .athrow();

        return new CatchBlock(start, end, handler);
    }

    private void emitInnerCatchBlock(Bytecode code, Local outerExceptionLocal,
                                     Local property1Local, Local property2Local) {
        Local exceptionLocal = code.acquireLocal(false);

        // e1.addSuppressed(e2)
        code.astore(exceptionLocal)
            .aload(exceptionLocal)
            .aload(outerExceptionLocal)
            .invoke(ThrowableDecl().requireDeclaredMethod("addSuppressed", ThrowableDecl()));

        // property1.removeListener(this)
        code.aload(property1Local)
            .aload(0)
            .invoke(ObservableValueDecl().requireDeclaredMethod("removeListener", ChangeListenerDecl()));

        // property2.removeListener(this)
        code.aload(property2Local)
            .aload(0)
            .invoke(ObservableValueDecl().requireDeclaredMethod("removeListener", ChangeListenerDecl()));

        // this.updating = false
        code.aload(0)
            .iconst(0)
            .putfield(updatingField);

        // throw new RuntimeException(EXCEPTION_MESSAGE_INNER, e1)
        code.anew(RuntimeExceptionDecl())
            .dup()
            .ldc(EXCEPTION_MESSAGE_INNER)
            .aload(exceptionLocal)
            .invoke(RuntimeExceptionDecl().requireDeclaredConstructor(StringDecl(), ThrowableDecl()))
            .athrow();

        code.releaseLocal(exceptionLocal);
    }

    private record CatchBlock(int start, int end, int handler) {}
}
