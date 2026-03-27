// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.generate;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.EmitObservableFunctionNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.Label;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.NameHelper;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jfxcore.compiler.type.Types.*;

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
    private static final String RECEIVER_FIELD = "receiver";
    private static final String INVERSE_RECEIVER_FIELD = "inverseReceiver";

    private final boolean bidirectional;
    private final Callable function;
    private final Callable inverseFunction;
    private final boolean storeReceiver;
    private final boolean storeInverseReceiver;
    private final List<EmitMethodArgumentNode> arguments;
    private final List<FieldDeclaration> paramFields;
    private final List<TypeDeclaration> paramTypes;

    private final TypeInstance superType;
    private final TypeDeclaration returnType;
    private final boolean isNumeric;
    private int numObservables;

    private ConstructorDeclaration constructor;
    private MethodDeclaration getValueMethod;
    private MethodDeclaration getMethod;
    private MethodDeclaration intValueMethod;
    private MethodDeclaration longValueMethod;
    private MethodDeclaration floatValueMethod;
    private MethodDeclaration doubleValueMethod;
    private MethodDeclaration addInvalidationListenerMethod;
    private MethodDeclaration removeInvalidationListenerMethod;
    private MethodDeclaration addChangeListenerMethod;
    private MethodDeclaration removeChangeListenerMethod;
    private MethodDeclaration invalidatedMethod;
    private MethodDeclaration validateMethod;
    private MethodDeclaration connectMethod;
    private MethodDeclaration disconnectMethod;

    private MethodDeclaration setMethod;
    private MethodDeclaration setValueMethod;
    private MethodDeclaration getBeanMethod;
    private MethodDeclaration getNameMethod;
    private MethodDeclaration bindMethod;
    private MethodDeclaration unbindMethod;
    private MethodDeclaration isBoundMethod;
    private MethodDeclaration bindBidirectionalMethod;
    private MethodDeclaration unbindBidirectionalMethod;

    public ObservableFunctionGenerator(
            Callable function,
            @Nullable Callable inverseFunction,
            Collection<? extends EmitMethodArgumentNode> arguments) {
        this.bidirectional = inverseFunction != null;
        this.function = function;
        this.inverseFunction = inverseFunction;
        this.storeReceiver = !function.getBehavior().isStatic() && function.getBehavior() instanceof MethodDeclaration;
        this.storeInverseReceiver = inverseFunction != null &&
            !inverseFunction.getBehavior().isStatic() && inverseFunction.getBehavior() instanceof MethodDeclaration;
        this.arguments = new ArrayList<>(arguments);
        this.paramFields = new ArrayList<>();
        this.paramTypes = new ArrayList<>();

        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        TypeDeclaration returnType = function.getBehavior() instanceof ConstructorDeclaration
            ? function.getBehavior().declaringType()
            : ((MethodDeclaration)function.getBehavior()).returnType();

        if (returnType.equals(booleanDecl()) || returnType.equals(BooleanDecl())) {
            this.superType = invoker.invokeType(bidirectional ? BooleanPropertyDecl() : ObservableBooleanValueDecl());
        } else if (returnType.equals(byteDecl()) || returnType.equals(ByteDecl())
                || returnType.equals(charDecl()) || returnType.equals(CharacterDecl())
                || returnType.equals(shortDecl()) || returnType.equals(ShortDecl())
                || returnType.equals(intDecl()) || returnType.equals(IntegerDecl())) {
            this.superType = invoker.invokeType(bidirectional ? IntegerPropertyDecl() : ObservableIntegerValueDecl());
        } else if (returnType.equals(longDecl()) || returnType.equals(LongDecl())) {
            this.superType = invoker.invokeType(bidirectional ? LongPropertyDecl() : ObservableLongValueDecl());
        } else if (returnType.equals(floatDecl()) || returnType.equals(FloatDecl())) {
            this.superType = invoker.invokeType(bidirectional ? FloatPropertyDecl() : ObservableFloatValueDecl());
        } else if (returnType.equals(doubleDecl()) || returnType.equals(DoubleDecl())) {
            this.superType = invoker.invokeType(bidirectional ? DoublePropertyDecl() : ObservableDoubleValueDecl());
        } else {
            this.superType = invoker.invokeType(
                bidirectional ? PropertyDecl() : ObservableValueDecl(),
                List.of(invoker.invokeType(returnType)));
        }

        this.returnType = new Resolver(SourceInfo.none()).findObservableArgument(superType).declaration();
        this.isNumeric = returnType.isNumeric();
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
    public TypeDeclaration emitClass(BytecodeEmitContext context) {
        TypeDeclaration generatedClass = super.emitClass(context);

        if (superType.declaration().isInterface()) {
            generatedClass.addInterface(superType.declaration());
        } else {
            generatedClass.setSuperClass(superType.declaration());
        }

        generatedClass.addInterface(InvalidationListenerDecl());
        generatedClass.setModifiers(Modifier.PRIVATE | Modifier.FINAL);

        return generatedClass;
    }

    @Override
    public void emitFields(BytecodeEmitContext context) {
        Resolver resolver = new Resolver(SourceInfo.none());
        int fieldNum = 1;

        if (storeReceiver) {
            createField(RECEIVER_FIELD, function.getBehavior().declaringType())
                .setModifiers(Modifier.FINAL | Modifier.PRIVATE);
        }

        if (storeInverseReceiver) {
            createField(INVERSE_RECEIVER_FIELD, inverseFunction.getBehavior().declaringType())
                .setModifiers(Modifier.FINAL | Modifier.PRIVATE);
        }

        for (EmitMethodArgumentNode argument : arguments) {
            for (ValueNode child : argument.getChildren()) {
                if (child instanceof EmitLiteralNode) {
                    continue;
                }

                TypeDeclaration fieldType = isObservableArgument(child) && !bidirectional ?
                    resolver.getObservableClass(TypeHelper.getTypeDeclaration(child), false) :
                    TypeHelper.getTypeDeclaration(child);

                if (isObservableArgument(child)) {
                    paramTypes.add(resolver.findObservableArgument(TypeHelper.getTypeInstance(child)).declaration());
                } else {
                    paramTypes.add(TypeHelper.getTypeDeclaration(child));
                }

                FieldDeclaration paramField = createField("param" + fieldNum, fieldType).setModifiers(Modifier.PRIVATE);
                paramFields.add(paramField);

                if (isObservableArgument(child)) {
                    numObservables++;
                }

                fieldNum++;
            }
        }

        if (numObservables > 0) {
            createField(INVALIDATION_LISTENER_FIELD, InvalidationListenerDecl()).setModifiers(Modifier.PRIVATE);
            createField(CHANGE_LISTENER_FIELD, ChangeListenerDecl()).setModifiers(Modifier.PRIVATE);
        }

        createField(FLAGS_FIELD, intDecl()).setModifiers(Modifier.PRIVATE);
        createField(VALUE_FIELD, returnType.boxed()).setModifiers(Modifier.PRIVATE);

        if (returnType.isPrimitive()) {
            createField(PRIMITIVE_VALUE_FIELD, returnType).setModifiers(Modifier.PRIVATE);
        }
    }

    @Override
    public void emitMethods(BytecodeEmitContext context) {
        super.emitMethods(context);

        constructor = createConstructor(context.getRuntimeContextClass());

        addInvalidationListenerMethod = createMethod("addListener", voidDecl(), InvalidationListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        removeInvalidationListenerMethod = createMethod("removeListener", voidDecl(), InvalidationListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        addChangeListenerMethod = createMethod("addListener", voidDecl(), ChangeListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        removeChangeListenerMethod = createMethod("removeListener", voidDecl(), ChangeListenerDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        invalidatedMethod = createMethod("invalidated", voidDecl(), ObservableDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        getValueMethod = createMethod("getValue", ObjectDecl())
            .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

        if (bidirectional) {
            setValueMethod = createMethod("setValue", voidDecl(), ObjectDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }

        if (returnType.isPrimitive()) {
            getMethod = createMethod("get", returnType)
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            if (bidirectional) {
                setMethod = createMethod("set", voidDecl(), returnType)
                    .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
            }
        }

        if (isNumeric) {
            intValueMethod = createMethod("intValue", intDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            longValueMethod = createMethod("longValue", longDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            floatValueMethod = createMethod("floatValue", floatDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            doubleValueMethod = createMethod("doubleValue", doubleDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }

        TypeDeclaration[] validateParams = returnType.isPrimitive()
            ? new TypeDeclaration[] { booleanDecl() }
            : new TypeDeclaration[0];

        validateMethod = createMethod(VALIDATE_METHOD, voidDecl(), validateParams)
            .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

        if (numObservables > 0) {
            connectMethod = createMethod(CONNECT_METHOD, voidDecl())
                .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            disconnectMethod = createMethod(DISCONNECT_METHOD, voidDecl())
                .setModifiers(Modifier.PRIVATE | Modifier.FINAL);
        }

        if (bidirectional) {
            bindMethod = createMethod("bind", voidDecl(), ObservableValueDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            unbindMethod = createMethod("unbind", voidDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            isBoundMethod = createMethod("isBound", booleanDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            bindBidirectionalMethod = createMethod("bindBidirectional", voidDecl(), PropertyDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            unbindBidirectionalMethod = createMethod("unbindBidirectional", voidDecl(), PropertyDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            getBeanMethod = createMethod("getBean", ObjectDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);

            getNameMethod = createMethod("getName", StringDecl())
                .setModifiers(Modifier.PUBLIC | Modifier.FINAL);
        }
    }

    @Override
    public void emitCode(BytecodeEmitContext context) {
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
            emitNumberValueMethod(intValueMethod, intDecl());
            emitNumberValueMethod(longValueMethod, longDecl());
            emitNumberValueMethod(floatValueMethod, floatDecl());
            emitNumberValueMethod(doubleValueMethod, doubleDecl());
        }

        if (bidirectional) {
            emitNotSupportedMethod(bindMethod);
            emitNotSupportedMethod(unbindMethod);
            emitNotSupportedMethod(bindBidirectionalMethod);
            emitNotSupportedMethod(unbindBidirectionalMethod);
            emitIsBoundMethod(isBoundMethod);
            emitGetBeanOrNameMethod(getBeanMethod, true);
            emitGetBeanOrNameMethod(getNameMethod, false);
        }
    }

    private void emitConstructor(ConstructorDeclaration constructor, BytecodeEmitContext parentContext) {
        BytecodeEmitContext context = new BytecodeEmitContext(parentContext, constructor, 1);
        Bytecode code = context.getOutput();

        code.aload(0)
            .invoke(requireSuperClass().requireDeclaredConstructor());

        if (storeReceiver) {
            code.aload(0);

            for (ValueEmitterNode emitter : function.getReceiver()) {
                context.emit(emitter);
            }

            code.putfield(requireDeclaredField(RECEIVER_FIELD));
        }

        if (storeInverseReceiver) {
            code.aload(0);

            for (ValueEmitterNode emitter : inverseFunction.getReceiver()) {
                context.emit(emitter);
            }

            code.putfield(requireDeclaredField(INVERSE_RECEIVER_FIELD));
        }

        int fieldIdx = 0;

        for (EmitMethodArgumentNode argument : arguments) {
            for (ValueNode child : argument.getChildren()) {
                if (child instanceof EmitLiteralNode) {
                    continue;
                }

                FieldDeclaration field = paramFields.get(fieldIdx++);
                code.aload(0);
                context.emit(child);
                code.putfield(field);
            }
        }

        code.vreturn();

        constructor.setCode(code);
    }

    private void emitAddListenerMethod(MethodDeclaration method, boolean changeListenerIsTrue) {
        Bytecode code = new Bytecode(method);
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        String otherFieldName = changeListenerIsTrue ? INVALIDATION_LISTENER_FIELD : CHANGE_LISTENER_FIELD;

        if (numObservables > 0) {
            code.aload(0)
                .getfield(requireDeclaredField(fieldName))
                .ifnonnull(
                    () -> code
                        .anew(RuntimeExceptionDecl())
                        .dup()
                        .ldc(changeListenerIsTrue ? CHANGE_LISTENERS_ERROR : INVALIDATION_LISTENERS_ERROR)
                        .invoke(RuntimeExceptionDecl().requireDeclaredConstructor(StringDecl()))
                        .athrow(),
                    () -> code
                        .aload(0)
                        .aload(1)
                        .putfield(requireDeclaredField(fieldName)))
                .aload(0)
                .getfield(requireDeclaredField(otherFieldName))
                .ifnull(() -> code
                    .aload(0)
                    .invoke(requireDeclaredMethod(CONNECT_METHOD)));
        }

        code.vreturn();
        method.setCode(code);
    }

    private void emitRemoveListenerMethod(MethodDeclaration method, boolean changeListenerIsTrue) {
        Bytecode code = new Bytecode(method);
        String fieldName = changeListenerIsTrue ? CHANGE_LISTENER_FIELD : INVALIDATION_LISTENER_FIELD;
        String otherFieldName = changeListenerIsTrue ? INVALIDATION_LISTENER_FIELD : CHANGE_LISTENER_FIELD;

        if (numObservables > 0) {
            code.aload(0)
                .getfield(requireDeclaredField(fieldName))
                .ifnonnull(() -> code
                    .aload(0)
                    .getfield(requireDeclaredField(fieldName))
                    .aload(1)
                    .invoke(ObjectDecl().requireDeclaredMethod("equals", ObjectDecl()))
                    .ifne(() -> code
                        .aload(0)
                        .aconst_null()
                        .putfield(requireDeclaredField(fieldName))
                        .aload(0)
                        .getfield(requireDeclaredField(otherFieldName))
                        .ifnull(() -> code
                            .aload(0)
                            .invoke(requireDeclaredMethod(DISCONNECT_METHOD)))));
        }

        code.vreturn();
        method.setCode(code);
    }

    @SuppressWarnings("ConstantConditions")
    private void emitValidateMethod(MethodDeclaration method, BytecodeEmitContext parentContext) {
        var context = new BytecodeEmitContext(parentContext, method, -1);
        Bytecode code = context.getOutput();

        Local valueLocal = code.acquireLocal(returnType);

        if (function.getBehavior() instanceof ConstructorDeclaration) {
            code.anew(function.getBehavior().declaringType())
                .dup();
        } else if (!function.getBehavior().isStatic()) {
            code.aload(0)
                .getfield(requireDeclaredField(RECEIVER_FIELD));
        }

        int fieldIdx = 0;
        Local varargsLocal = null;

        if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).isVarargs()) {
            varargsLocal = code.acquireLocal(false);
        }

        for (EmitMethodArgumentNode argument : arguments) {
            TypeInstance requestedType = argument.isVarargs() ?
                argument.getType().getTypeInstance().componentType() :
                argument.getType().getTypeInstance();

            if (argument.isVarargs()) {
                code.newarray(requestedType.declaration(), argument.getChildren().size())
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
                    FieldDeclaration field = paramFields.get(fieldIdx);
                    TypeDeclaration fieldType = field.type();
                    TypeDeclaration paramType = paramTypes.get(fieldIdx);
                    fieldIdx++;

                    code.aload(0)
                        .getfield(field);

                    if (isObservableArgument(child)) {
                        if (bidirectional) {
                            Local local = code.acquireLocal(fieldType);

                            code.store(fieldType, local)
                                .load(fieldType, local)
                                .ifnull(
                                    () -> code.defaultconst(requestedType.declaration()),
                                    () -> code.load(fieldType, local)
                                              .unboxObservable(fieldType, requestedType.declaration()))
                                .releaseLocal(local);
                        } else {
                            try {
                                code.unboxObservable(fieldType, requestedType.declaration());
                            } catch (IllegalArgumentException ex) {
                                code.unboxObservable(fieldType, paramType)
                                    .autoconv(paramType, requestedType.declaration());
                            }
                        }
                    } else {
                        code.autoconv(fieldType, requestedType.declaration());
                    }
                }

                if (argument.isVarargs()) {
                    code.arraystore(requestedType.declaration());
                }
            }

            if (argument.isVarargs()) {
                code.aload(varargsLocal);
            }
        }

        code.invoke(function.getBehavior());

        if (varargsLocal != null) {
            code.releaseLocal(varargsLocal);
        }

        TypeDeclaration methodReturnType;
        if (function.getBehavior() instanceof MethodDeclaration m) {
            methodReturnType = m.returnType();
        } else if (function.getBehavior() instanceof ConstructorDeclaration c) {
            methodReturnType = c.declaringType();
        } else {
            throw new InternalError();
        }

        code.autoconv(methodReturnType, returnType)
            .store(returnType, valueLocal);

        if (returnType.isPrimitive()) {
            code.aload(0)
                .load(returnType, valueLocal)
                .putfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD));

            code.iload(1)
                .ifne(
                    () -> code
                        .aload(0)
                        .load(returnType, valueLocal)
                        .box(returnType)
                        .putfield(requireDeclaredField(VALUE_FIELD))
                        .aload(0)
                        .iconst(1)
                        .putfield(requireDeclaredField(FLAGS_FIELD)),
                    () -> code
                        .aload(0)
                        .iconst(2)
                        .putfield(requireDeclaredField(FLAGS_FIELD)));
        } else {
            code.aload(0)
                .aload(valueLocal)
                .putfield(requireDeclaredField(VALUE_FIELD))
                .aload(0)
                .iconst(1)
                .putfield(requireDeclaredField(FLAGS_FIELD));
        }

        code.releaseLocal(valueLocal);
        code.vreturn();
        method.setCode(code);
    }

    private void emitGetMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .getfield(requireDeclaredField(FLAGS_FIELD))
            .ifeq(() -> code
                .aload(0)
                .iconst(0)
                .invoke(requireDeclaredMethod(VALIDATE_METHOD, booleanDecl())))
            .aload(0)
            .getfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD))
            .ret(returnType);

        method.setCode(code);
    }

    private void emitSetMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);
        emitSetMethodImpl(method, code);
        method.setCode(code);
    }

    private void emitSetMethodImpl(MethodDeclaration method, Bytecode code) {
        TypeDeclaration sourceObservableType = paramFields.get(0).type();
        TypeDeclaration paramType = method.parameters().get(0).type();

        Resolver resolver = new Resolver(SourceInfo.none());
        TypeInvoker invoker = new TypeInvoker(SourceInfo.none());
        TypeDeclaration sourceValueType = resolver.findWritableArgument(invoker.invokeType(sourceObservableType)).declaration();
        FieldDeclaration currentValueField = requireDeclaredField(returnType.isPrimitive() ? PRIMITIVE_VALUE_FIELD : VALUE_FIELD);

        code.aload(0)
            .iconst(0)
            .putfield(requireDeclaredField(FLAGS_FIELD))
            .aload(0)
            .load(paramType, 1)
            .castconv(paramType, returnType)
            .putfield(currentValueField);

        Local convertedValueLocal = code.acquireLocal(sourceValueType);
        int start = code.position();
        TypeDeclaration methodReturnType;

        if (inverseFunction.getBehavior() instanceof ConstructorDeclaration) {
            methodReturnType = invalidatedMethod.declaringType();
            TypeDeclaration inverseDeclaringClass = inverseFunction.getBehavior().declaringType();

            code.anew(inverseDeclaringClass)
                .dup();
        } else {
            methodReturnType = ((MethodDeclaration)inverseFunction.getBehavior()).returnType();

            if (!inverseFunction.getBehavior().isStatic()) {
                code.aload(0)
                    .getfield(requireDeclaredField(INVERSE_RECEIVER_FIELD));
            }
        }

        code.aload(0)
            .getfield(currentValueField)
            .invoke(inverseFunction.getBehavior())
            .autoconv(methodReturnType, sourceValueType)
            .store(sourceValueType, convertedValueLocal);

        Local sourceObservableLocal = code.acquireLocal(false);

        code.aload(0)
            .getfield(paramFields.get(0))
            .astore(sourceObservableLocal)
            .aload(sourceObservableLocal)
            .ifnonnull(() -> {
                code.aload(sourceObservableLocal)
                    .load(sourceValueType, convertedValueLocal);

                if (sourceObservableType.subtypeOf(WritableBooleanValueDecl())) {
                    code.invoke(WritableBooleanValueDecl().requireDeclaredMethod("set", booleanDecl()));
                } else if (sourceObservableType.subtypeOf(WritableIntegerValueDecl())) {
                    code.invoke(WritableIntegerValueDecl().requireDeclaredMethod("set", intDecl()));
                } else if (sourceObservableType.subtypeOf(WritableLongValueDecl())) {
                    code.invoke(WritableLongValueDecl().requireDeclaredMethod("set", longDecl()));
                } else if (sourceObservableType.subtypeOf(WritableFloatValueDecl())) {
                    code.invoke(WritableFloatValueDecl().requireDeclaredMethod("set", floatDecl()));
                } else if (sourceObservableType.subtypeOf(WritableDoubleValueDecl())) {
                    code.invoke(WritableDoubleValueDecl().requireDeclaredMethod("set", doubleDecl()));
                } else {
                    code.invoke(WritableValueDecl().requireDeclaredMethod("setValue", ObjectDecl()));
                }
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
            .putfield(requireDeclaredField(FLAGS_FIELD))
            .vreturn()
            .handleException(ThrowableDecl(), start, end, handler);
    }

    private void emitLogException(Bytecode code) {
        Local exceptionLocal = code.acquireLocal(false);
        Local threadLocal = code.acquireLocal(false);

        code.astore(exceptionLocal)
            .invoke(ThreadDecl().requireDeclaredMethod("currentThread"))
            .astore(threadLocal)
            .aload(threadLocal)
            .invoke(ThreadDecl().requireDeclaredMethod("getUncaughtExceptionHandler"))
            .aload(threadLocal)
            .aload(exceptionLocal)
            .invoke(UncaughtExceptionHandlerDecl().requireDeclaredMethod(
                "uncaughtException", ThreadDecl(), ThrowableDecl()));

        code.releaseLocal(exceptionLocal);
        code.releaseLocal(threadLocal);
    }

    private void emitGetValueMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .getfield(requireDeclaredField(FLAGS_FIELD))
            .ifeq(
                () -> {
                    if (returnType.isPrimitive()) {
                        code.aload(0)
                            .iconst(1)
                            .invoke(requireDeclaredMethod(VALIDATE_METHOD, booleanDecl()));
                    } else {
                        code.aload(0)
                            .invoke(requireDeclaredMethod(VALIDATE_METHOD));
                    }
                },
                () -> {
                    if (returnType.isPrimitive()) {
                        code.aload(0)
                            .getfield(requireDeclaredField(FLAGS_FIELD))
                            .iconst(2)
                            .if_icmpeq(() -> code
                                .aload(0)
                                .aload(0)
                                .getfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD))
                                .box(returnType)
                                .putfield(requireDeclaredField(VALUE_FIELD))
                                .aload(0)
                                .iconst(1)
                                .putfield(requireDeclaredField(FLAGS_FIELD)));
                    }
                })
            .aload(0)
            .getfield(requireDeclaredField(VALUE_FIELD))
            .areturn();

        method.setCode(code);
    }

    private void emitSetValueMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (setMethod != null) {
            code.aload(0)
                .load(ObjectDecl(), 1)
                .castconv(ObjectDecl(), returnType)
                .invoke(requireDeclaredMethod("set", returnType))
                .vreturn();
        } else {
            emitSetMethodImpl(method, code);
        }

        method.setCode(code);
    }

    private void emitInvalidatedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        if (numObservables == 0) {
            code.vreturn();
            method.setCode(code);
            return;
        }

        Local oldValue = code.acquireLocal(false);
        Local newValue = code.acquireLocal(false);

        code.aload(0)
            .getfield(requireDeclaredField(FLAGS_FIELD))
            .ifne(() -> code
                .aload(0)
                .iconst(0)
                .putfield(requireDeclaredField(FLAGS_FIELD))
                .aload(0)
                .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                .ifnonnull(() -> {
                    if (returnType.isPrimitive()) {
                        code.aload(0)
                            .getfield(requireDeclaredField(FLAGS_FIELD))
                            .iconst(2)
                            .if_icmpeq(() -> code
                                .aload(0)
                                .aload(0)
                                .getfield(requireDeclaredField(PRIMITIVE_VALUE_FIELD))
                                .box(returnType)
                                .putfield(requireDeclaredField(VALUE_FIELD))
                                .aload(0)
                                .iconst(1)
                                .putfield(requireDeclaredField(FLAGS_FIELD)));
                    }

                    code.aload(0)
                        .getfield(requireDeclaredField(VALUE_FIELD))
                        .astore(oldValue)
                        .aload(0)
                        .invoke(ObservableValueDecl().requireDeclaredMethod("getValue"))
                        .astore(newValue)
                        .aload(0)
                        .getfield(requireDeclaredField(CHANGE_LISTENER_FIELD))
                        .aload(0)
                        .aload(oldValue)
                        .aload(newValue)
                        .invoke(ChangeListenerDecl().requireDeclaredMethod(
                            "changed", ObservableValueDecl(), ObjectDecl(), ObjectDecl()));
                })
                .aload(0)
                .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                .ifnonnull(() -> code
                    .aload(0)
                    .getfield(requireDeclaredField(INVALIDATION_LISTENER_FIELD))
                    .aload(0)
                    .invoke(InvalidationListenerDecl().requireDeclaredMethod(
                        "invalidated", ObservableDecl()))))
            .vreturn();

        code.releaseLocal(oldValue);
        code.releaseLocal(newValue);
        method.setCode(code);
    }

    private void emitConnectDisconnectMethod(MethodDeclaration method, boolean connectIsTrue) {
        Bytecode code = new Bytecode(method);
        int fieldIdx = 0;

        MethodDeclaration listenerMethod = ObservableDecl().requireDeclaredMethod(
            connectIsTrue ? "addListener" : "removeListener", InvalidationListenerDecl());

        for (EmitMethodArgumentNode argument : arguments) {
            for (ValueNode child : argument.getChildren()) {
                if (child instanceof EmitLiteralNode) {
                    continue;
                }

                if (isObservableArgument(child)) {
                    FieldDeclaration field = paramFields.get(fieldIdx);
                    TypeDeclaration fieldType = field.type();

                    code.aload(0)
                        .getfield(field);

                    if (bidirectional) {
                        Local local = code.acquireLocal(fieldType);

                        code.store(fieldType, local)
                            .load(fieldType, local)
                            .ifnonnull(() -> code
                                .load(fieldType, local)
                                .aload(0)
                                .invoke(listenerMethod));

                        code.releaseLocal(local);
                    } else {
                        code.aload(0)
                            .invoke(listenerMethod);
                    }
                }

                fieldIdx++;
            }
        }

        code.vreturn();
        method.setCode(code);
    }

    private void emitNumberValueMethod(MethodDeclaration method, TypeDeclaration primitiveType) {
        Bytecode code = new Bytecode(method);

        code.aload(0)
            .invoke(requireDeclaredMethod("get"))
            .primconv(returnType, primitiveType)
            .ret(primitiveType);

        method.setCode(code);
    }

    private void emitNotSupportedMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.anew(UnsupportedOperationExceptionDecl())
            .dup()
            .invoke(UnsupportedOperationExceptionDecl().requireDeclaredConstructor())
            .athrow();

        method.setCode(code);
    }

    private void emitIsBoundMethod(MethodDeclaration method) {
        Bytecode code = new Bytecode(method);

        code.iconst(0)
            .ireturn();

        method.setCode(code);
    }

    @SuppressWarnings("Convert2MethodRef")
    private void emitGetBeanOrNameMethod(MethodDeclaration method, boolean beanIsTrue) {
        Bytecode code = new Bytecode(method);
        FieldDeclaration field = paramFields.get(0);
        MethodDeclaration getterMethod = ReadOnlyPropertyDecl().requireDeclaredMethod(beanIsTrue ? "getBean" : "getName");

        code.aload(0)
            .getfield(field);

        if (bidirectional) {
            Local local = code.acquireLocal(field.type());

            code.store(field.type(), local)
                .load(field.type(), local)
                .ifnull(
                    () -> code.aconst_null(),
                    () -> code.load(field.type(), local)
                              .invoke(getterMethod))
                .areturn();
        } else {
            code.invoke(getterMethod)
                .areturn();
        }

        method.setCode(code);
    }

    boolean isObservableArgument(ValueNode node) {
        return node instanceof EmitObservablePathNode || node instanceof EmitObservableFunctionNode;
    }
}
