// Copyright (c) 2022, 2024, JFXcore. All rights reserved.
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
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.util.Callable;
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
    private final TypeInstance targetType;
    private final Map<InvocationInfoKey, InvocationInfo> invocationCache = new HashMap<>();

    protected AbstractFunctionEmitterFactory(TypeInstance invokingType, @Nullable TypeInstance targetType) {
        this.invokingType = invokingType;
        this.targetType = targetType;
    }

    protected InvocationInfo createInvocation(
            FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {
        var key = new InvocationInfoKey(functionExpression, bidirectional, preferObservable);
        var cachedInvocationInfo = invocationCache.get(key);
        if (cachedInvocationInfo != null) {
            return cachedInvocationInfo;
        }

        PathExpressionNode methodPath = functionExpression.getPath();
        List<TypeInstance> witnesses = methodPath.getSegments()
            .get(methodPath.getSegments().size() - 1)
            .getWitnesses()
            .stream()
            .map(PathNode::resolve)
            .toList();

        Queue<Node> methodArguments = new ArrayDeque<>(functionExpression.getArguments());
        Callable function = findFunction(methodPath, targetType, witnesses, methodArguments, preferObservable);
        Callable inverseFunction = null;

        Resolver resolver = new Resolver(functionExpression.getSourceInfo());
        boolean isVarArgs = Modifier.isVarArgs(function.getBehavior().getModifiers());
        TypeInstance[] paramTypes = resolver.getParameterTypes(function.getBehavior(), List.of(invokingType), witnesses);
        TypeInstance returnType = resolver.getTypeInstance(function.getBehavior(), List.of(invokingType), witnesses);
        List<EmitMethodArgumentNode> argumentValues = new ArrayList<>();
        boolean observableFunction = false;

        if (!isVarArgs && methodArguments.size() != paramTypes.length
                || isVarArgs && methodArguments.size() < paramTypes.length) {
            throw GeneralErrors.numFunctionArgumentsMismatch(
                SourceInfo.span(methodArguments),
                NameHelper.getLongMethodSignature(function.getBehavior()),
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
                        argument.getSourceInfo(), NameHelper.getLongMethodSignature(function.getBehavior()),
                        i, ex.getTypeName());
                }
            } else {
                argumentValue = createVariadicFunctionArgumentValue(
                    new ArrayList<>(methodArguments), function.getBehavior(), i, bidirectional, preferObservable);
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

                inverseFunction = findFunction(
                    inversePath, paramTypes[0], witnesses, List.of(new ReturnValueNode()), preferObservable);
            } else {
                inverseFunction = findInverseFunctionViaAnnotation(
                    function, paramTypes[0], returnType, methodPath.getSourceInfo());
            }
        }

        var result = new InvocationInfo(
            observableFunction, returnType, function, inverseFunction, argumentValues);

        invocationCache.put(key, result);

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

            return EmitMethodArgumentNode.newVariadic(componentType, values, sourceInfo);
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

        if (argument instanceof BooleanNode booleanArg) {
            if (!paramType.isAssignableFrom(TypeInstance.BooleanType())) {
                throw new InconvertibleArgumentException(Classes.BooleanName);
            }

            boolean value = Boolean.parseBoolean(booleanArg.getText());

            return EmitMethodArgumentNode.newScalar(
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

            return EmitMethodArgumentNode.newScalar(
                paramType, new EmitLiteralNode(paramType, value, sourceInfo), false, sourceInfo);
        }

        if (argument instanceof TextNode textArg) {
            if (!paramType.isAssignableFrom(TypeInstance.StringType())) {
                throw new InconvertibleArgumentException(Classes.StringName);
            }

            return EmitMethodArgumentNode.newScalar(
                paramType,
                new EmitLiteralNode(paramType, textArg.getText(), sourceInfo),
                false,
                sourceInfo);
        }

        if (argument instanceof ExpressionNode) {
            EmitterFactory factory;

            if (argument instanceof FunctionExpressionNode funcExpressionArg) {
                InvocationInfo invocationInfo = createInvocation(
                    funcExpressionArg, false, preferObservable);

                if (invocationInfo.observable()) {
                    factory = new ObservableFunctionEmitterFactory(funcExpressionArg, invokingType, paramType);
                } else {
                    factory = new SimpleFunctionEmitterFactory(funcExpressionArg, invokingType, paramType);
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

                return EmitMethodArgumentNode.newScalar(
                    paramType, emitterInfo.getValue(), emitterInfo.getObservableType() != null, sourceInfo);
            } catch (MarkupException ex) {
                throw new InconvertibleArgumentException(argument.getClass().getName(), ex);
            }
        }

        if (argument instanceof ValueEmitterNode valueEmitterArg) {
            return EmitMethodArgumentNode.newScalar(paramType, valueEmitterArg, false, sourceInfo);
        }

        throw new InconvertibleArgumentException(argument.getClass().getName());
    }

    private Callable findFunction(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable) {
        return findFunction(pathExpression, returnType, typeWitnesses, arguments, preferObservable, true);
    }

    private Callable findFunction(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable,
            boolean maybeInstanceMethod) {
        String methodName;
        CtClass declaringClass;
        ResolvedPath resolvedPath = null;
        boolean isConstructor = false;

        if (pathExpression.getSegments().size() > 1) {
            // If we assume that the path points to a method, we limit path resolution to all but the
            // last segment of the path (since method names are not part of the resolvable path).
            int limit = pathExpression.getSegments().size() - 1;
            methodName = pathExpression.getSegments().get(limit).getText();
            String className = null;

            try {
                if (maybeInstanceMethod) {
                    resolvedPath = pathExpression.resolvePath(false, limit);
                    className = resolvedPath.getValueTypeInstance().getJavaName();
                }
            } catch (MarkupException ignored) {
                maybeInstanceMethod = false;
            }

            if (!maybeInstanceMethod) {
                // If we don't have a valid path expression, the only other possible interpretation would be
                // a static method call. Since a static method call is not resolved by a path expression, we
                // check that only the default binding context selector is used.
                className = pathExpression.getSimplePath(limit);
                BindingContextSelector selector = pathExpression.getBindingContext().getSelector();
                if (selector != BindingContextSelector.DEFAULT && selector != BindingContextSelector.TEMPLATED_ITEM) {
                    throw BindingSourceErrors.bindingContextNotApplicable(pathExpression.getBindingContext().getSourceInfo());
                }
            }

            var resolver = new Resolver(pathExpression.getSourceInfo());
            declaringClass = resolver.tryResolveClassAgainstImports(className);
            if (declaringClass == null) {
                declaringClass = resolver.tryResolveNestedClass(
                    pathExpression.getBindingContext().getType().getJvmType(), className);
            }

            if (declaringClass == null) {
                className = pathExpression.getSimplePath();
                declaringClass = resolver.tryResolveClass(className);
                isConstructor = true;

                if (declaringClass == null) {
                    throw SymbolResolutionErrors.memberNotFound(
                        pathExpression.getSourceInfo(),
                        pathExpression.getBindingContext().getType().getJvmType(),
                        className);
                }
            }
        } else {
            methodName = pathExpression.getSimplePath();
            declaringClass = pathExpression.getBindingContext().getType().getJvmType();
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
            var methodFinder = new MethodFinder(invokingType, declaringClass);
            CtMethod method = methodFinder.findMethod(
                methodName, false, returnType, typeWitnesses, argumentTypes,
                argumentsSourceInfo, diagnostics, pathExpression.getSourceInfo());

            // If we didn't find a method with the specified return type, relax the search to
            // include any return type if the path has a boolean operator.
            if (method == null && returnType != null && pathExpression.getOperator().isBoolean()) {
                method = methodFinder.findMethod(
                    methodName, false, null, typeWitnesses, argumentTypes,
                    argumentsSourceInfo, diagnostics, pathExpression.getSourceInfo());
            }

            if (method != null) {
                if (!maybeInstanceMethod && !Modifier.isStatic(method.getModifiers())) {
                    throw SymbolResolutionErrors.instanceMemberReferencedFromStaticContext(
                        pathExpression.getSourceInfo(), method);
                }

                return new Callable(
                    getMethodReceiverEmitters(pathExpression, resolvedPath, method),
                    method, pathExpression.getSourceInfo());
            }
        }

        // If no applicable methods were found, we treat the identifier as the name of a class and
        // see if there is a constructor that accepts our arguments.
        var resolver = new Resolver(pathExpression.getSourceInfo());
        CtClass ctorClass = resolver.tryResolveClass(pathExpression.getSimplePath());
        if (ctorClass == null) {
            ctorClass = resolver.tryResolveClassAgainstImports(methodName);
        }

        if (ctorClass != null) {
            CtConstructor constructor = new MethodFinder(invokingType, ctorClass).findConstructor(
                typeWitnesses,
                argumentTypes,
                argumentsSourceInfo,
                diagnostics,
                pathExpression.getSourceInfo());

            if (constructor != null) {
                if (returnType != null && !resolver.getTypeInstance(
                        constructor, List.of(invokingType)).subtypeOf(returnType)) {
                    throw GeneralErrors.incompatibleReturnValue(
                        pathExpression.getSourceInfo(), constructor, returnType);
                }

                return new Callable(Collections.emptyList(), constructor, pathExpression.getSourceInfo());
            }
        }

        // At this point, we've tried to find a method that is applicable for the arguments and failed.
        // If we were looking for an instance method, we try again, but only look for static methods.
        if (maybeInstanceMethod) {
            return findFunction(pathExpression, returnType, typeWitnesses, arguments, preferObservable, false);
        }

        if (diagnostics.size() == 1) {
            throw new MarkupException(diagnostics.get(0).getSourceInfo(), diagnostics.get(0).getDiagnostic());
        }

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.cannotBindFunction(
                pathExpression.getSourceInfo(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        throw SymbolResolutionErrors.memberNotFound(
            pathExpression.getSourceInfo(), declaringClass, methodName);
    }

    private TypeInstance getArgumentType(Node argument, boolean preferObservable) {
        if (argument instanceof FunctionExpressionNode funcExpressionArg) {
            return createInvocation(funcExpressionArg, false, true).type();
        } else if (argument instanceof PathExpressionNode pathExpressionArg) {
            Operator operator = pathExpressionArg.getOperator();

            if (operator == Operator.NOT || operator == Operator.BOOLIFY) {
                return TypeInstance.booleanType();
            } else {
                return pathExpressionArg.resolvePath(preferObservable).getValueTypeInstance();
            }
        } else if (argument instanceof TextNode) {
            if (argument instanceof BooleanNode) {
                return TypeInstance.BooleanType();
            } else if (argument instanceof NumberNode numberNode) {
                return NumberUtil.parseType(numberNode.getText());
            } else {
                return TypeInstance.StringType();
            }
        } else if (argument instanceof ValueEmitterNode) {
            return TypeHelper.getTypeInstance(argument);
        }

        throw GeneralErrors.expressionNotApplicable(argument.getSourceInfo(), false);
    }

    private List<ValueEmitterNode> getMethodReceiverEmitters(
            PathExpressionNode pathExpression, ResolvedPath resolvedPath, CtMethod method) {
        if (resolvedPath != null) {
            return resolvedPath.toValueEmitters(true, pathExpression.getSourceInfo());
        }

        if (!Modifier.isStatic(method.getModifiers())) {
            BindingContextNode bindingSource = pathExpression.getBindingContext();
            var result = new ArrayList<ValueEmitterNode>(1);
            result.add((bindingSource.toSegment().toValueEmitter(true, bindingSource.getSourceInfo())));
            return result;
        }

        return Collections.emptyList();
    }

    private Callable findInverseFunctionViaAnnotation(
            Callable method, TypeInstance argumentType, TypeInstance returnType, SourceInfo sourceInfo) {
        Annotation annotation = new Resolver(sourceInfo).tryResolveMethodAnnotation(
            method.getBehavior(), "InverseMethod", true);

        if (annotation == null) {
            throw BindingSourceErrors.methodNotInvertible(sourceInfo, method.getBehavior());
        }

        String methodName = TypeHelper.getAnnotationString(annotation, "value");
        if (methodName == null) {
            throw BindingSourceErrors.invalidInverseMethodAnnotationValue(sourceInfo, method.getBehavior());
        }

        CtClass declaringClass = method.getBehavior().getDeclaringClass();
        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        // TODO: Do we need a way to specify type witnesses for inverse methods?
        CtMethod jvmMethod = new MethodFinder(invokingType, declaringClass).findMethod(
            methodName, false, argumentType, List.of(), List.of(returnType), List.of(sourceInfo), diagnostics, sourceInfo);

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.invalidInverseMethod(
                sourceInfo, method.getBehavior(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        return new Callable(method.getReceiver(), jvmMethod, sourceInfo);
    }

    private record InvocationInfoKey(
        FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {}

    protected record InvocationInfo(
        boolean observable,
        TypeInstance type,
        Callable function,
        Callable inverseFunction,
        List<EmitMethodArgumentNode> arguments) { }

}
