// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.ast.expression.path.Segment;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;

public class IntermediateSegmentGenerator extends SegmentGeneratorBase {

    public static final String OBSERVABLE_FIELD = "observable";

    private final Resolver resolver;
    private final TypeInvoker invoker;
    private CtClass observableType;
    private CtConstructor constructor;
    private CtMethod updateMethod;
    private CtMethod changedMethod;

    public IntermediateSegmentGenerator(SourceInfo sourceInfo, FoldedGroup[] path, int segment) {
        super(sourceInfo, path, segment);
        this.resolver = new Resolver(sourceInfo);
        this.invoker = new TypeInvoker(sourceInfo);
    }

    @Override
    public TypeInstance getTypeInstance() {
        return invoker.invokeType(ChangeListenerType());
    }

    @Override
    public String getClassName() {
        return NameHelper.getMangledClassName(groups[segment].getName());
    }

    @Override
    public void emitClass(BytecodeEmitContext context) {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.addInterface(ChangeListenerType());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        groups[segment].setCompiledClass(generatedClass);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(groups[segment + 1].getCompiledClass(), NEXT_FIELD, generatedClass);
        field.setModifiers(Modifier.FINAL);
        generatedClass.addField(field);

        observableType = groups[segment].getFirstPathSegment().getTypeInstance().jvmType();
        field = new CtField(observableType, OBSERVABLE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        constructor = CtNewConstructor.defaultConstructor(generatedClass);

        updateMethod = new CtMethod(
            CtClass.voidType, UPDATE_METHOD, new CtClass[] {observableType}, generatedClass);
        updateMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        changedMethod = new CtMethod(
            CtClass.voidType,
            "changed",
            new CtClass[] {ObservableValueType(), ObjectType(), ObjectType()},
                generatedClass);
        changedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        generatedClass.addConstructor(constructor);
        generatedClass.addMethod(updateMethod);
        generatedClass.addMethod(changedMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        emitConstructor(constructor);
        emitUpdateMethod(updateMethod);
        emitChangedMethod(changedMethod);
    }

    private void emitConstructor(CtConstructor constructor) throws Exception {
        Bytecode code = new Bytecode(constructor.getDeclaringClass(), 1);
        CtClass nextClass = groups[segment + 1].getCompiledClass();

        code.aload(0)
            .invokespecial(ObjectType(), MethodInfo.nameInit, constructor())
            .aload(0)
            .anew(nextClass)
            .dup()
            .invokespecial(nextClass, MethodInfo.nameInit, constructor())
            .putfield(constructor.getDeclaringClass(), NEXT_FIELD, nextClass)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitUpdateMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 3);
        CtClass declaringClass = method.getDeclaringClass();
        Label L0, L1, L2;

        // if (this.observable != null)
        L0 = code
            .aload(0)
            .getfield(declaringClass, OBSERVABLE_FIELD, observableType)
            .ifnull();

        // this.observable.removeListener(this);
        code.aload(0)
            .getfield(declaringClass, OBSERVABLE_FIELD, observableType)
            .aload(0)
            .invokeinterface(
                ObservableValueType(),
                "removeListener",
                function(CtClass.voidType, ChangeListenerType()));

        // this.observable = $1
        L0.resume()
            .aload(0)
            .aload(1)
            .putfield(declaringClass, OBSERVABLE_FIELD, observableType);

        // if ($1 != null)
        L1 = code
            .aload(1)
            .ifnull();

        // $1.addListener(this);
        // newValue = $1.getValue();
        L2 = code
            .aload(1)
            .aload(0)
            .invokeinterface(
                ObservableValueType(),
                "addListener",
                function(CtClass.voidType, ChangeListenerType()))
            .aload(1)
            .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
            .astore(2)
            .goto_label();

        // else newValue = null;
        L1.resume()
            .aconst_null()
            .astore(2);

        // this.changed(null, oldValue, newValue);
        L2.resume()
            .aload(0)
            .aconst_null()
            .aconst_null()
            .aload(2)
            .invokevirtual(
                method.getDeclaringClass(),
                "changed",
                function(CtClass.voidType, ObservableValueType(), ObjectType(), ObjectType()))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitChangedMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 4);
        String nextClassName = groups[segment + 1].getCompiledClass().getName();
        CtClass nextObservableType = groups[segment + 1].getFirstPathSegment().getTypeInstance().jvmType();
        CtClass firstValueType = groups[segment].getFirstPathSegment().getValueTypeInstance().jvmType();
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
                .checkcast(firstValueType.getName());

            // .foo.bar().baz...
            emitInvariants(firstValueType, path, code);

            code.astore(3);

            // end if
            L0.resume().pop();
        }

        code.aload(0)
            .getfield(generatedClass, NEXT_FIELD, resolver.resolveClass(nextClassName))
            .aload(3)
            .checkcast(nextObservableType.getName())
            .invokevirtual(nextClassName, UPDATE_METHOD, function(CtClass.voidType, nextObservableType))
            .vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }
}
