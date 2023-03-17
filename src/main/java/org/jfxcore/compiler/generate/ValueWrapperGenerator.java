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
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;

import static org.jfxcore.compiler.generate.SharedMethodImpls.*;
import static org.jfxcore.compiler.util.Classes.*;

public abstract class ValueWrapperGenerator extends ClassGenerator {

    public static ClassGenerator newInstance(CtClass valueType) {
        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass primitiveType = TypeHelper.getPrimitiveType(valueType);

        if (primitiveType == CtClass.booleanType) {
            return new ValueWrapperGenerator(
                    resolver.getTypeInstance(ObservableBooleanValueType()), CtClass.booleanType, BooleanType()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("BooleanConstant");
                }
            };
        } else if (primitiveType == CtClass.intType) {
            return new ValueWrapperGenerator(
                    resolver.getTypeInstance(ObservableIntegerValueType()), CtClass.intType, IntegerType()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("IntegerConstant");
                }
            };
        } else if (primitiveType == CtClass.longType) {
            return new ValueWrapperGenerator(
                    resolver.getTypeInstance(ObservableLongValueType()), CtClass.longType, LongType()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("LongConstant");
                }
            };
        } else if (primitiveType == CtClass.floatType) {
            return new ValueWrapperGenerator(
                    resolver.getTypeInstance(ObservableFloatValueType()), CtClass.floatType, FloatType()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("FloatConstant");
                }
            };
        } else if (primitiveType == CtClass.doubleType) {
            return new ValueWrapperGenerator(
                    resolver.getTypeInstance(ObservableDoubleValueType()), CtClass.doubleType, DoubleType()) {
                @Override public String getClassName() {
                    return NameHelper.getMangledClassName("DoubleConstant");
                }
            };
        }

        return new ValueWrapperGenerator(
                resolver.getTypeInstance(ObservableObjectValueType()), null, ObjectType()) {
            @Override public String getClassName() {
                return NameHelper.getMangledClassName("ObjectConstant");
            }
        };
    }

    private static final String BOXED_FIELD = "value";
    private static final String PRIMITIVE_FIELD = "primitiveValue";
    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";

    private final TypeInstance observableType;
    private final CtClass primitiveType;
    private final CtClass boxedType;
    private CtConstructor primitiveConstructor;
    private CtConstructor boxedConstructor;
    private CtMethod getMethod;
    private CtMethod getValueMethod;
    private CtMethod intValueMethod;
    private CtMethod longValueMethod;
    private CtMethod floatValueMethod;
    private CtMethod doubleValueMethod;

    ValueWrapperGenerator(TypeInstance observableType, CtClass primitiveType, CtClass boxedType) {
        this.observableType = observableType;
        this.primitiveType = primitiveType;
        this.boxedType = boxedType;
    }

    @Override
    public TypeInstance getTypeInstance() {
        return observableType;
    }

    @Override
    public void emitClass(BytecodeEmitContext context) {
        generatedClass = context.getNestedClasses().create(getClassName());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addInterface(observableType.jvmType());
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        CtField field = new CtField(boxedType, BOXED_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addField(field);

        if (primitiveType != null) {
            field = new CtField(primitiveType, PRIMITIVE_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            generatedClass.addField(field);
        }

        field = new CtField(InvalidationListenerType(), INVALIDATION_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(ChangeListenerType(), CHANGE_LISTENER_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        if (primitiveType != null) {
            primitiveConstructor = new CtConstructor(new CtClass[]{primitiveType}, generatedClass);
            generatedClass.addConstructor(primitiveConstructor);
        }

        boxedConstructor = new CtConstructor(new CtClass[] {boxedType}, generatedClass);
        generatedClass.addConstructor(boxedConstructor);

        getMethod = new CtMethod(primitiveType != null ? primitiveType : boxedType, "get", new CtClass[0], generatedClass);
        getMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(getMethod);

        getValueMethod = new CtMethod(boxedType, "getValue", new CtClass[0], generatedClass);
        getValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(getValueMethod);

        createListenerMethods(context, generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType());
        createListenerMethods(context, generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType());

        if (primitiveType != null && TypeHelper.isNumericPrimitive(primitiveType)) {
            intValueMethod = new CtMethod(CtClass.intType, "intValue", new CtClass[0], generatedClass);
            intValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(intValueMethod);

            longValueMethod = new CtMethod(CtClass.longType, "longValue", new CtClass[0], generatedClass);
            longValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(longValueMethod);

            floatValueMethod = new CtMethod(CtClass.floatType, "floatValue", new CtClass[0], generatedClass);
            floatValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(floatValueMethod);

            doubleValueMethod = new CtMethod(CtClass.doubleType, "doubleValue", new CtClass[0], generatedClass);
            doubleValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(doubleValueMethod);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        if (primitiveType != null) {
            emitPrimitiveConstructor(context, primitiveConstructor);
        }

        emitBoxedConstructor(context, boxedConstructor);
        emitGetValueMethod(context, getValueMethod);
        emitGetMethod(context, getMethod);


        if (primitiveType != null && TypeHelper.isNumericPrimitive(primitiveType)) {
            emitConversionMethods(context);
        }
    }

    private void emitPrimitiveConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, TypeHelper.getSlots(primitiveType) + 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, Descriptors.constructor())
            .aload(0)
            .ext_load(primitiveType, 1)
            .putfield(generatedClass, PRIMITIVE_FIELD, primitiveType)
            .aload(0)
            .ext_load(primitiveType, 1)
            .ext_autoconv(SourceInfo.none(), primitiveType, boxedType)
            .putfield(generatedClass, BOXED_FIELD, boxedType)
            .vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitBoxedConstructor(BytecodeEmitContext parentContext, CtConstructor constructor) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, Descriptors.constructor())
            .aload(0)
            .ext_load(boxedType, 1)
            .putfield(generatedClass, BOXED_FIELD, boxedType);

        if (primitiveType != null) {
            code.aload(0)
                .aload(1)
                .ext_autoconv(SourceInfo.none(), boxedType, primitiveType)
                .putfield(generatedClass, PRIMITIVE_FIELD, primitiveType);
        }

        code.vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitGetMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        CtClass type = primitiveType != null ? primitiveType : boxedType;
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, primitiveType != null ? PRIMITIVE_FIELD : BOXED_FIELD, type)
            .ext_return(type);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitGetValueMethod(BytecodeEmitContext parentContext, CtMethod method) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 1, -1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .getfield(generatedClass, BOXED_FIELD, boxedType)
            .ext_return(boxedType);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

    private void emitConversionMethods(BytecodeEmitContext parentContext) throws Exception {
        var context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        context.getOutput()
            .aload(0)
            .getfield(generatedClass, PRIMITIVE_FIELD, primitiveType)
            .ext_autoconv(SourceInfo.none(), primitiveType, CtClass.intType)
            .ireturn();

        intValueMethod.getMethodInfo().setCodeAttribute(context.getOutput().toCodeAttribute());
        intValueMethod.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());

        context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        context.getOutput()
            .aload(0)
            .getfield(generatedClass, PRIMITIVE_FIELD, primitiveType)
            .ext_autoconv(SourceInfo.none(), primitiveType, CtClass.longType)
            .lreturn();

        longValueMethod.getMethodInfo().setCodeAttribute(context.getOutput().toCodeAttribute());
        longValueMethod.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());

        context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        context.getOutput()
            .aload(0)
            .getfield(generatedClass, PRIMITIVE_FIELD, primitiveType)
            .ext_autoconv(SourceInfo.none(), primitiveType, CtClass.floatType)
            .freturn();

        floatValueMethod.getMethodInfo().setCodeAttribute(context.getOutput().toCodeAttribute());
        floatValueMethod.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());

        context = new BytecodeEmitContext(parentContext, generatedClass, 2, -1);
        context.getOutput()
            .aload(0)
            .getfield(generatedClass, PRIMITIVE_FIELD, primitiveType)
            .ext_autoconv(SourceInfo.none(), primitiveType, CtClass.doubleType)
            .dreturn();

        doubleValueMethod.getMethodInfo().setCodeAttribute(context.getOutput().toCodeAttribute());
        doubleValueMethod.getMethodInfo().rebuildStackMap(generatedClass.getClassPool());
    }

}
