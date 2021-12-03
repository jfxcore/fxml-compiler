// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.annotation.Annotation;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.emit.EmitLiteralNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.util.ExceptionHelper.unchecked;

abstract class AbstractFunctionEmitterFactory {

    private final TypeInstance invokingType;
    private final Map<MethodInfoKey, MethodInvocationInfo> methodInvocationCache = new HashMap<>();

    protected AbstractFunctionEmitterFactory(TypeInstance invokingType) {
        this.invokingType = invokingType;
    }

    protected MethodInvocationInfo createMethodInvocation(
            FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {
        MethodInfoKey key = new MethodInfoKey(functionExpression, bidirectional, preferObservable);
        MethodInvocationInfo cachedMethodInfo = methodInvocationCache.get(key);
        if (cachedMethodInfo != null) {
            return cachedMethodInfo;
        }

        Resolver resolver = new Resolver(functionExpression.getSourceInfo());
        CtBehavior method = findMethod(functionExpression, preferObservable);
        boolean isVarArgs = Modifier.isVarArgs(method.getModifiers());
        Queue<Node> arguments = new ArrayDeque<>(functionExpression.getArguments());
        TypeInstance[] paramTypes = resolver.getParameterTypes(method, List.of(invokingType));
        List<EmitMethodArgumentNode> argumentValues = new ArrayList<>();
        boolean observableFunction = false;
        CtBehavior inverseMethod = null;

        if (!isVarArgs && arguments.size() != paramTypes.length || isVarArgs && arguments.size() < paramTypes.length) {
            throw GeneralErrors.numFunctionArgumentsMismatch(
                functionExpression.getSourceInfo(), NameHelper.getLongMethodSignature(method),
                paramTypes.length, arguments.size());
        }

        for (int i = 0; i < paramTypes.length; ++i) {
            EmitMethodArgumentNode argumentValue;

            if (i < paramTypes.length - 1 || !isVarArgs) {
                try {
                    argumentValue = createSingleFunctionArgumentValue(
                        arguments.remove(), paramTypes[i], bidirectional, preferObservable);
                } catch (InconvertibleArgumentException ex) {
                    if (ex.getCause() instanceof MarkupException) {
                        throw (MarkupException)ex.getCause();
                    }

                    throw ex;
                }
            } else {
                argumentValue = createVariadicFunctionArgumentValue(
                    new ArrayList<>(arguments), method, i, bidirectional, preferObservable);
            }

            argumentValues.add(argumentValue);

            if (!observableFunction && argumentValue.isObservable()) {
                observableFunction = true;
            }
        }

        if (bidirectional) {
            if (argumentValues.size() != 1) {
                throw BindingSourceErrors.invalidBidirectionalMethodParamCount(functionExpression.getSourceInfo());
            } else {
                Node argNode = functionExpression.getArguments().get(0);
                if (!(argNode instanceof PathExpressionNode)) {
                    throw BindingSourceErrors.invalidBidirectionalMethodParamKind(argNode.getSourceInfo());
                }
            }

            inverseMethod = findInverseMethod((CtMethod)method, functionExpression);
        }

        var result = new MethodInvocationInfo(observableFunction, method, inverseMethod, argumentValues);
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
            if (!paramType.isConvertibleFrom(resolver.getTypeInstance(Classes.BooleanType()))) {
                throw new InconvertibleArgumentException(Classes.BooleanName);
            }

            boolean value = Boolean.parseBoolean(booleanArg.getText());

            return new EmitMethodArgumentNode(
                paramType, new EmitLiteralNode(paramType, value, sourceInfo), false, sourceInfo);
        }

        if (argument instanceof NumberNode numberArg) {
            if (!paramType.isConvertibleFrom(resolver.getTypeInstance(Classes.NumberType()))) {
                throw new InconvertibleArgumentException(Classes.NumberName);
            }

            Number value;
            try {
                value = NumberUtil.parse((numberArg).getText());
            } catch (NumberFormatException ex) {
                throw new InconvertibleArgumentException(Classes.NumberName);
            }

            return new EmitMethodArgumentNode(
                paramType, new EmitLiteralNode(paramType, value, sourceInfo), false, sourceInfo);
        }

        if (argument instanceof TextNode textArg) {
            if (!paramType.isConvertibleFrom(resolver.getTypeInstance(Classes.StringType()))) {
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

    private CtBehavior findMethod(FunctionExpressionNode expressionNode, boolean preferObservable) {
        Resolver resolver = new Resolver(expressionNode.getSourceInfo());
        String methodFullName = expressionNode.getPath().getPath();
        String methodName;
        CtClass declaringClass;
        boolean maybeInstanceMethod;
        boolean isConstructor = false;

        if (methodFullName.contains(".")) {
            BindingContextSelector selector = expressionNode.getPath().getSource().getSelector();
            if (selector != BindingContextSelector.DEFAULT && selector != BindingContextSelector.TEMPLATED_ITEM) {
                throw BindingSourceErrors.invalidBindingContext(expressionNode.getPath().getSource().getSourceInfo());
            }

            maybeInstanceMethod = false;
            int idx = methodFullName.lastIndexOf('.');
            String className = methodFullName.substring(0, idx);
            methodName = methodFullName.substring(idx + 1);
            declaringClass = resolver.tryResolveClassAgainstImports(className);

            if (declaringClass == null) {
                declaringClass = resolver.resolveClass(methodFullName);
                isConstructor = true;
            }
        } else {
            maybeInstanceMethod = true;
            methodName = methodFullName;
            declaringClass = expressionNode.getPath().getSource().getType().getJvmType();
        }

        List<TypeInstance> argumentTypes = new ArrayList<>();

        for (Node argument : expressionNode.getArguments()) {
            if (argument instanceof FunctionExpressionNode funcExpressionArg) {
                MethodInvocationInfo invocationInfo = createMethodInvocation(funcExpressionArg, false, true);

                if (invocationInfo.method() instanceof CtConstructor) {
                    argumentTypes.add(resolver.getTypeInstance(invocationInfo.method().getDeclaringClass()));
                } else {
                    argumentTypes.add(resolver.getReturnType(invocationInfo.method()));
                }
            } else if (argument instanceof PathExpressionNode pathExpressionArg) {
                Operator operator = pathExpressionArg.getOperator();

                if (operator == Operator.NOT || operator == Operator.BOOLIFY) {
                    argumentTypes.add(resolver.getTypeInstance(CtClass.booleanType));
                } else {
                    argumentTypes.add(pathExpressionArg.resolvePath(preferObservable).getValueTypeInstance());
                }
            } else if (argument instanceof TextNode) {
                if (argument instanceof BooleanNode) {
                    argumentTypes.add(resolver.getTypeInstance(Classes.BooleanType()));
                } else if (argument instanceof NumberNode) {
                    argumentTypes.add(resolver.getTypeInstance(Classes.NumberType()));
                } else {
                    argumentTypes.add(resolver.getTypeInstance(Classes.StringType()));
                }
            } else if (argument instanceof ValueEmitterNode) {
                argumentTypes.add(TypeHelper.getTypeInstance(argument));
            } else {
                throw GeneralErrors.expressionNotApplicable(argument.getSourceInfo(), false);
            }
        }

        List<SourceInfo> argsSourceInfo = expressionNode.getArguments().stream()
            .map(Node::getSourceInfo).collect(Collectors.toList());

        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        // First we try to match the identifier against methods.
        // If applicable methods are found, we choose the most specific method.
        if (!isConstructor) {
            CtMethod method = new MethodFinder(invokingType, declaringClass).findMethod(
                methodName,
                argumentTypes,
                argsSourceInfo,
                diagnostics,
                maybeInstanceMethod ? MethodFinder.InvocationType.BOTH : MethodFinder.InvocationType.STATIC,
                expressionNode.getSourceInfo());

            if (method != null) {
                return method;
            }
        }

        // If no applicable methods were found, we treat the identifier as the name of a class and
        // see if there is a constructor that accepts our arguments.
        CtClass ctorClass = resolver.tryResolveClass(methodFullName);
        if (ctorClass == null) {
            ctorClass = resolver.tryResolveClassAgainstImports(methodName);
        }

        if (ctorClass != null) {
            CtConstructor constructor = new MethodFinder(invokingType, ctorClass).findConstructor(
                argumentTypes, argsSourceInfo, diagnostics, expressionNode.getSourceInfo());

            if (constructor != null) {
                return constructor;
            }
        }

        if (diagnostics.size() == 1) {
            throw new MarkupException(diagnostics.get(0).getSourceInfo(), diagnostics.get(0).getDiagnostic());
        }

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.cannotBindFunction(
                expressionNode.getSourceInfo(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        throw SymbolResolutionErrors.methodNotFound(expressionNode.getSourceInfo(), declaringClass, methodName);
    }

    private CtBehavior findInverseMethod(CtMethod method, FunctionExpressionNode functionExpression) {
        Resolver resolver;
        SourceInfo sourceInfo = functionExpression.getSourceInfo();
        TextNode inverseMethod = functionExpression.getInverseMethod();
        String inverseMethodName = inverseMethod != null ? inverseMethod.getText() : null;
        CtClass declaringClass;

        if (inverseMethodName == null) {
            resolver = new Resolver(sourceInfo);
            Annotation annotation = resolver.tryResolveMethodAnnotation(
                method, Classes.InverseMethodAnnotationName);

            if (annotation == null) {
                throw BindingSourceErrors.methodNotInvertible(sourceInfo, method);
            }

            declaringClass = method.getDeclaringClass();
            inverseMethodName = TypeHelper.getAnnotationString(annotation, "value");
            if (inverseMethodName == null) {
                throw SymbolResolutionErrors.methodNotFound(sourceInfo, declaringClass, null);
            }
        } else if (inverseMethodName.contains(".")) {
            resolver = new Resolver(inverseMethod.getSourceInfo());
            String[] parts = inverseMethodName.split("\\.");
            String className = Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("."));
            declaringClass = resolver.resolveClassAgainstImports(className);
            inverseMethodName = parts[parts.length - 1];
        } else {
            declaringClass = functionExpression.getPath().getSource().getType().getJvmType();
            resolver = new Resolver(sourceInfo);
        }

        TypeInstance requiredReturnType = resolver.getParameterTypes(method, List.of(invokingType))[0];
        TypeInstance requiredParamType = resolver.getReturnType(method);
        List<CtBehavior> discardedMethods = new ArrayList<>();
        String inverseMethodNameCopy = inverseMethodName;

        CtMethod result = resolver.tryResolveMethod(declaringClass, m -> {
            if (!m.getName().equals(inverseMethodNameCopy) || Modifier.isPrivate(m.getModifiers())) {
                return false;
            }

            TypeInstance[] paramTypes = resolver.getParameterTypes(m, List.of(invokingType));
            if (paramTypes.length == 1
                && unchecked(sourceInfo, () -> requiredParamType.subtypeOf(paramTypes[0]))
                && unchecked(sourceInfo, () -> resolver.getReturnType(m).subtypeOf(requiredReturnType))) {
                return true;
            }

            discardedMethods.add(m);
            return false;
        });

        if (result != null) {
            return result;
        }

        CtClass ctorClass = resolver.tryResolveClass(inverseMethodName);
        if (ctorClass == null) {
            ctorClass = resolver.tryResolveClassAgainstImports(inverseMethodName);
        }

        if (ctorClass != null) {
            TypeInstance ctorClassType = resolver.getTypeInstance(ctorClass);

            for (CtConstructor constructor : ctorClass.getConstructors()) {
                TypeInstance[] paramTypes = resolver.getParameterTypes(constructor, List.of(invokingType));
                if (paramTypes.length != 1 || !requiredParamType.subtypeOf(paramTypes[0])) {
                    discardedMethods.add(constructor);
                    continue;
                }

                if (!ctorClassType.subtypeOf(requiredReturnType)) {
                    discardedMethods.add(constructor);
                    continue;
                }

                return constructor;
            }
        }

        if (discardedMethods.isEmpty()) {
            throw SymbolResolutionErrors.methodNotFound(
                inverseMethod != null ? inverseMethod.getSourceInfo() : sourceInfo,
                declaringClass,
                inverseMethodName);
        }

        throw BindingSourceErrors.invalidInverseMethod(
            inverseMethod != null ? inverseMethod.getSourceInfo() : sourceInfo,
            method,
            discardedMethods.toArray(CtBehavior[]::new));
    }

    private static record MethodInfoKey(
        FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {}

    protected static record MethodInvocationInfo(
        boolean observable,
        CtBehavior method,
        CtBehavior inverseMethod,
        List<EmitMethodArgumentNode> arguments) {}

}
