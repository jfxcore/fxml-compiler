// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.expression.path.FoldedGroup;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Descriptors;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.List;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

abstract class PropertySegmentGeneratorBase extends SegmentGeneratorBase {

    boolean isNumeric;
    CtClass valueClass;
    CtMethod getValueMethod;
    CtMethod setValueMethod;
    CtMethod getMethod;
    CtMethod setMethod;
    CtMethod intValueMethod;
    CtMethod longValueMethod;
    CtMethod floatValueMethod;
    CtMethod doubleValueMethod;
    CtMethod addInvalidationListenerMethod;
    CtMethod removeInvalidationListenerMethod;
    CtMethod addChangeListenerMethod;
    CtMethod removeChangeListenerMethod;
    CtMethod getBeanMethod;
    CtMethod getNameMethod;

    private CtMethod bindMethod;
    private CtMethod unbindMethod;
    private CtMethod isBoundMethod;
    private CtMethod bindBidirectionalMethod;
    private CtMethod unbindBidirectionalMethod;
    private final TypeInstance type;
    private final List<CtClass> interfaces;

    PropertySegmentGeneratorBase(SourceInfo sourceInfo, FoldedGroup[] groups, int segment) {
        super(sourceInfo, groups, segment);

        interfaces = new ArrayList<>();
        interfaces.add(PropertyType());

        CtClass type = groups[groups.length - 1].getObservableType();
        if (type == null) {
            type = groups[groups.length - 1].getValueType();
        }

        CtClass finalType = type;
        
        if (type == CtClass.booleanType
                || unchecked(sourceInfo, () -> finalType.subtypeOf(ObservableBooleanValueType()))) {
            interfaces.add(ObservableBooleanValueType());
            interfaces.add(WritableBooleanValueType());
            valueClass = CtClass.booleanType;
        } else if (finalType == CtClass.intType
                || finalType == CtClass.shortType
                || finalType == CtClass.byteType
                || finalType == CtClass.charType
                || unchecked(sourceInfo, () -> finalType.subtypeOf(ObservableIntegerValueType()))) {
            interfaces.add(ObservableIntegerValueType());
            interfaces.add(WritableIntegerValueType());
            valueClass = unchecked(sourceInfo, () ->
                finalType.subtypeOf(ObservableIntegerValueType())) ? CtClass.intType : finalType;
            isNumeric = true;
        } else if (finalType == CtClass.longType
                || unchecked(sourceInfo, () -> finalType.subtypeOf(ObservableLongValueType()))) {
            interfaces.add(ObservableLongValueType());
            interfaces.add(WritableLongValueType());
            valueClass = CtClass.longType;
            isNumeric = true;
        } else if (finalType == CtClass.floatType
                || unchecked(sourceInfo, () -> finalType.subtypeOf(ObservableFloatValueType()))) {
            interfaces.add(ObservableFloatValueType());
            interfaces.add(WritableFloatValueType());
            valueClass = CtClass.floatType;
            isNumeric = true;
        } else if (finalType == CtClass.doubleType
                || unchecked(sourceInfo, () -> finalType.subtypeOf(ObservableDoubleValueType()))) {
            interfaces.add(ObservableDoubleValueType());
            interfaces.add(WritableDoubleValueType());
            valueClass = CtClass.doubleType;
            isNumeric = true;
        } else {
            valueClass = groups[groups.length - 1].getValueType();
        }

        Resolver resolver = new Resolver(SourceInfo.none());
        this.type = resolver.getTypeInstance(
            PropertyType(), List.of(resolver.getTypeInstance(TypeHelper.getBoxedType(valueClass))));
    }

    @Override
    public TypeInstance getTypeInstance() {
        return type;
    }

    @Override
    public String getClassName() {
        return groups[segment].getName();
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        clazz = context.getMarkupClass().makeNestedClass(getClassName(), true);
        clazz.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        interfaces.forEach(itf -> clazz.addInterface(itf));
        groups[segment].setCompiledClass(clazz);
        context.getNestedClasses().add(clazz);
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        getValueMethod = new CtMethod(ObjectType(), "getValue", new CtClass[0], clazz);
        getValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        setValueMethod = new CtMethod(CtClass.voidType, "setValue", new CtClass[] {ObjectType()}, clazz);
        setValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        addInvalidationListenerMethod = new CtMethod(
            CtClass.voidType, "addListener", new CtClass[] {InvalidationListenerType()}, clazz);
        addInvalidationListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        removeInvalidationListenerMethod = new CtMethod(
            CtClass.voidType, "removeListener", new CtClass[] {InvalidationListenerType()}, clazz);
        removeInvalidationListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        addChangeListenerMethod = new CtMethod(
            CtClass.voidType, "addListener", new CtClass[] {ChangeListenerType()}, clazz);
        addChangeListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        removeChangeListenerMethod = new CtMethod(
            CtClass.voidType, "removeListener", new CtClass[] {ChangeListenerType()}, clazz);
        removeChangeListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        bindMethod = new CtMethod(CtClass.voidType, "bind", new CtClass[] {ObservableValueType()}, clazz);
        bindMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        unbindMethod = new CtMethod(CtClass.voidType, "unbind", new CtClass[0], clazz);
        unbindMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        isBoundMethod = new CtMethod(CtClass.booleanType, "isBound", new CtClass[0], clazz);
        isBoundMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        bindBidirectionalMethod = new CtMethod(
            CtClass.voidType, "bindBidirectional", new CtClass[] {PropertyType()}, clazz);
        bindBidirectionalMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        unbindBidirectionalMethod = new CtMethod(
            CtClass.voidType, "unbindBidirectional", new CtClass[] {PropertyType()}, clazz);
        unbindBidirectionalMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        getBeanMethod = new CtMethod(ObjectType(), "getBean", new CtClass[0], clazz);
        getBeanMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        getNameMethod = new CtMethod(StringType(), "getName", new CtClass[0], clazz);
        getNameMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        if (valueClass.isPrimitive()) {
            getMethod = new CtMethod(TypeHelper.getWidenedNumericType(valueClass), "get", new CtClass[0], clazz);
            getMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(getMethod);

            setMethod = new CtMethod(CtClass.voidType, "set", new CtClass[] {valueClass}, clazz);
            setMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(setMethod);
        }

        if (isNumeric) {
            intValueMethod = new CtMethod(CtClass.intType, "intValue", new CtClass[0], clazz);
            intValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(intValueMethod);

            longValueMethod = new CtMethod(CtClass.longType, "longValue", new CtClass[0], clazz);
            longValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(longValueMethod);

            floatValueMethod = new CtMethod(CtClass.floatType, "floatValue", new CtClass[0], clazz);
            floatValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(floatValueMethod);

            doubleValueMethod = new CtMethod(CtClass.doubleType, "doubleValue", new CtClass[0], clazz);
            doubleValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(doubleValueMethod);
        }

        clazz.addMethod(getValueMethod);
        clazz.addMethod(setValueMethod);
        clazz.addMethod(addInvalidationListenerMethod);
        clazz.addMethod(removeInvalidationListenerMethod);
        clazz.addMethod(addChangeListenerMethod);
        clazz.addMethod(removeChangeListenerMethod);
        clazz.addMethod(bindMethod);
        clazz.addMethod(unbindMethod);
        clazz.addMethod(isBoundMethod);
        clazz.addMethod(bindBidirectionalMethod);
        clazz.addMethod(unbindBidirectionalMethod);
        clazz.addMethod(getBeanMethod);
        clazz.addMethod(getNameMethod);
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitNotSupportedMethod(bindMethod, 2);
        emitNotSupportedMethod(unbindMethod, 1);
        emitNotSupportedMethod(bindBidirectionalMethod, 2);
        emitNotSupportedMethod(unbindBidirectionalMethod, 2);
        emitIsBoundMethod(isBoundMethod);
    }

    protected void emitNotSupportedMethod(CtMethod method, int maxLocals) throws Exception {
        final String exceptionType = "java.lang.UnsupportedOperationException";
        Bytecode code = new Bytecode(method.getDeclaringClass(), maxLocals);

        code.anew(exceptionType)
            .dup()
            .invokespecial(exceptionType, MethodInfo.nameInit, Descriptors.constructor())
            .athrow();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitIsBoundMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        code.iconst(0)
            .ireturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

}
