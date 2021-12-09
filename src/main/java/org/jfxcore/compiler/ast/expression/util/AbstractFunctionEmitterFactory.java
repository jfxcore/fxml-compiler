// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.annotation.Annotation;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.BindingContextSelector;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.Operator;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.path.InconvertibleArgumentException;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.text.BooleanNode;
import org.jfxcore.compiler.ast.text.NumberNode;
import org.jfxcore.compiler.ast.text.TextNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import org.jfxcore.compiler.util.NumberUtil;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

abstract class AbstractFunctionEmitterFactory {

    private final TypeInstance invokingType;
    private final Map<MethodInvocationInfoKey, MethodInvocationInfo> methodInvocationCache = new HashMap<>();

    protected AbstractFunctionEmitterFactory(TypeInstance invokingType) {
        this.invokingType = invokingType;
    }

    protected MethodInvocationInfo createMethodInvocation(
            FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {
        var key = new MethodInvocationInfoKey(functionExpression, bidirectional, preferObservable);
        var cachedMethodInfo = methodInvocationCache.get(key);
        if (cachedMethodInfo != null) {
            return cachedMethodInfo;
        }

        PathExpressionNode methodPath = functionExpression.getPath();
        Queue<Node> methodArguments = new ArrayDeque<>(functionExpression.getArguments());
        MethodInfo methodInfo = findMethod(methodPath, null, methodArguments, preferObservable);
        MethodInfo inverseMethodInfo = null;

        Resolver resolver = new Resolver(functionExpression.getSourceInfo());
        boolean isVarArgs = Modifier.isVarArgs(methodInfo.jvmMethod().getModifiers());
        TypeInstance[] paramTypes = resolver.getParameterTypes(methodInfo.jvmMethod(), List.of(invokingType));
        TypeInstance returnType = resolver.getReturnType(methodInfo.jvmMethod(), List.of(invokingType));
        List<EmitMethodArgumentNode> argumentValues = new ArrayList<>();
        boolean observableFunction = false;

        if (!isVarArgs && methodArguments.size() != paramTypes.length
                || isVarArgs && methodArguments.size() < paramTypes.length) {
            throw GeneralErrors.numFunctionArgumentsMismatch(
                SourceInfo.span(methodArguments),
                NameHelper.getLongMethodSignature(methodInfo.jvmMethod()),
                paramTypes.length,
                methodArguments.size());
        }

        for (int i = 0; i < paramTypes.length; ++i) {
            EmitMethodArgumentNode argumentValue;

            if (i < paramTypes.length - 1 || !isVarArgs) {
                Node argument = methodArguments.remove();

                try {
                    argumentValue = createSingleFunctionArgumentValue(
                        argument, paramTypes[i], bidirectional, preferObservable);
                } catch (InconvertibleArgumentException ex) {
                    if (ex.getCause() instanceof MarkupException) {
                        throw (MarkupException)ex.getCause();
                    }

                    throw GeneralErrors.cannotAssignFunctionArgument(
                        argument.getSourceInfo(), NameHelper.getLongMethodSignature(methodInfo.jvmMethod()),
                        i, ex.getTypeName());
                }
            } else {
                argumentValue = createVariadicFunctionArgumentValue(
                    new ArrayList<>(methodArguments), methodInfo.jvmMethod(), i, bidirectional, preferObservable);
            }

            argumentValues.add(argumentValue);

            if (!observableFunction && argumentValue.isObservable()) {
                observableFunction = true;
            }
        }

        if (bidirectional) {
            if (argumentValues.size() != 1) {
                throw BindingSourceErrors.invalidBidirectionalMethodParamCount(
                    SourceInfo.span(methodArguments));
            } else {
                Node argNode = functionExpression.getArguments().get(0);
                if (!(argNode instanceof PathExpressionNode)) {
                    throw BindingSourceErrors.invalidBidirectionalMethodParamKind(argNode.getSourceInfo());
                }
            }

            var inversePath = functionExpression.getInversePath();
            if (inversePath != null) {
                // Synthetic node to represent the value of the return type
                class ReturnValueNode extends AbstractNode implements ValueEmitterNode {
                    final ResolvedTypeNode type = new ResolvedTypeNode(returnType, methodPath.getSourceInfo());
                    public ReturnValueNode() { super(methodPath.getSourceInfo()); }
                    @Override public void emit(BytecodeEmitContext context) {}
                    @Override public ValueEmitterNode deepClone() { return null; }
                    @Override public ResolvedTypeNode getType() {
                        return type;
                    }
                }

                inverseMethodInfo = findMethod(
                    inversePath, paramTypes[0], List.of(new ReturnValueNode()), preferObservable);
            } else {
                inverseMethodInfo = findInverseMethodViaAnnotation(
                    methodInfo, paramTypes[0], returnType, methodPath.getSourceInfo());
            }
        }

        TypeInstance valueType = methodInfo.jvmMethod() instanceof CtConstructor ?
            resolver.getTypeInstance(methodInfo.jvmMethod().getDeclaringClass()) :
            resolver.getReturnType(methodInfo.jvmMethod(), List.of(invokingType));

        var result = new MethodInvocationInfo(
            observableFunction, valueType, methodInfo, inverseMethodInfo, argumentValues);

        methodInvocationCache.put(key, result);

        return result;
    }

    private EmitMethodArgumentNode createVariadicFunctionArgumentValue(
            List<Node> arguments, CtBehavior method, int paramIndex, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = SourceInfo.span(
            arguments.get(0).getSourceInfo(), arguments.get(arguments.size() - 1).getSourceInfo());

        try {
            TypeInstance componentType = Objects.requireNonNull(TypeHelper.tryGetArrayComponentType(method, paramIndex));
            List<EmitMethodArgumentNode> values = new ArrayList<>();

            for (Node argument : arguments) {
                values.add(createSingleFunctionArgumentValue(argument, componentType, bidirectional, preferObservable));
            }

            return new EmitMethodArgumentNode(componentType, values, sourceInfo);
        } catch (InconvertibleArgumentException ex) {
            if (ex.getCause() instanceof MarkupException) {
                throw (MarkupException)ex.getCause();
            }

            throw GeneralErrors.cannotAssignFunctionArgument(
                sourceInfo, NameHelper.getLongMethodSignature(method), paramIndex, ex.getTypeName());
        }
    }

    private EmitMethodArgumentNode createSingleFunctionArgumentValue(
            Node argument, TypeInstance paramType, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = argument.getSourceInfo();
        Resolver resolver = new Resolver(sourceInfo);

        if (argument instanceof BooleanNode booleanArg) {
            if (!paramType.isAssignableFrom(resolver.getTypeInstance(Classes.BooleanType()))) {
                throw new InconvertibleArgumentException(Classes.BooleanName);
            }

            boolean value = Boolean.parseBoolean(booleanArg.getText());

            return new EmitMethodArgumentNode(
                paramType, new EmitLiteralNode(paramType, value, sourceInfo), false, sourceInfo);
        }

        if (argument instanceof NumberNode numberArg) {
            TypeInstance numberType;
            Number value;

            try {
                numberType = NumberUtil.parseType(numberArg.getText());
                value = NumberUtil.parse(numberArg.getText());
            } catch (NumberFormatException ex) {
                throw new InconvertibleArgumentException(Classes.NumberName);
            }

            if (!paramType.isAssignableFrom(numberType)) {
                throw new InconvertibleArgumentException(Classes.NumberName);
            }

            return new EmitMethodArgumentNode(
                paramType, new EmitLiteralNode(paramType, value, sourceInfo), false, sourceInfo);
        }

        if (argument instanceof TextNode textArg) {
            if (!paramType.isAssignableFrom(resolver.getTypeInstance(Classes.StringType()))) {
                throw new InconvertibleArgumentException(Classes.StringName);
            }

            return new EmitMethodArgumentNode(
                paramType,
                new EmitLiteralNode(paramType, textArg.getText(), sourceInfo),
                false,
                sourceInfo);
        }

        if (argument instanceof ExpressionNode) {
            EmitterFactory factory;

            if (argument instanceof FunctionExpressionNode funcExpressionArg) {
                MethodInvocationInfo invocationInfo = createMethodInvocation(
                    funcExpressionArg, false, preferObservable);

                if (invocationInfo.observable()) {
                    factory = new ObservableFunctionEmitterFactory(funcExpressionArg, invokingType);
                } else {
                    factory = new SimpleFunctionEmitterFactory(funcExpressionArg, invokingType);
                }
            } else if (argument instanceof PathExpressionNode pathExpressionArg) {
                ResolvedPath path = pathExpressionArg.resolvePath(preferObservable);

                if (preferObservable && path.isObservable()) {
                    factory = new ObservablePathEmitterFactory(pathExpressionArg);
                } else {
                    factory = new SimplePathEmitterFactory(pathExpressionArg);
                }
            } else {
                throw GeneralErrors.expressionNotApplicable(sourceInfo, false);
            }

            try {
                BindingEmitterInfo emitterInfo = factory instanceof ObservableEmitterFactory observableFactory ?
                    observableFactory.newInstance(bidirectional) : factory.newInstance();

                return new EmitMethodArgumentNode(
                    paramType, emitterInfo.getValue(), emitterInfo.getObservableType() != null, sourceInfo);
            } catch (MarkupException ex) {
                throw new InconvertibleArgumentException(argument.getClass().getName(), ex);
            }
        }

        if (argument instanceof ValueEmitterNode valueEmitterArg) {
            return new EmitMethodArgumentNode(paramType, valueEmitterArg, false, sourceInfo);
        }

        throw new InconvertibleArgumentException(argument.getClass().getName());
    }

    private MethodInfo findMethod(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            Collection<Node> arguments,
            boolean preferObservable) {
        String methodFullName = pathExpression.getPath();
        String methodName;
        CtClass declaringClass;
        ResolvedPath resolvedPath = null;
        boolean maybeInstanceMethod;
        boolean isConstructor = false;

        if (methodFullName.contains(".")) {
            maybeInstanceMethod = false;
            int idx = methodFullName.lastIndexOf('.');
            String className = methodFullName.substring(0, idx);
            methodName = methodFullName.substring(idx + 1);

            try {
                resolvedPath = pathExpression.resolvePath(false, true);
                className = resolvedPath.getValueTypeInstance().getJavaName();
                maybeInstanceMethod = true;
            } catch (MarkupException ignored) {
                // If we don't have a valid path expression, the only other possible interpretation would be
                // a static method call. Since a static method call is not resolved by a path expression, we
                // check that only the default binding context selector is used.
                BindingContextSelector selector = pathExpression.getSource().getSelector();
                if (selector != BindingContextSelector.DEFAULT && selector != BindingContextSelector.TEMPLATED_ITEM) {
                    throw BindingSourceErrors.bindingContextNotApplicable(pathExpression.getSource().getSourceInfo());
                }
            }

            var resolver = new Resolver(pathExpression.getSourceInfo());
            declaringClass = resolver.tryResolveClassAgainstImports(className);

            if (declaringClass == null) {
                declaringClass = resolver.resolveClass(methodFullName);
                isConstructor = true;
            }
        } else {
            maybeInstanceMethod = true;
            methodName = methodFullName;
            declaringClass = pathExpression.getSource().getType().getJvmType();
        }

        List<TypeInstance> argumentTypes = arguments.stream()
            .map(arg -> getArgumentType(arg, preferObservable))
            .collect(Collectors.toList());

        List<SourceInfo> argumentsSourceInfo = arguments.stream()
            .map(Node::getSourceInfo).collect(Collectors.toList());

        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        // First we try to match the identifier against methods.
        // If applicable methods are found, we choose the most specific method.
        if (!isConstructor) {
            CtMethod method = new MethodFinder(invokingType, declaringClass).findMethod(
                methodName,
                returnType,
                argumentTypes,
                argumentsSourceInfo,
                maybeInstanceMethod ? MethodFinder.InvocationType.BOTH : MethodFinder.InvocationType.STATIC,
                diagnostics,
                pathExpression.getSourceInfo());

            if (method != null) {
                return new MethodInfo(getMethodReceiverEmitters(pathExpression, resolvedPath, method), method);
            }
        }

        // If no applicable methods were found, we treat the identifier as the name of a class and
        // see if there is a constructor that accepts our arguments.
        var resolver = new Resolver(pathExpression.getSourceInfo());
        CtClass ctorClass = resolver.tryResolveClass(methodFullName);
        if (ctorClass == null) {
            ctorClass = resolver.tryResolveClassAgainstImports(methodName);
        }

        if (ctorClass != null) {
            CtConstructor constructor = new MethodFinder(invokingType, ctorClass).findConstructor(
                argumentTypes,
                argumentsSourceInfo,
                diagnostics,
                pathExpression.getSourceInfo());

            if (constructor != null) {
                if (returnType != null && !resolver.getReturnType(
                        constructor, List.of(invokingType)).subtypeOf(returnType)) {
                    throw GeneralErrors.incompatibleReturnValue(
                        pathExpression.getSourceInfo(), constructor, returnType);
                }

                return new MethodInfo(Collections.emptyList(), constructor);
            }
        }

        if (diagnostics.size() == 1) {
            throw new MarkupException(diagnostics.get(0).getSourceInfo(), diagnostics.get(0).getDiagnostic());
        }

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.cannotBindFunction(
                pathExpression.getSourceInfo(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        throw SymbolResolutionErrors.methodNotFound(
            pathExpression.getSourceInfo(), declaringClass, methodName);
    }

    private TypeInstance getArgumentType(Node argument, boolean preferObservable) {
        var resolver = new Resolver(argument.getSourceInfo());

        if (argument instanceof FunctionExpressionNode funcExpressionArg) {
            MethodInvocationInfo invocationInfo = createMethodInvocation(funcExpressionArg, false, true);

            if (invocationInfo.method().jvmMethod() instanceof CtConstructor) {
                return resolver.getTypeInstance(invocationInfo.method().jvmMethod().getDeclaringClass());
            } else {
                return resolver.getReturnType(invocationInfo.method().jvmMethod());
            }
        } else if (argument instanceof PathExpressionNode pathExpressionArg) {
            Operator operator = pathExpressionArg.getOperator();

            if (operator == Operator.NOT || operator == Operator.BOOLIFY) {
                return resolver.getTypeInstance(CtClass.booleanType);
            } else {
                return pathExpressionArg.resolvePath(preferObservable).getValueTypeInstance();
            }
        } else if (argument instanceof TextNode) {
            if (argument instanceof BooleanNode) {
                return resolver.getTypeInstance(Classes.BooleanType());
            } else if (argument instanceof NumberNode numberNode) {
                return NumberUtil.parseType(numberNode.getText());
            } else {
                return resolver.getTypeInstance(Classes.StringType());
            }
        } else if (argument instanceof ValueEmitterNode) {
            return TypeHelper.getTypeInstance(argument);
        }

        throw GeneralErrors.expressionNotApplicable(argument.getSourceInfo(), false);
    }

    private List<ValueEmitterNode> getMethodReceiverEmitters(
            PathExpressionNode pathExpression, ResolvedPath resolvedPath, CtMethod method) {
        if (resolvedPath != null) {
            return resolvedPath.toValueEmitters(pathExpression.getSourceInfo());
        }

        if (!Modifier.isStatic(method.getModifiers())) {
            BindingContextNode bindingSource = pathExpression.getSource();
            return List.of(bindingSource.toSegment().toValueEmitter(bindingSource.getSourceInfo()));
        }

        return Collections.emptyList();
    }

    private MethodInfo findInverseMethodViaAnnotation(
            MethodInfo method, TypeInstance argumentType, TypeInstance returnType, SourceInfo sourceInfo) {
        var resolver = new Resolver(sourceInfo);
        Annotation annotation = resolver.tryResolveMethodAnnotation(
            method.jvmMethod(), Classes.InverseMethodAnnotationName);

        if (annotation == null) {
            throw BindingSourceErrors.methodNotInvertible(sourceInfo, method.jvmMethod());
        }

        String methodName = TypeHelper.getAnnotationString(annotation, "value");
        if (methodName == null) {
            throw BindingSourceErrors.invalidInverseMethodAnnotationValue(sourceInfo, method.jvmMethod());
        }

        CtClass declaringClass = method.jvmMethod().getDeclaringClass();
        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        CtMethod jvmMethod = new MethodFinder(invokingType, declaringClass).findMethod(
            methodName, argumentType, List.of(returnType), List.of(sourceInfo),
            MethodFinder.InvocationType.BOTH, diagnostics, sourceInfo);

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.invalidInverseMethod(
                sourceInfo, method.jvmMethod(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        return new MethodInfo(method.receiver(), jvmMethod);
    }

    protected static record MethodInfo(List<ValueEmitterNode> receiver, CtBehavior jvmMethod) {}

    private static record MethodInvocationInfoKey(
        FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {}

    protected static record MethodInvocationInfo(
        boolean observable,
        TypeInstance type,
        MethodInfo method,
        MethodInfo inverseMethod,
        List<EmitMethodArgumentNode> arguments) { }

}
