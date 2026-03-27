// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import static org.jfxcore.compiler.type.KnownSymbols.*;

public class HeadSegmentGenerator extends PropertySegmentGeneratorBase {

    private ConstructorDeclaration constructor;
    private MethodDeclaration changedMethod;

    public HeadSegmentGenerator(SourceInfo sourceInfo, FoldedGroup[] groups) {
        super(sourceInfo, groups, 0);
    }

    @Override
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        return super.emitClass(context).addInterface(ChangeListenerDecl());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        createField(NEXT_FIELD, groups[segment + 1].getCompiledClass()).setModifiers(Modifier.FINAL);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(ObservableValueDecl());

        changedMethod = createMethod(
            "changed", voidDecl(), ObservableValueDecl(), ObjectDecl(), ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
        super.emitCode(context);

        emitConstructor(constructor);
        emitChangedMethod(changedMethod);
        emitGetValueMethod(getValueMethod);
        emitSetValueMethod(setValueMethod);
        emitAddInvalidationListenerMethod(addInvalidationListenerMethod);
        emitRemoveInvalidationListenerMethod(removeInvalidationListenerMethod);
        emitAddChangeListenerMethod(addChangeListenerMethod);
        emitRemoveChangeListenerMethod(removeChangeListenerMethod);
        emitGetBeanMethod(getBeanMethod);
        emitGetNameMethod(getNameMethod);

        if (valueClass.isPrimitive()) {
            emitGetMethod(getMethod);
            emitSetMethod(setMethod);
        }

        if (isNumeric) {
            emitIntValueMethod(intValueMethod);
            emitLongValueMethod(longValueMethod);
            emitFloatValueMethod(floatValueMethod);
            emitDoubleValueMethod(doubleValueMethod);
        }
    }

    private void emitGetValueMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .invoke(nextClassMethod("getValue"))
            .areturn());
    }

    private void emitSetValueMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .aload(1)
            .invoke(nextClassMethod("setValue", ObjectDecl()))
            .vreturn());
    }

    private void emitAddInvalidationListenerMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .aload(1)
            .invoke(nextClassMethod("addListener", InvalidationListenerDecl()))
            .vreturn());
    }

    private void emitRemoveInvalidationListenerMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .aload(1)
            .invoke(nextClassMethod("removeListener", InvalidationListenerDecl()))
            .vreturn());
    }

    private void emitAddChangeListenerMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .aload(1)
            .invoke(nextClassMethod("addListener", ChangeListenerDecl()))
            .vreturn());
    }

    private void emitRemoveChangeListenerMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .aload(1)
            .invoke(nextClassMethod("removeListener", ChangeListenerDecl()))
            .vreturn());
    }

    private void emitGetMethod(MethodDeclaration method) {
        expandPath(method, code ->
            code.invoke(nextClassMethod("get"))
                .ret(valueClass));
    }

    private void emitSetMethod(MethodDeclaration method) {
        expandPath(method, code ->
            code.load(valueClass, 1)
                .invoke(nextClassMethod("set", valueClass))
                .vreturn());
    }

    private void emitIntValueMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .invoke(nextClassMethod("intValue"))
            .ireturn());
    }

    private void emitLongValueMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .invoke(nextClassMethod("longValue"))
            .lreturn());
    }

    private void emitFloatValueMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .invoke(nextClassMethod("floatValue"))
            .freturn());
    }

    private void emitDoubleValueMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .invoke(nextClassMethod("doubleValue"))
            .dreturn());
    }

    private void emitGetBeanMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .invoke(nextClassMethod("getBean"))
            .areturn());
    }

    private void emitGetNameMethod(MethodDeclaration method) {
        expandPath(method, code -> code
            .invoke(nextClassMethod("getName"))
            .areturn());
    }

    private void expandPath(MethodDeclaration method, Consumer<Bytecode> fixup) {
        Bytecode code = new Bytecode(method);
        code.aload(0);

        for (int i = 0; i < groups.length - 1; ++i) {
            code.getfield(groups[i].getCompiledClass().requireDeclaredField(NEXT_FIELD));
        }

        fixup.accept(code);
        method.setCode(code);
    }

    private void emitConstructor(ConstructorDeclaration constructor) {
        Bytecode code = new Bytecode(constructor.declaringType(), 2);
        TypeDeclaration nextClass = groups[segment + 1].getCompiledClass();
        MethodDeclaration changedMethod = requireDeclaredMethod("changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl());

        // this.next = new NextClassName();
        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor())
            .aload(0)
            .anew(nextClass)
            .dup()
            .invoke(nextClass.requireDeclaredConstructor())
            .putfield(requireDeclaredField(NEXT_FIELD));

        // $1.addListener(this);
        code.aload(1)
            .aload(0)
            .invoke(ObservableValueDecl().requireDeclaredMethod("addListener", ChangeListenerDecl()));

        // this.changed(null, null, $1.getValue());
        code.aload(0)
            .aconst_null()
            .aconst_null()
            .aload(1)
            .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
            .invoke(changedMethod)
            .vreturn();

        constructor.setCode(code);
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
            .invoke(nextClass.requireDeclaredMethod(UPDATE_METHOD, nextObservableType))
            .vreturn();

        method.setCode(code);
    }

    private MethodDeclaration nextClassMethod(String name, TypeDeclaration... params) {
        return groups[segment + 1].getCompiledClass().requireDeclaredMethod(name, params);
    }
}
