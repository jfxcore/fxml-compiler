// Copyright (c) 2022, 2025, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.MethodInfo;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import org.jfxcore.compiler.util.TypeInvoker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jfxcore.compiler.util.Classes.*;
import static org.jfxcore.compiler.util.Descriptors.*;
import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

public class ObservableFunctionGenerator extends ClassGenerator {

    private static final String CHANGE_LISTENERS_ERROR =
        "Cannot add multiple change listeners to a compiled binding.";

    private static final String INVALIDATION_LISTENERS_ERROR =
        "Cannot add multiple invalidation listeners to a compiled binding.";

    private static final String INVALIDATION_LISTENER_FIELD = "invalidationListener";
    private static final String CHANGE_LISTENER_FIELD = "changeListener";
    private static final String VALUE_FIELD = "value";
    private static final String PRIMITIVE_VALUE_FIELD = "primitiveValue";
    private static final String FLAGS_FIELD = "flags";
    private static final String VALIDATE_METHOD = "validate";
    private static final String CONNECT_METHOD = "connect";
    private static final String DISCONNECT_METHOD = "disconnect";

    private final boolean bidirectional;
    private final Callable function;
    private final Callable inverseFunction;
    private final boolean storeReceiver;
    private final boolean storeInverseReceiver;
    private final List<EmitMethodArgumentNode> arguments;
    private final List<CtField> paramFields;
    private final List<CtClass> paramTypes;

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
            Callable function,
            @Nullable Callable inverseFunction,
            Collection<? extends EmitMethodArgumentNode> arguments) {
        this.bidirectional = inverseFunction != null;
        this.function = function;
        this.inverseFunction = inverseFunction;
        this.storeReceiver =
            !Modifier.isStatic(function.getBehavior().getModifiers())
            && function.getBehavior() instanceof CtMethod;
        this.storeInverseReceiver = inverseFunction != null &&
            !Modifier.isStatic(inverseFunction.getBehavior().getModifiers())
            && inverseFunction.getBehavior() instanceof CtMethod;
        this.arguments = new ArrayList<>(arguments);
        this.paramFields = new ArrayList<>();
        this.paramTypes = new ArrayList<>();

        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        CtClass returnType = function.getBehavior() instanceof CtConstructor ?
            function.getBehavior().getDeclaringClass() :
            unchecked(SourceInfo.none(), ((CtMethod) function.getBehavior())::getReturnType);

        if (returnType == CtClass.booleanType || returnType == BooleanType()) {
            this.superType = invoker.invokeType(
                bidirectional ? BooleanPropertyType() : ObservableBooleanValueType());
        } else if (returnType == CtClass.byteType || returnType == ByteType()
            || returnType == CtClass.charType || returnType == CharacterType()
            || returnType == CtClass.shortType || returnType == ShortType()
            || returnType == CtClass.intType || returnType == IntegerType()) {
            this.superType = invoker.invokeType(
                bidirectional ? IntegerPropertyType() : ObservableIntegerValueType());
        } else if (returnType == CtClass.longType || returnType == LongType()) {
            this.superType = invoker.invokeType(
                bidirectional ? LongPropertyType() : ObservableLongValueType());
        } else if (returnType == CtClass.floatType || returnType == FloatType()) {
            this.superType = invoker.invokeType(
                bidirectional ? FloatPropertyType() : ObservableFloatValueType());
        } else if (returnType == CtClass.doubleType || returnType == DoubleType()) {
            this.superType = invoker.invokeType(
                bidirectional ? DoublePropertyType() : ObservableDoubleValueType());
        } else {
            this.superType = invoker.invokeType(
                bidirectional ? PropertyType() : ObservableValueType(),
                List.of(invoker.invokeType(returnType)));
        }

        this.returnType = new Resolver(SourceInfo.none()).findObservableArgument(superType).jvmType();
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

    @Override
    public void emitClass(BytecodeEmitContext context) throws Exception {
        generatedClass = context.getNestedClasses().create(getClassName());

        if (superType.jvmType().isInterface()) {
            generatedClass.addInterface(superType.jvmType());
        } else {
            generatedClass.setSuperclass(superType.jvmType());
        }

        generatedClass.addInterface(InvalidationListenerType());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
    }

    @Override
    public void emitFields(BytecodeEmitContext context) throws Exception {
        Resolver resolver = new Resolver(SourceInfo.none());
        int fieldNum = 1;
        CtField field;

        if (storeReceiver) {
            field = new CtField(function.getBehavior().getDeclaringClass(), "receiver", generatedClass);
            field.setModifiers(Modifier.FINAL | Modifier.PRIVATE);
            generatedClass.addField(field);
        }

        if (storeInverseReceiver) {
            field = new CtField(inverseFunction.getBehavior().getDeclaringClass(), "inverseReceiver", generatedClass);
            field.setModifiers(Modifier.FINAL | Modifier.PRIVATE);
            generatedClass.addField(field);
        }

        for (EmitMethodArgumentNode argument : arguments) {
            for (ValueNode child : argument.getChildren()) {
                if (child instanceof EmitLiteralNode) {
                    continue;
                }

                CtClass fieldType = isObservableArgument(child) && !bidirectional ?
                    resolver.getObservableClass(TypeHelper.getJvmType(child), false) :
                    TypeHelper.getJvmType(child);

                if (isObservableArgument(child)) {
                    paramTypes.add(resolver.findObservableArgument(TypeHelper.getTypeInstance(child)).jvmType());
                } else {
                    paramTypes.add(TypeHelper.getJvmType(child));
                }

                CtField paramField = new CtField(fieldType, "param" + fieldNum, generatedClass);

                paramField.setModifiers(Modifier.PRIVATE);
                paramFields.add(paramField);
                generatedClass.addField(paramField);

                if (isObservableArgument(child)) {
                    numObservables++;
                }

                fieldNum++;
            }
        }

        if (numObservables > 0) {
            field = new CtField(InvalidationListenerType(), INVALIDATION_LISTENER_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);

            field = new CtField(ChangeListenerType(), CHANGE_LISTENER_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);
        }

        field = new CtField(CtClass.intType, FLAGS_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        field = new CtField(TypeHelper.getBoxedType(returnType), VALUE_FIELD, generatedClass);
        field.setModifiers(Modifier.PRIVATE);
        generatedClass.addField(field);

        if (returnType.isPrimitive()) {
            field = new CtField(returnType, PRIMITIVE_VALUE_FIELD, generatedClass);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) throws Exception {
        super.emitMethods(context);

        generatedClass.addConstructor(constructor = new CtConstructor(new CtClass[] {context.getRuntimeContextClass()}, generatedClass));

        addInvalidationListenerMethod = new CtMethod(
            CtClass.voidType, "addListener", new CtClass[] {InvalidationListenerType()}, generatedClass);
        addInvalidationListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(addInvalidationListenerMethod);

        removeInvalidationListenerMethod = new CtMethod(
            CtClass.voidType, "removeListener", new CtClass[] {InvalidationListenerType()}, generatedClass);
        removeInvalidationListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(removeInvalidationListenerMethod);

        addChangeListenerMethod = new CtMethod(
            CtClass.voidType, "addListener", new CtClass[] {ChangeListenerType()}, generatedClass);
        addChangeListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(addChangeListenerMethod);

        removeChangeListenerMethod = new CtMethod(
            CtClass.voidType, "removeListener", new CtClass[] {ChangeListenerType()}, generatedClass);
        removeChangeListenerMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(removeChangeListenerMethod);

        invalidatedMethod = new CtMethod(CtClass.voidType, "invalidated", new CtClass[] {ObservableType()}, generatedClass);
        invalidatedMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(invalidatedMethod);

        getValueMethod = new CtMethod(ObjectType(), "getValue", new CtClass[0], generatedClass);
        getValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        generatedClass.addMethod(getValueMethod);

        if (bidirectional) {
            setValueMethod = new CtMethod(CtClass.voidType, "setValue", new CtClass[] {ObjectType()}, generatedClass);
            setValueMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(setValueMethod);
        }

        if (returnType.isPrimitive()) {
            getMethod = new CtMethod(returnType, "get", new CtClass[0], generatedClass);
            getMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            generatedClass.addMethod(getMethod);

            if (bidirectional) {
                setMethod = new CtMethod(CtClass.voidType, "set", new CtClass[]{returnType}, generatedClass);
                setMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);
                generatedClass.addMethod(setMethod);
            }
        }

        if (isNumeric) {
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

        validateMethod = new CtMethod(
            CtClass.voidType,
            VALIDATE_METHOD,
            returnType.isPrimitive() ? new CtClass[] {CtClass.booleanType} : new CtClass[0],
                generatedClass);
        validateMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        generatedClass.addMethod(validateMethod);

        if (numObservables > 0) {
            connectMethod = new CtMethod(CtClass.voidType, CONNECT_METHOD, new CtClass[0], generatedClass);
            connectMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            generatedClass.addMethod(connectMethod);

            disconnectMethod = new CtMethod(CtClass.voidType, DISCONNECT_METHOD, new CtClass[0], generatedClass);
            disconnectMethod.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            generatedClass.addMethod(disconnectMethod);
        }

        if (bidirectional) {
            bindMethod = new CtMethod(CtClass.voidType, "bind", new CtClass[] {ObservableValueType()}, generatedClass);
            bindMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            unbindMethod = new CtMethod(CtClass.voidType, "unbind", new CtClass[0], generatedClass);
            unbindMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            isBoundMethod = new CtMethod(CtClass.booleanType, "isBound", new CtClass[0], generatedClass);
            isBoundMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            bindBidirectionalMethod = new CtMethod(
                CtClass.voidType, "bindBidirectional", new CtClass[] {PropertyType()}, generatedClass);
            bindBidirectionalMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            unbindBidirectionalMethod = new CtMethod(
                CtClass.voidType, "unbindBidirectional", new CtClass[] {PropertyType()}, generatedClass);
            unbindBidirectionalMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            getBeanMethod = new CtMethod(ObjectType(), "getBean", new CtClass[0], generatedClass);
            getBeanMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            getNameMethod = new CtMethod(StringType(), "getName", new CtClass[0], generatedClass);
            getNameMethod.setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            generatedClass.addMethod(bindMethod);
            generatedClass.addMethod(unbindMethod);
            generatedClass.addMethod(isBoundMethod);
            generatedClass.addMethod(bindBidirectionalMethod);
            generatedClass.addMethod(unbindBidirectionalMethod);
            generatedClass.addMethod(getBeanMethod);
            generatedClass.addMethod(getNameMethod);
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
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, generatedClass, 2, 1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invokespecial(generatedClass.getSuperclass(), MethodInfo.nameInit, constructor());

        if (storeReceiver) {
            code.aload(0);

            for (ValueEmitterNode emitter : function.getReceiver()) {
                context.emit(emitter);
            }

            code.putfield(generatedClass, "receiver", function.getBehavior().getDeclaringClass());
        }

        if (storeInverseReceiver) {
            code.aload(0);

            for (ValueEmitterNode emitter : inverseFunction.getReceiver()) {
                context.emit(emitter);
            }

            code.putfield(generatedClass, "inverseReceiver", inverseFunction.getBehavior().getDeclaringClass());
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
                code.putfield(generatedClass, field.getName(), field.getType());
            }
        }

        code.vreturn();

        constructor.getMethodInfo().setCodeAttribute(code.toCodeAttribute());
        constructor.getMethodInfo().rebuildStackMap(constructor.getDeclaringClass().getClassPool());
    }

    private void emitAddListenerMethod(CtMethod method, boolean changeListenerIsTrue) throws Exception {
        Bytecode code = new Bytecode(method.getDeclaringClass(), 2);
        CtClass declaringClass = method.getDeclaringClass();
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        String otherFieldName = changeListenerIsTrue ? INVALIDATION_LISTENER_FIELD : CHANGE_LISTENER_FIELD;
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
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        String otherFieldName = changeListenerIsTrue ? INVALIDATION_LISTENER_FIELD : CHANGE_LISTENER_FIELD;
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
        var context = new BytecodeEmitContext(parentContext, generatedClass, returnType.isPrimitive() ? 2 : 1, -1);
        Bytecode code = context.getOutput();

        Local valueLocal = code.acquireLocal(returnType);

        if (function.getBehavior() instanceof CtConstructor) {
            code.anew(function.getBehavior().getDeclaringClass())
                .dup();
        } else if (!Modifier.isStatic(function.getBehavior().getModifiers())) {
            code.aload(0)
                .getfield(generatedClass, "receiver", function.getBehavior().getDeclaringClass());
        }

        int fieldIdx = 0;
        Local varargsLocal = null;

        if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).isVarargs()) {
            varargsLocal = code.acquireLocal(false);
        }

        for (EmitMethodArgumentNode argument : arguments) {
            TypeInstance requestedType = argument.isVarargs() ?
                argument.getType().getTypeInstance().getComponentType() :
                argument.getType().getTypeInstance();

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
                    CtField field = paramFields.get(fieldIdx);
                    CtClass fieldType = field.getType();
                    CtClass paramType = paramTypes.get(fieldIdx);
                    fieldIdx++;

                    code.aload(0)
                        .getfield(generatedClass, field.getName(), fieldType);

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
                            try {
                                code.ext_ObservableUnbox(child.getSourceInfo(), fieldType, requestedType.jvmType());
                            } catch (IllegalArgumentException ex) {
                                // In some cases, we can't directly unbox an observable, for example when the
                                // field type is ObservableValue<Integer>, and the requested type is int.
                                // The solution is to cast the value to Integer first, then convert to int.
                                code.ext_ObservableUnbox(child.getSourceInfo(), fieldType, paramType)
                                    .ext_autoconv(child.getSourceInfo(), paramType, requestedType.jvmType());
                            }
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

        code.ext_invoke(function.getBehavior());

        if (varargsLocal != null) {
            code.releaseLocal(varargsLocal);
        }

        CtClass methodReturnType;
        if (function.getBehavior() instanceof CtMethod m) {
            methodReturnType = m.getReturnType();
        } else if (function.getBehavior() instanceof CtConstructor c) {
            methodReturnType = c.getDeclaringClass();
        } else {
            throw new InternalError();
        }

        code.ext_autoconv(function.getSourceInfo(), methodReturnType, returnType)
            .ext_store(returnType, valueLocal);

        if (returnType.isPrimitive()) {
            // this.pvalue = $valueLocal
            code.aload(0)
                .ext_load(returnType, valueLocal)
                .putfield(generatedClass, PRIMITIVE_VALUE_FIELD, returnType);

            // if (boxValue)
            code.iload(1)
                .ifne(
                    () -> code
                        // this.value = $1
                        .aload(0)
                        .ext_load(returnType, valueLocal)
                        .ext_box(returnType)
                        .putfield(generatedClass, VALUE_FIELD, TypeHelper.getBoxedType(returnType))
                        .aload(0)
                        .iconst(1) // 1 = valid, boxed
                        .putfield(generatedClass, FLAGS_FIELD, CtClass.intType),
                    () -> code
                        .aload(0)
                        .iconst(2) // 2 = valid, unboxed
                        .putfield(generatedClass, FLAGS_FIELD, CtClass.intType));
        } else {
            // this.value = $valueLocal
            code.aload(0)
                .aload(valueLocal)
                .putfield(generatedClass, VALUE_FIELD, TypeHelper.getBoxedType(returnType));

            code.aload(0)
                .iconst(1) // 1 = valid, boxed
                .putfield(generatedClass, FLAGS_FIELD, CtClass.intType);
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
            .getfield(method.getDeclaringClass(), FLAGS_FIELD, CtClass.intType)
            .ifeq(() -> code
                // validate(false)
                .aload(0)
                .iconst(0)
                .invokevirtual(
                    method.getDeclaringClass(),
                    VALIDATE_METHOD,
                    function(CtClass.voidType, CtClass.booleanType)))
            .aload(0)
            .getfield(method.getDeclaringClass(), PRIMITIVE_VALUE_FIELD, returnType)
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
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        CtClass sourceValueType = resolver.findWritableArgument(invoker.invokeType(sourceObservableType)).jvmType();

        code.aload(0)
            .iconst(0)
            .putfield(generatedClass, FLAGS_FIELD, CtClass.intType);

        code.aload(0)
            .ext_load(paramType, 1)
            .ext_castconv(SourceInfo.none(), paramType, returnType)
            .putfield(generatedClass, returnType.isPrimitive() ? PRIMITIVE_VALUE_FIELD : VALUE_FIELD, returnType);

        Local convertedValueLocal = code.acquireLocal(sourceValueType);
        int start = code.position();
        CtClass methodReturnType;

        if (inverseFunction.getBehavior() instanceof CtConstructor) {
            methodReturnType = invalidatedMethod.getDeclaringClass();

            code.anew(inverseFunction.getBehavior().getDeclaringClass())
                .dup();
        } else {
            methodReturnType = ((CtMethod) inverseFunction.getBehavior()).getReturnType();

            if (!Modifier.isStatic(inverseFunction.getBehavior().getModifiers())) {
                code.aload(0)
                    .getfield(generatedClass, "inverseReceiver",
                              inverseFunction.getBehavior().getDeclaringClass());
            }
        }

        code.aload(0)
            .getfield(generatedClass, returnType.isPrimitive() ? PRIMITIVE_VALUE_FIELD : VALUE_FIELD, returnType);

        code.ext_invoke(inverseFunction.getBehavior())
            .ext_autoconv(SourceInfo.none(), methodReturnType, sourceValueType)
            .ext_store(sourceValueType, convertedValueLocal);

        Local sourceObservableLocal = code.acquireLocal(false);

        code.aload(0)
            .getfield(generatedClass, paramFields.get(0).getName(), sourceObservableType)
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
            .putfield(generatedClass, FLAGS_FIELD, CtClass.intType);

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
            .getfield(declaringClass, FLAGS_FIELD, CtClass.intType)
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
                            .getfield(declaringClass, FLAGS_FIELD, CtClass.intType)
                            .iconst(2)
                            .if_icmpeq(() -> code
                                .aload(0)
                                .aload(0)
                                .getfield(declaringClass, PRIMITIVE_VALUE_FIELD, returnType)
                                .ext_box(returnType)
                                .putfield(declaringClass, VALUE_FIELD, TypeHelper.getBoxedType(returnType))
                                .aload(0)
                                .iconst(1)
                                .putfield(declaringClass, FLAGS_FIELD, CtClass.intType));
                    }
                });

        // return this.value
        code.aload(0)
            .getfield(declaringClass, VALUE_FIELD, TypeHelper.getBoxedType(returnType))
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
            .getfield(generatedClass, FLAGS_FIELD, CtClass.intType)
            .ifne(() -> code
                .aload(0)
                .iconst(0)
                .putfield(generatedClass, FLAGS_FIELD, CtClass.intType)
                .aload(0)
                .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                .ifnonnull(() -> {
                    if (returnType.isPrimitive()) {
                        // if (this.flags == 2)
                        code.aload(0)
                            .getfield(generatedClass, FLAGS_FIELD, CtClass.intType)
                            .iconst(2)
                            .if_icmpeq(() -> code
                                // this.value = this.pvalue
                                .aload(0)
                                .aload(0)
                                .getfield(generatedClass, PRIMITIVE_VALUE_FIELD, returnType)
                                .ext_box(returnType)
                                .putfield(generatedClass, VALUE_FIELD, TypeHelper.getBoxedType(returnType))

                                // this.flags = 1
                                .aload(0)
                                .iconst(1)
                                .putfield(generatedClass, FLAGS_FIELD, CtClass.intType));
                    }

                    // oldValue = this.value
                    code.aload(0)
                        .getfield(generatedClass, VALUE_FIELD, TypeHelper.getBoxedType(returnType))
                        .astore(oldValue);

                    // newValue = getValue()
                    code.aload(0)
                        .invokeinterface(ObservableValueType(), "getValue", function(ObjectType()))
                        .astore(newValue);

                    // this.changeListener.changed(this, oldValue, newValue)
                    code.aload(0)
                        .getfield(generatedClass, CHANGE_LISTENER_FIELD, ChangeListenerType())
                        .aload(0)
                        .aload(oldValue)
                        .aload(newValue)
                        .invokeinterface(ChangeListenerType(), "changed",
                                         function(CtClass.voidType, ObservableValueType(), ObjectType(), ObjectType()));
                })
                .aload(0)
                .getfield(generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
                .ifnonnull(() -> code
                    .aload(0)
                    .getfield(generatedClass, INVALIDATION_LISTENER_FIELD, InvalidationListenerType())
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
                        .getfield(generatedClass, field.getName(), fieldType);

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
            .getfield(generatedClass, "param1", fieldType);

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
