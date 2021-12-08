// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;
import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

public class ObservableFunctionGenerator extends GeneratorBase {

    private static final String CHANGE_LISTENERS_ERROR =
        "Cannot add multiple change listeners to a compiled binding.";

    private static final String INVALIDATION_LISTENERS_ERROR =
        "Cannot add multiple invalidation listeners to a compiled binding.";

    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";
    private static final String VALUE_FIELD = "value";
    private static final String PRIMITIVE_VALUE_FIELD = "pvalue";
    private static final String FLAGS_FIELD = "flags";
    private static final String VALIDATE_METHOD = "validate";
    private static final String CONNECT_METHOD = "connect";
    private static final String DISCONNECT_METHOD = "disconnect";

    private final CtBehavior method;
    private final CtBehavior inverseMethod;
    private final boolean storeCallerContext;
    private final boolean bidirectional;
    private final List<ValueEmitterNode> methodReceiver;
    private final CtClass methodReceiverType;
    private final List<EmitMethodArgumentNode> arguments;
    private final List<CtField> paramFields;
    private final List<String> fieldNames;

    private final TypeInstance superType;
    private final CtClass returnType;
    private final boolean isNumeric;
    private int numObservables;

    private CtConstructor constructor;
    private CtMethod getValueMethod;
    private CtMethod getMethod;
    private CtMethod intValueMethod;
    private CtMethod longValueMethod;
    private CtMethod floatValueMethod;
    private CtMethod doubleValueMethod;
    private CtMethod addInvalidationListenerMethod;
    private CtMethod removeInvalidationListenerMethod;
    private CtMethod addChangeListenerMethod;
    private CtMethod removeChangeListenerMethod;
    private CtMethod invalidatedMethod;
    private CtMethod validateMethod;
    private CtMethod connectMethod;
    private CtMethod disconnectMethod;

    private CtMethod setMethod;
    private CtMethod setValueMethod;
    private CtMethod getBeanMethod;
    private CtMethod getNameMethod;
    private CtMethod bindMethod;
    private CtMethod unbindMethod;
    private CtMethod isBoundMethod;
    private CtMethod bindBidirectionalMethod;
    private CtMethod unbindBidirectionalMethod;

    public ObservableFunctionGenerator(
            CtBehavior method,
            CtBehavior inverseMethod,
            List<ValueEmitterNode> methodReceiver,
            Collection<? extends EmitMethodArgumentNode> arguments) {
        this.method = method;
        this.inverseMethod = inverseMethod;
        this.storeCallerContext =
            !Modifier.isStatic(this.method.getModifiers()) && this.method instanceof CtMethod
            || inverseMethod != null && !Modifier.isStatic(inverseMethod.getModifiers());
        this.bidirectional = inverseMethod != null;
        this.methodReceiver = new ArrayList<>(methodReceiver);
        this.methodReceiverType = method.getDeclaringClass();
        this.arguments = new ArrayList<>(arguments);
        this.paramFields = new ArrayList<>();
        this.fieldNames = new ArrayList<>();

        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass returnType = method instanceof CtConstructor ?
            method.getDeclaringClass() : unchecked(SourceInfo.none(), ((CtMethod)method)::getReturnType);

        if (returnType == CtClass.booleanType || returnType == BooleanType()) {
            this.superType = resolver.getTypeInstance(
                bidirectional ? BooleanPropertyType() : ObservableBooleanValueType());
        } else if (returnType == CtClass.byteType || returnType == ByteType()
            || returnType == CtClass.charType || returnType == CharacterType()
            || returnType == CtClass.shortType || returnType == ShortType()
            || returnType == CtClass.intType || returnType == IntegerType()) {
            this.superType = resolver.getTypeInstance(
                bidirectional ? IntegerPropertyType() : ObservableIntegerValueType());
        } else if (returnType == CtClass.longType || returnType == LongType()) {
            this.superType = resolver.getTypeInstance(
                bidirectional ? LongPropertyType() : ObservableLongValueType());
        } else if (returnType == CtClass.floatType || returnType == FloatType()) {
            this.superType = resolver.getTypeInstance(
                bidirectional ? FloatPropertyType() : ObservableFloatValueType());
        } else if (returnType == CtClass.doubleType || returnType == DoubleType()) {
            this.superType = resolver.getTypeInstance(
                bidirectional ? DoublePropertyType() : ObservableDoubleValueType());
        } else {
            this.superType = resolver.getTypeInstance(
                bidirectional ? PropertyType() : ObservableValueType(),
                List.of(resolver.getTypeInstance(returnType)));
        }

        this.returnType = resolver.findObservableArgument(superType).jvmType();
        this.isNumeric = TypeHelper.isNumeric(returnType);
    }

    @Override
    public TypeInstance getTypeInstance() {
        return superType;
    }

    @Override
    public String getClassName() {
        return NameHelper.getUniqueName("Function", this);
    }

    private String mangle(String name) {
        int idx = fieldNames.indexOf(name);
        if (idx >= 0) {
            return "$" + idx;
        }

        fieldNames.add(name);
        return "$" + (fieldNames.size() - 1);
    }

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        clazz = context.getMarkupClass().makeNestedClass(getClassName(), true);

        if (superType.jvmType().isInterface()) {
            clazz.addInterface(superType.jvmType());
        } else {
            clazz.setSuperclass(superType.jvmType());
        }

        clazz.addInterface(InvalidationListenerType());
        clazz.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        context.getNestedClasses().add(clazz);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        int fieldNum = 1;
        CtField field;

        if (storeCallerContext) {
            field = new CtField(methodReceiverType, mangle("context0"), clazz);
            field.setModifiers(Modifier.FINAL | Modifier.PRIVATE);
            clazz.addField(field);
        }

        for (EmitMethodArgumentNode argument : arguments) {
            for (ValueNode child : argument.getChildren()) {
                if (child instanceof EmitLiteralNode) {
                    continue;
                }

                CtClass fieldType = isObservableArgument(child) && !bidirectional ?
                    resolver.getObservableClass(TypeHelper.getJvmType(child), false) :
                    TypeHelper.getJvmType(child);

                CtField paramField = new CtField(fieldType, mangle("param" + fieldNum), clazz);

                paramField.setModifiers(Modifier.PRIVATE);
                paramFields.add(paramField);
                clazz.addField(paramField);

                if (isObservableArgument(child)) {
                    numObservables++;
                }

                fieldNum++;
            }
        }

        if (numObservables > 0) {
            field = new CtField(InvalidationListenerType(), mangle(INVALIDATION_LISTENER_FIELD), clazz);
            field.setModifiers(Modifier.PRIVATE);
            clazz.addField(field);

            field = new CtField(ChangeListenerType(), mangle(CHANGE_LISTENER_FIELD), clazz);
            field.setModifiers(Modifier.PRIVATE);
            clazz.addField(field);
        }

        field = new CtField(CtClass.intType, mangle(FLAGS_FIELD), clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        field = new CtField(TypeHelper.getBoxedType(returnType), mangle(VALUE_FIELD), clazz);
        field.setModifiers(Modifier.PRIVATE);
        clazz.addField(field);

        if (returnType.isPrimitive()) {
            field = new CtField(returnType, mangle(PRIMITIVE_VALUE_FIELD), clazz);
            field.setModifiers(Modifier.PRIVATE);
            clazz.addField(field);
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        clazz.addConstructor(constructor = new CtConstructor(new CtClass[] {context.getRuntimeContextClass()}, clazz));

        addInvalidationListenerMethod = new CtMethod(
            CtClass.voidType, "addListener", new CtClass[] {InvalidationListenerType()}, clazz);
        addInvalidationListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(addInvalidationListenerMethod);

        removeInvalidationListenerMethod = new CtMethod(
            CtClass.voidType, "removeListener", new CtClass[] {InvalidationListenerType()}, clazz);
        removeInvalidationListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(removeInvalidationListenerMethod);

        addChangeListenerMethod = new CtMethod(
            CtClass.voidType, "addListener", new CtClass[] {ChangeListenerType()}, clazz);
        addChangeListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(addChangeListenerMethod);

        removeChangeListenerMethod = new CtMethod(
            CtClass.voidType, "removeListener", new CtClass[] {ChangeListenerType()}, clazz);
        removeChangeListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(removeChangeListenerMethod);

        invalidatedMethod = new CtMethod(CtClass.voidType, "invalidated", new CtClass[] {ObservableType()}, clazz);
        invalidatedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(invalidatedMethod);

        getValueMethod = new CtMethod(ObjectType(), "getValue", new CtClass[0], clazz);
        getValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        clazz.addMethod(getValueMethod);

        if (bidirectional) {
            setValueMethod = new CtMethod(CtClass.voidType, "setValue", new CtClass[] {ObjectType()}, clazz);
            setValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(setValueMethod);
        }

        if (returnType.isPrimitive()) {
            getMethod = new CtMethod(returnType, "get", new CtClass[0], clazz);
            getMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            clazz.addMethod(getMethod);

            if (bidirectional) {
                setMethod = new CtMethod(CtClass.voidType, "set", new CtClass[]{returnType}, clazz);
                setMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
                clazz.addMethod(setMethod);
            }
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

        validateMethod = new CtMethod(
            CtClass.voidType,
            VALIDATE_METHOD,
            returnType.isPrimitive() ? new CtClass[] {CtClass.booleanType} : new CtClass[0],
            clazz);
        validateMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        clazz.addMethod(validateMethod);

        if (numObservables > 0) {
            connectMethod = new CtMethod(CtClass.voidType, CONNECT_METHOD, new CtClass[0], clazz);
            connectMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            clazz.addMethod(connectMethod);

            disconnectMethod = new CtMethod(CtClass.voidType, DISCONNECT_METHOD, new CtClass[0], clazz);
            disconnectMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            clazz.addMethod(disconnectMethod);
        }

        if (bidirectional) {
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

            clazz.addMethod(bindMethod);
            clazz.addMethod(unbindMethod);
            clazz.addMethod(isBoundMethod);
            clazz.addMethod(bindBidirectionalMethod);
            clazz.addMethod(unbindBidirectionalMethod);
            clazz.addMethod(getBeanMethod);
            clazz.addMethod(getNameMethod);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) throws Exception {
        super.emitCode(context);

        emitConstructor(constructor, context);
        emitAddListenerMethod(addInvalidationListenerMethod, false);
        emitAddListenerMethod(addChangeListenerMethod, true);
        emitRemoveListenerMethod(removeInvalidationListenerMethod, false);
        emitRemoveListenerMethod(removeChangeListenerMethod, true);
        emitInvalidatedMethod(invalidatedMethod);
        emitValidateMethod(validateMethod, context);
        emitGetValueMethod(getValueMethod);

        if (setValueMethod != null) {
            emitSetValueMethod(setValueMethod);
        }

        if (connectMethod != null) {
            emitConnectDisconnectMethod(connectMethod, true);
        }

        if (disconnectMethod != null) {
            emitConnectDisconnectMethod(disconnectMethod, false);
        }

        if (getMethod != null) {
            emitGetMethod(getMethod);
        }

        if (setMethod != null) {
            emitSetMethod(setMethod);
        }

        if (isNumeric) {
            emitNumberValueMethod(intValueMethod, CtClass.intType);
            emitNumberValueMethod(longValueMethod, CtClass.longType);
            emitNumberValueMethod(floatValueMethod, CtClass.floatType);
            emitNumberValueMethod(doubleValueMethod, CtClass.doubleType);
        }

        if (bidirectional) {
            emitNotSupportedMethod(bindMethod, 2);
            emitNotSupportedMethod(unbindMethod, 1);
            emitNotSupportedMethod(bindBidirectionalMethod, 2);
            emitNotSupportedMethod(unbindBidirectionalMethod, 2);
            emitIsBoundMethod(isBoundMethod);
            emitGetBeanOrNameMethod(getBeanMethod, true);
            emitGetBeanOrNameMethod(getNameMethod, false);
        }
    }

    private void emitConstructor(CtConstructor constructor, BytecodeEmitContext parentContext) throws Exception {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, clazz, 2, 1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(clazz.getSuperclass(), MethodInfo.nameInit, constructor());

        if (storeCallerContext) {
            code.aload(0);

            for (ValueEmitterNode emitter : methodReceiver) {
                context.emit(emitter);
            }

            code.putfield(clazz, mangle("context0"), methodReceiverType);
        }

        int fieldIdx = 0;

        for (EmitMethodArgumentNode argument : arguments) {
            for (ValueNode child : argument.getChildren()) {
                if (child instanceof EmitLiteralNode) {
                    continue;
                }

                CtField field = paramFields.get(fieldIdx++);
                code.aload(0);
                context.emit(child);
                code.putfield(clazz, field.getName(), field.getType());
            }
        }

        code.vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitAddListenerMethod(CtMethod method, boolean changeListenerIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);
        CtClass declaringClass = method.getDeclaringClass();
        String fieldName = mangle(changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD);
        String otherFieldName = mangle(changeListenerIsTrue ? INVALIDATION_LISTENER_FIELD : CHANGE_LISTENER_FIELD);
        CtClass listenerType = changeListenerIsTrue ? ChangeListenerType() : InvalidationListenerType();
        CtClass otherListenerType = changeListenerIsTrue ? InvalidationListenerType() : ChangeListenerType();

        if (numObservables > 0) {
            code.aload(0)
                .getfield(declaringClass, fieldName, listenerType)
                .ifnonnull(
                    () -> code
                        // throw new RuntimeException()
                        .anew(RuntimeExceptionType())
                        .dup()
                        .ldc(changeListenerIsTrue ? CHANGE_LISTENERS_ERROR : INVALIDATION_LISTENERS_ERROR)
                        .invokespecial(RuntimeExceptionType(), MethodInfo.nameInit,
                                       constructor(StringType()))
                        .athrow(),
                    () -> code
                        // this.listener = $1
                        .aload(0)
                        .aload(1)
                        .putfield(declaringClass, fieldName, listenerType))
                        .aload(0)
                        .getfield(declaringClass, otherFieldName, otherListenerType)
                        .ifnull(() -> code
                            .aload(0)
                            .invokevirtual(declaringClass, CONNECT_METHOD, function(CtClass.voidType)));
        }

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitRemoveListenerMethod(CtMethod method, boolean changeListenerIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);
        CtClass declaringClass = method.getDeclaringClass();
        String fieldName = mangle(changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD);
        String otherFieldName = mangle(changeListenerIsTrue ? INVALIDATION_LISTENER_FIELD : CHANGE_LISTENER_FIELD);
        CtClass listenerType = changeListenerIsTrue ? ChangeListenerType() : InvalidationListenerType();
        CtClass otherListenerType = changeListenerIsTrue ? InvalidationListenerType() : ChangeListenerType();

        if (numObservables > 0) {
            // if (listener != null...
            code.aload(0)
                .getfield(declaringClass, fieldName, listenerType)
                .ifnonnull(() -> code
                    // ... && listener.equals($1))
                    .aload(0)
                    .getfield(declaringClass, fieldName, listenerType)
                    .aload(1)
                    .invokevirtual(ObjectType(), "equals",
                                   function(CtClass.booleanType, ObjectType()))
                    .ifne(() -> code
                        // listener = null
                        .aload(0)
                        .aconst_null()
                        .putfield(declaringClass, fieldName, listenerType)
                        .aload(0)
                        .getfield(declaringClass, otherFieldName, otherListenerType)
                        .ifnull(() -> code
                            .aload(0)
                            .invokevirtual(declaringClass, DISCONNECT_METHOD,
                                           function(CtClass.voidType)))));
        }

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    @SuppressWarnings("ConstantConditions")
    private void emitValidateMethod(CtBehavior method, BytecodeEmitContext parentContext) throws Exception {
        var context = new BytecodeEmitContext(parentContext, clazz, returnType.isPrimitive() ? 2 : 1, -1);
        Bytecode code = context.getOutput();

        Local valueLocal = code.acquireLocal(returnType);

        if (this.method instanceof CtConstructor) {
            code.anew(this.method.getDeclaringClass())
                .dup();
        } else if (!Modifier.isStatic(this.method.getModifiers())) {
            code.aload(0)
                .getfield(clazz, mangle("context0"), methodReceiverType);
        }

        int fieldIdx = 0;
        Local varargsLocal = null;

        if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).isVarargs()) {
            varargsLocal = code.acquireLocal(false);
        }

        for (EmitMethodArgumentNode argument : arguments) {
            TypeInstance requestedType = argument.getType().getTypeInstance();

            if (argument.isVarargs()) {
                code.newarray(requestedType.jvmType(), argument.getChildren().size())
                    .astore(varargsLocal);
            }

            int childIdx = 0;

            for (ValueNode child : argument.getChildren()) {
                if (argument.isVarargs()) {
                    code.aload(varargsLocal)
                        .iconst(childIdx++);
                }

                if (child instanceof EmitLiteralNode) {
                    context.emit(child);
                } else {
                    CtField field = paramFields.get(fieldIdx++);
                    CtClass fieldType = field.getType();

                    code.aload(0)
                        .getfield(clazz, field.getName(), fieldType);

                    if (isObservableArgument(child)) {
                        if (bidirectional) {
                            Local local = code.acquireLocal(fieldType);

                            code.ext_store(fieldType, local)
                                .ext_load(fieldType, local)
                                .ifnull(
                                    () -> code.ext_defaultconst(requestedType.jvmType()),
                                    () -> code.ext_load(fieldType, local)
                                              .ext_ObservableUnbox(child.getSourceInfo(), fieldType,
                                                                   requestedType.jvmType()))
                                .releaseLocal(local);
                        } else {
                            code.ext_ObservableUnbox(child.getSourceInfo(), fieldType, requestedType.jvmType());
                        }
                    } else {
                        code.ext_autoconv(child.getSourceInfo(), fieldType, requestedType.jvmType());
                    }
                }

                if (argument.isVarargs()) {
                    code.ext_arraystore(requestedType.jvmType());
                }
            }

            if (argument.isVarargs()) {
                code.aload(varargsLocal);
            }
        }

        code.ext_invoke(this.method);

        if (varargsLocal != null) {
            code.releaseLocal(varargsLocal);
        }

        code.ext_store(returnType, valueLocal);

        if (returnType.isPrimitive()) {
            // this.pvalue = $valueLocal
            code.aload(0)
                .ext_load(returnType, valueLocal)
                .putfield(clazz, mangle(PRIMITIVE_VALUE_FIELD), returnType);

            // if (boxValue)
            code.iload(1)
                .ifne(
                    () -> code
                        // this.value = $1
                        .aload(0)
                        .ext_load(returnType, valueLocal)
                        .ext_box(returnType)
                        .putfield(clazz, mangle(VALUE_FIELD), TypeHelper.getBoxedType(returnType))
                        .aload(0)
                        .iconst(1) // 1 = valid, boxed
                        .putfield(clazz, mangle(FLAGS_FIELD), CtClass.intType),
                    () -> code
                        .aload(0)
                        .iconst(2) // 2 = valid, unboxed
                        .putfield(clazz, mangle(FLAGS_FIELD), CtClass.intType));
        } else {
            // this.value = $valueLocal
            code.aload(0)
                .aload(valueLocal)
                .putfield(clazz, mangle(VALUE_FIELD), TypeHelper.getBoxedType(returnType));

            code.aload(0)
                .iconst(1) // 1 = valid, boxed
                .putfield(clazz, mangle(FLAGS_FIELD), CtClass.intType);
        }

        code.releaseLocal(valueLocal);
        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitGetMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        // if (this.valid == 0)
        code.aload(0)
            .getfield(method.getDeclaringClass(), mangle(FLAGS_FIELD), CtClass.intType)
            .ifeq(() -> code
                // validate(false)
                .aload(0)
                .iconst(0)
                .invokevirtual(
                    method.getDeclaringClass(),
                    VALIDATE_METHOD,
                    function(CtClass.voidType, CtClass.booleanType)))
            .aload(0)
            .getfield(method.getDeclaringClass(), mangle(PRIMITIVE_VALUE_FIELD), returnType)
            .ext_return(method.getReturnType());

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitSetMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1 + TypeHelper.getSlots(returnType));
        emitSetMethodImpl(method, code);
        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitSetMethodImpl(CtMethod method, Bytecode code) throws Exception {
        CtClass sourceObservableType = paramFields.get(0).getType();
        CtClass paramType = method.getParameterTypes()[0];

        Resolver resolver = new Resolver(SourceInfo.none());
        CtClass sourceValueType = resolver.findWritableArgument(
            resolver.getTypeInstance(sourceObservableType)).jvmType();

        code.aload(0)
            .iconst(0)
            .putfield(clazz, mangle(FLAGS_FIELD), CtClass.intType);

        code.aload(0)
            .ext_load(paramType, 1)
            .ext_castconv(SourceInfo.none(), paramType, returnType)
            .putfield(clazz, mangle(returnType.isPrimitive() ? PRIMITIVE_VALUE_FIELD : VALUE_FIELD), returnType);

        Local convertedValueLocal = code.acquireLocal(sourceValueType);
        int start = code.position();
        CtClass methodReturnType;

        if (inverseMethod instanceof CtConstructor) {
            methodReturnType = invalidatedMethod.getDeclaringClass();

            code.anew(inverseMethod.getDeclaringClass())
                .dup();
        } else {
            methodReturnType = unchecked(SourceInfo.none(), ((CtMethod)inverseMethod)::getReturnType);

            if (!Modifier.isStatic(inverseMethod.getModifiers())) {
                code.aload(0)
                    .getfield(clazz, mangle("context0"), methodReceiverType);
            }
        }

        code.aload(0)
            .getfield(clazz, mangle(returnType.isPrimitive() ? PRIMITIVE_VALUE_FIELD : VALUE_FIELD), returnType);

        code.ext_invoke(inverseMethod)
            .ext_autoconv(SourceInfo.none(), methodReturnType, sourceValueType)
            .ext_store(sourceValueType, convertedValueLocal);

        Local sourceObservableLocal = code.acquireLocal(false);

        code.aload(0)
            .getfield(clazz, paramFields.get(0).getName(), sourceObservableType)
            .astore(sourceObservableLocal)
            .aload(sourceObservableLocal)
            .ifnonnull(() -> {
                code.aload(sourceObservableLocal)
                    .ext_load(sourceValueType, convertedValueLocal);

                unchecked(SourceInfo.none(), () -> {
                    if (sourceObservableType.subtypeOf(WritableBooleanValueType())) {
                        code.invokeinterface(WritableBooleanValueType(), "set",
                                             function(CtClass.voidType, CtClass.booleanType));
                    } else if (sourceObservableType.subtypeOf(WritableIntegerValueType())) {
                        code.invokeinterface(WritableIntegerValueType(), "set",
                                             function(CtClass.voidType, CtClass.intType));
                    } else if (sourceObservableType.subtypeOf(WritableLongValueType())) {
                        code.invokeinterface(WritableLongValueType(), "set",
                                             function(CtClass.voidType, CtClass.longType));
                    } else if (sourceObservableType.subtypeOf(WritableFloatValueType())) {
                        code.invokeinterface(WritableFloatValueType(), "set",
                                             function(CtClass.voidType, CtClass.floatType));
                    } else if (sourceObservableType.subtypeOf(WritableDoubleValueType())) {
                        code.invokeinterface(WritableDoubleValueType(), "set",
                                             function(CtClass.voidType, CtClass.doubleType));
                    } else {
                        code.invokeinterface(WritableValueType(), "setValue",
                                             function(CtClass.voidType, ObjectType()));
                    }
                });
            });

        code.releaseLocal(sourceObservableLocal);
        code.releaseLocal(convertedValueLocal);

        int end = code.position();
        Label label = code.goto_label();
        int handler = code.position();
        emitLogException(code);
        code.addExtraStackSize(1);
        label.resume();

        code.aload(0)
            .iconst(returnType.isPrimitive() ? 2 : 1)
            .putfield(clazz, mangle(FLAGS_FIELD), CtClass.intType);

        code.vreturn();

        code.getExceptionTable().add(
            start, end, handler, code.getConstPool().addClassInfo(ThrowableType()));
    }

    private void emitLogException(Bytecode code) {
        Local exceptionLocal = code.acquireLocal(false);
        Local threadLocal = code.acquireLocal(false);

        code.astore(exceptionLocal)
            .invokestatic(ThreadType(), "currentThread", function(ThreadType()))
            .astore(threadLocal)
            .aload(threadLocal)
            .invokevirtual(ThreadType(), "getUncaughtExceptionHandler", function(UncaughtExceptionHandlerType()))
            .aload(threadLocal)
            .aload(exceptionLocal)
            .invokeinterface(UncaughtExceptionHandlerType(), "uncaughtException",
                             function(CtClass.voidType, ThreadType(), ThrowableType()));

        code.releaseLocal(exceptionLocal);
        code.releaseLocal(threadLocal);
    }

    private void emitGetValueMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);
        CtClass declaringClass = method.getDeclaringClass();

        code.aload(0)
            .getfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType)
            .ifeq(
                // if (this.flags == 0)
                () -> {
                    if (returnType.isPrimitive()) {
                        // validate(true)
                        code.aload(0)
                            .iconst(1)
                            .invokevirtual(declaringClass, VALIDATE_METHOD,
                                           function(CtClass.voidType, CtClass.booleanType));
                    } else {
                        // validate()
                        code.aload(0)
                            .invokevirtual(declaringClass, VALIDATE_METHOD, function(CtClass.voidType));
                    }
                },

                // if (this.flags == 2)
                () -> {
                    if (returnType.isPrimitive()) {
                        code.aload(0)
                            .getfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType)
                            .iconst(2)
                            .if_icmpeq(() -> code
                                .aload(0)
                                .aload(0)
                                .getfield(declaringClass, mangle(PRIMITIVE_VALUE_FIELD), returnType)
                                .ext_box(returnType)
                                .putfield(declaringClass, mangle(VALUE_FIELD), TypeHelper.getBoxedType(returnType))
                                .aload(0)
                                .iconst(1)
                                .putfield(declaringClass, mangle(FLAGS_FIELD), CtClass.intType));
                    }
                });

        // return this.value
        code.aload(0)
            .getfield(declaringClass, mangle(VALUE_FIELD), TypeHelper.getBoxedType(returnType))
            .areturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitSetValueMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);

        if (setMethod != null) {
            code.aload(0)
                .ext_load(ObjectType(), 1)
                .ext_castconv(SourceInfo.none(), ObjectType(), returnType)
                .invokevirtual(method.getDeclaringClass(), "set", function(CtClass.voidType, returnType))
                .vreturn();
        } else {
            emitSetMethodImpl(method, code);
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitInvalidatedMethod(CtMethod method) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);

        if (numObservables == 0) {
            code.vreturn();
            method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
            method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
            return;
        }

        Local oldValue = code.acquireLocal(false);
        Local newValue = code.acquireLocal(false);

        code.aload(0)
            .getfield(clazz, mangle(FLAGS_FIELD), CtClass.intType)
            .ifne(() -> code
                .aload(0)
                .iconst(0)
                .putfield(clazz, mangle(FLAGS_FIELD), CtClass.intType)
                .aload(0)
                .getfield(clazz, mangle(CHANGE_LISTENER_FIELD), ChangeListenerType())
                .ifnonnull(() -> {
                    if (returnType.isPrimitive()) {
                        // if (this.flags == 2)
                        code.aload(0)
                            .getfield(clazz, mangle(FLAGS_FIELD), CtClass.intType)
                            .iconst(2)
                            .if_icmpeq(() -> code
                                // this.value = this.pvalue
                                .aload(0)
                                .aload(0)
                                .getfield(clazz, mangle(PRIMITIVE_VALUE_FIELD), returnType)
                                .ext_box(returnType)
                                .putfield(clazz, mangle(VALUE_FIELD), TypeHelper.getBoxedType(returnType))

                                // this.flags = 1
                                .aload(0)
                                .iconst(1)
                                .putfield(clazz, mangle(FLAGS_FIELD), CtClass.intType));
                    }

                    // oldValue = this.value
                    code.aload(0)
                        .getfield(clazz, mangle(VALUE_FIELD), TypeHelper.getBoxedType(returnType))
                        .astore(oldValue);

                    // newValue = getValue()
                    code.aload(0)
                        .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                        .astore(newValue);

                    // this.changeListener.changed(this, oldValue, newValue)
                    code.aload(0)
                        .getfield(clazz, mangle(CHANGE_LISTENER_FIELD), ChangeListenerType())
                        .aload(0)
                        .aload(oldValue)
                        .aload(newValue)
                        .invokeinterface(ChangeListenerType(), "changed",
                                         function(CtClass.voidType, ObservableValueType(), ObjectType(), ObjectType()));
                })
                .aload(0)
                .getfield(clazz, mangle(INVALIDATION_LISTENER_FIELD), InvalidationListenerType())
                .ifnonnull(() -> code
                    .aload(0)
                    .getfield(clazz, mangle(INVALIDATION_LISTENER_FIELD), InvalidationListenerType())
                    .aload(0)
                    .invokeinterface(InvalidationListenerType(), "invalidated",
                                     function(CtClass.voidType, ObservableType()))))
            .vreturn();

        code.releaseLocal(oldValue);
        code.releaseLocal(newValue);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitConnectDisconnectMethod(CtMethod method, boolean connectIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);
        int fieldIdx = 0;

        for (EmitMethodArgumentNode argument : arguments) {
            for (ValueNode child : argument.getChildren()) {
                if (child instanceof EmitLiteralNode) {
                    continue;
                }

                if (isObservableArgument(child)) {
                    CtField field = paramFields.get(fieldIdx);
                    CtClass fieldType = field.getType();

                    code.aload(0)
                        .getfield(clazz, field.getName(), fieldType);

                    if (bidirectional) {
                        Local local = code.acquireLocal(fieldType);

                        code.ext_store(fieldType, local)
                            .ext_load(fieldType, local)
                            .ifnonnull(() -> code
                                .ext_load(fieldType, local)
                                .aload(0)
                                .invokeinterface(
                                    ObservableType(),
                                    connectIsTrue ? "addListener" : "removeListener",
                                    function(CtClass.voidType, InvalidationListenerType())));

                        code.releaseLocal(local);
                    } else {
                        code.aload(0)
                            .invokeinterface(
                                ObservableType(),
                                connectIsTrue ? "addListener" : "removeListener",
                                function(CtClass.voidType, InvalidationListenerType()));
                    }
                }

                fieldIdx++;
            }
        }

        code.vreturn();

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitNumberValueMethod(CtMethod method, CtClass primitiveType) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);

        code.aload(0)
            .invokevirtual(method.getDeclaringClass(), "get", function(returnType))
            .ext_primitiveconv(returnType, primitiveType)
            .ext_return(primitiveType);

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    private void emitNotSupportedMethod(CtMethod method, int maxLocals) throws Exception {
        final String exceptionType = "java.lang.UnsupportedOperationException";
        Bytecode code = new Bytecode(method.getDeclaringClass(), maxLocals);

        code.anew(exceptionType)
            .dup()
            .invokespecial(exceptionType, MethodInfo.nameInit, constructor())
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

    @SuppressWarnings("Convert2MethodRef")
    private void emitGetBeanOrNameMethod(CtMethod method, boolean beanIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 1);
        CtClass fieldType = paramFields.get(0).getType();
        String methodName = beanIsTrue ? "getBean" : "getName";
        String methodSignature = function(beanIsTrue ? ObjectType() : StringType());

        code.aload(0)
            .getfield(clazz, mangle("param1"), fieldType);

        if (bidirectional) {
            Local local = code.acquireLocal(fieldType);

            code.ext_store(fieldType, local)
                .ext_load(fieldType, local)
                .ifnull(
                    () -> code.aconst_null(),
                    () -> code.ext_load(fieldType, local)
                              .invokeinterface(PropertyType(), methodName, methodSignature))
                .areturn();
        } else {
            code.invokeinterface(PropertyType(), methodName, methodSignature)
                .areturn();
        }

        method.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        method.getMethodInfo().rebuildStackMap(method.getDeclaringClass().getClassPool());
    }

    boolean isObservableArgument(ValueNode node) {
        return node instanceof EmitObservablePathNode || node instanceof EmitObservableFunctionNode;
    }

}
