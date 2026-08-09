// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.util;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.Node;
import org.jfxcore.compiler.ast.ObservableDependencyKind;
import org.jfxcore.compiler.ast.ResolvedTypeNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitApplyMarkupExtensionNode;
import org.jfxcore.compiler.ast.emit.EmitInvariantPathNode;
import org.jfxcore.compiler.ast.emit.EmitMethodArgumentNode;
import org.jfxcore.compiler.ast.emit.EmitObservablePathNode;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.expression.BindingContextNode;
import org.jfxcore.compiler.ast.expression.BindingEmitterInfo;
import org.jfxcore.compiler.ast.expression.ConstructorExpressionNode;
import org.jfxcore.compiler.ast.expression.ExpressionNode;
import org.jfxcore.compiler.ast.expression.FunctionExpressionNode;
import org.jfxcore.compiler.ast.expression.BindingOperator;
import org.jfxcore.compiler.ast.expression.PathExpressionNode;
import org.jfxcore.compiler.ast.expression.path.InconvertibleArgumentException;
import org.jfxcore.compiler.ast.expression.path.ResolvedPath;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.diagnostic.Diagnostic;
import org.jfxcore.compiler.diagnostic.DiagnosticInfo;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.BindingSourceErrors;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.ObjectInitializationErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.transform.markup.util.MarkupExtensionInfo;
import org.jfxcore.compiler.type.AnnotationDeclaration;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.ConstructorDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeHelper;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.AccessVerifier;
import org.jfxcore.compiler.util.Callable;
import org.jfxcore.compiler.util.MethodFinder;
import org.jfxcore.compiler.util.NameHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.type.KnownSymbols.*;

abstract class AbstractFunctionEmitterFactory {

    private final TypeInstance invokingType;
    private final TypeInstance targetType;
    private final Map<InvocationInfoKey, InvocationInfo> invocationCache = new HashMap<>();
    private final Map<ConstructionInvocationInfoKey, InvocationInfo> constructionInvocationCache = new HashMap<>();

    protected AbstractFunctionEmitterFactory(TypeInstance invokingType, @Nullable TypeInstance targetType) {
        this.invokingType = invokingType;
        this.targetType = targetType;
    }

    protected InvocationInfo createInvocation(
            FunctionExpressionNode functionExpression, boolean bidirectional, boolean preferObservable) {
        return createInvocation(functionExpression, bidirectional, preferObservable, targetType);
    }

    private InvocationInfo createInvocation(
            FunctionExpressionNode functionExpression,
            boolean bidirectional,
            boolean preferObservable,
            @Nullable TypeInstance targetType) {
        var key = new InvocationInfoKey(functionExpression, bidirectional, preferObservable, targetType);
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

        List<Node> methodArguments = functionExpression.getArguments();
        Callable function = findMethod(methodPath, targetType, witnesses, methodArguments, preferObservable);
        Callable inverseFunction = null;

        TypeInvoker invoker = new TypeInvoker(functionExpression.getSourceInfo());
        TypeInstance[] paramTypes = invoker.invokeParameterTypes(function.getBehavior(), function.getInvocationContext(), witnesses);
        TypeInstance returnType = invoker.invokeReturnType(function.getBehavior(), function.getInvocationContext(), witnesses);

        PreparedArguments preparedArguments = prepareArguments(
            function.getBehavior(), paramTypes, methodArguments, bidirectional, preferObservable,
            function.getReceiverDependencyKind() != ObservableDependencyKind.NONE, functionExpression.getSourceInfo());

        List<EmitMethodArgumentNode> argumentValues = preparedArguments.values();
        boolean observableFunction = preparedArguments.observable();

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

                inverseFunction = findInverseCallable(
                    inversePath, paramTypes[0], witnesses,
                    List.of(new ReturnValueNode()), preferObservable);
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

    protected InvocationInfo createConstructorInvocation(
            ConstructorExpressionNode expression,
            boolean preferObservable,
            boolean bidirectional) {
        var key = new ConstructionInvocationInfoKey(expression, preferObservable, bidirectional);
        InvocationInfo cached = constructionInvocationCache.get(key);
        if (cached != null) {
            return cached;
        }

        BindingEmitterInfo qualifierInfo = expression.getQualifier() != null
            ? expression.getQualifier().toEmitter(
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                invokingType, null)
            : null;

        TypeInstance owner = qualifierInfo != null ? qualifierInfo.getValueType() : null;
        SourceInfo typeSourceInfo = expression.getConstructedType().getSourceInfo();
        Resolver resolver = new Resolver(typeSourceInfo);
        TypeDeclaration constructedClass;

        if (owner == null) {
            constructedClass = resolver.resolveClassAgainstImports(expression.getConstructedType().formatText());

            if (!constructedClass.isStatic() && constructedClass.declaringType().isPresent()) {
                throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
            }
        } else {
            String memberName = expression.getConstructedType().formatText();
            constructedClass = resolver.tryResolveNestedClass(owner.declaration(), memberName);

            if (constructedClass == null) {
                throw SymbolResolutionErrors.memberNotFound(typeSourceInfo, owner.declaration(), memberName);
            }

            if (constructedClass.isStatic() || constructedClass.declaringType().isEmpty()) {
                throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
            }
        }

        AccessVerifier.verifyAccessible(constructedClass, expression.getInvocationContext(), typeSourceInfo);

        if (constructedClass.isAbstract()
                || constructedClass.isInterface()
                || constructedClass.isPrimitive()
                || constructedClass.isArray()) {
            throw ObjectInitializationErrors.constructorNotFound(typeSourceInfo, constructedClass);
        }

        List<TypeInstance> classArguments = expression.getClassArguments().stream()
            .map(PathNode::resolve)
            .toList();

        TypeInvoker invoker = new TypeInvoker(typeSourceInfo);

        TypeInstance constructedType = owner != null
            ? invoker.invokeType(owner, constructedClass, classArguments)
            : invoker.invokeType(constructedClass, classArguments);

        List<TypeInstance> invocationContext = getConstructionInvocationContext(constructedType);

        List<TypeInstance> witnesses = expression.getConstructorWitnesses().stream()
            .map(PathNode::resolve)
            .toList();

        List<TypeInstance> argumentTypes = expression.getArguments().stream()
            .map(argument -> getArgumentType(argument, preferObservable))
            .toList();

        List<SourceInfo> argumentSourceInfo = expression.getArguments().stream()
            .map(Node::getSourceInfo)
            .toList();

        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        ConstructorDeclaration constructor = new MethodFinder(invocationContext, constructedClass).findConstructor(
            witnesses, argumentTypes, argumentSourceInfo, diagnostics, expression.getSourceInfo());

        if (constructor == null) {
            if (!diagnostics.isEmpty()) {
                throw ObjectInitializationErrors.constructorNotFound(
                    expression.getSourceInfo(),
                    constructedClass,
                    diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
            }

            throw ObjectInitializationErrors.constructorNotFound(expression.getSourceInfo(), constructedClass);
        }

        AccessVerifier.verifyAccessible(constructor, expression.getInvocationContext(), expression.getSourceInfo());

        ObservableDependencyKind receiverDependencyKind = qualifierInfo != null
            ? getArgumentDependencyKind(qualifierInfo)
            : ObservableDependencyKind.NONE;

        Callable callable = new Callable(
            invocationContext, qualifierInfo != null ? List.of(qualifierInfo.getValue()) : List.of(),
            receiverDependencyKind, constructor, expression.getSourceInfo());

        TypeInstance[] parameterTypes = invoker.invokeSourceParameterTypes(constructor, invocationContext, witnesses);

        PreparedArguments preparedArguments = prepareArguments(
            constructor, parameterTypes, expression.getArguments(), bidirectional, preferObservable,
            receiverDependencyKind != ObservableDependencyKind.NONE, expression.getSourceInfo());

        Callable inverseFunction = null;

        if (bidirectional) {
            if (preparedArguments.values().size() != 1) {
                throw BindingSourceErrors.invalidBidirectionalMethodParamCount(expression.getSourceInfo());
            }

            Node argument = expression.getArguments().get(0);
            if (!(argument instanceof PathExpressionNode)) {
                throw BindingSourceErrors.invalidBidirectionalMethodParamKind(argument.getSourceInfo());
            }

            PathExpressionNode inversePath = expression.getInversePath();
            if (inversePath != null) {
                class ReturnValueNode extends AbstractNode implements ValueEmitterNode {
                    final ResolvedTypeNode type = new ResolvedTypeNode(constructedType, typeSourceInfo);
                    ReturnValueNode() { super(typeSourceInfo); }
                    @Override public void emit(BytecodeEmitContext context) {}
                    @Override public ValueEmitterNode deepClone() { return null; }
                    @Override public ResolvedTypeNode getType() { return type; }
                }

                List<TypeInstance> inverseWitnesses = inversePath.getSegments()
                    .get(inversePath.getSegments().size() - 1)
                    .getWitnesses()
                    .stream()
                    .map(PathNode::resolve)
                    .toList();

                inverseFunction = findInverseCallable(
                    inversePath,
                    parameterTypes[0],
                    inverseWitnesses,
                    List.of(new ReturnValueNode()),
                    preferObservable);
            } else {
                inverseFunction = findInverseFunctionViaAnnotation(
                    callable,
                    parameterTypes[0],
                    constructedType,
                    typeSourceInfo);
            }
        }

        InvocationInfo result = new InvocationInfo(
            preparedArguments.observable(), constructedType, callable,
            inverseFunction, preparedArguments.values());

        constructionInvocationCache.put(key, result);

        return result;
    }

    private PreparedArguments prepareArguments(
            BehaviorDeclaration behavior,
            TypeInstance[] parameterTypes,
            Collection<? extends Node> sourceArguments,
            boolean bidirectional,
            boolean preferObservable,
            boolean observable,
            SourceInfo invocationSourceInfo) {
        Queue<Node> arguments = new ArrayDeque<>(sourceArguments);
        boolean varargs = behavior.isVarArgs();

        if (!varargs && arguments.size() != parameterTypes.length
                || varargs && arguments.size() < parameterTypes.length - 1) {
            throw GeneralErrors.numFunctionArgumentsMismatch(
                SourceInfo.span(arguments),
                NameHelper.getDisplaySignature(behavior, parameterTypes),
                parameterTypes.length,
                arguments.size());
        }

        List<EmitMethodArgumentNode> argumentValues = new ArrayList<>();

        for (int i = 0; i < parameterTypes.length; ++i) {
            EmitMethodArgumentNode argumentValue;

            if (i < parameterTypes.length - 1 || !varargs) {
                Node argument = arguments.remove();

                try {
                    argumentValue = createSingleFunctionArgumentValue(
                        argument, parameterTypes[i], bidirectional, preferObservable);
                } catch (InconvertibleArgumentException ex) {
                    if (ex.getCause() instanceof MarkupException markupException) {
                        throw markupException;
                    }

                    throw GeneralErrors.cannotAssignFunctionArgument(
                        argument.getSourceInfo(),
                        behavior.displaySignature(false, false),
                        i, ex.getTypeName());
                }
            } else if (arguments.isEmpty()) {
                argumentValue = EmitMethodArgumentNode.newVariadic(
                    parameterTypes[i].componentType(), List.of(), invocationSourceInfo);
            } else {
                argumentValue = createVariadicFunctionArgumentValue(
                    behavior, new ArrayList<>(arguments), parameterTypes[i], i,
                    bidirectional, preferObservable);
            }

            argumentValues.add(argumentValue);
            observable |= argumentValue.isObservable();
        }

        return new PreparedArguments(argumentValues, observable);
    }

    private static List<TypeInstance> getConstructionInvocationContext(TypeInstance constructedType) {
        var result = new ArrayList<TypeInstance>();
        addOwnerChain(result, constructedType);
        return result;
    }

    private static void addOwnerChain(List<TypeInstance> result, TypeInstance type) {
        TypeInstance owner = type.owner();
        if (owner != null) {
            addOwnerChain(result, owner);
        }

        result.add(type);
    }

    private EmitMethodArgumentNode createVariadicFunctionArgumentValue(
            BehaviorDeclaration method, List<Node> arguments, TypeInstance paramType,
            int paramIndex, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = SourceInfo.span(
            arguments.get(0).getSourceInfo(),
            arguments.get(arguments.size() - 1).getSourceInfo());

        try {
            TypeInstance componentType = paramType.componentType();
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
                sourceInfo, method.displaySignature(false, false), paramIndex, ex.getTypeName());
        }
    }

    protected final EmitMethodArgumentNode createSingleFunctionArgumentValue(
            Node argument, TypeInstance paramType, boolean bidirectional, boolean preferObservable) {
        SourceInfo sourceInfo = argument.getSourceInfo();

        if (argument instanceof ExpressionNode expressionArg) {
            if (!(argument instanceof FunctionExpressionNode) && !(argument instanceof PathExpressionNode)) {
                try {
                    BindingEmitterInfo emitterInfo = expressionArg.toEmitter(
                        preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                        invokingType,
                        paramType);

                    return EmitMethodArgumentNode.newScalar(
                        paramType, emitterInfo.getValue(),
                        getArgumentDependencyKind(emitterInfo), sourceInfo);
                } catch (MarkupException ex) {
                    throw new InconvertibleArgumentException(argument.getClass().getName(), ex);
                }
            }

            EmitterFactory factory;

            if (argument instanceof FunctionExpressionNode funcExpressionArg) {
                InvocationInfo invocationInfo = createInvocation(funcExpressionArg, false, preferObservable, paramType);
                if (invocationInfo.observable()) {
                    factory = new ObservableFunctionEmitterFactory(funcExpressionArg, invokingType, paramType);
                } else {
                    factory = new SimpleFunctionEmitterFactory(funcExpressionArg, invokingType, paramType);
                }
            } else if (argument instanceof PathExpressionNode pathExpressionArg) { // always true
                if (preferObservable && pathExpressionArg.resolvePath(true).isObservable()) {
                    factory = new ObservablePathEmitterFactory(pathExpressionArg);
                } else {
                    factory = new SimplePathEmitterFactory(pathExpressionArg);
                }
            } else {
                throw new AssertionError();
            }

            try {
                BindingEmitterInfo emitterInfo;

                if (factory instanceof ObservablePathEmitterFactory observablePathFactory) {
                    emitterInfo = observablePathFactory.newInstance(bidirectional, true);
                } else if (factory instanceof ObservableEmitterFactory observableFactory) {
                    emitterInfo = observableFactory.newInstance(bidirectional);
                } else {
                    emitterInfo = factory.newInstance();
                }

                if (emitterInfo == null) {
                    if (argument instanceof PathExpressionNode pathExpressionArg) {
                        emitterInfo = new SimplePathEmitterFactory(pathExpressionArg).newInstance();
                    } else {
                        throw new AssertionError();
                    }
                }

                return EmitMethodArgumentNode.newScalar(
                    paramType, emitterInfo.getValue(), getArgumentDependencyKind(emitterInfo), sourceInfo);
            } catch (MarkupException ex) {
                throw new InconvertibleArgumentException(argument.getClass().getName(), ex);
            }
        }

        if (argument instanceof ValueEmitterNode valueEmitterArg) {
            var extensionInfo = MarkupExtensionInfo.of(valueEmitterArg);

            if (extensionInfo instanceof MarkupExtensionInfo.Supplier supplierInfo) {
                return EmitMethodArgumentNode.newScalar(
                    paramType,
                    new EmitApplyMarkupExtensionNode.Supplier(
                        valueEmitterArg, supplierInfo.markupExtensionInterface(), null,
                        paramType, supplierInfo.returnType(), null),
                    ObservableDependencyKind.NONE, sourceInfo);
            }

            if (extensionInfo instanceof MarkupExtensionInfo.PropertyConsumer) {
                throw new InconvertibleArgumentException(argument.getClass().getName(),
                    ObjectInitializationErrors.invalidMarkupExtensionUsage(sourceInfo));
            }

            return EmitMethodArgumentNode.newScalar(
                paramType, valueEmitterArg, ObservableDependencyKind.NONE, sourceInfo);
        }

        throw new InconvertibleArgumentException(argument.getClass().getName());
    }

    private Callable findMethod(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable) {
        return findCallable(
            pathExpression, returnType, typeWitnesses, arguments,
            preferObservable, true, false);
    }

    private Callable findInverseCallable(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable) {
        return findCallable(
            pathExpression, returnType, typeWitnesses, arguments,
            preferObservable, true, true);
    }

    private Callable findCallable(
            PathExpressionNode pathExpression,
            @Nullable TypeInstance returnType,
            List<TypeInstance> typeWitnesses,
            Collection<Node> arguments,
            boolean preferObservable,
            boolean maybeInstanceMethod,
            boolean allowConstructor) {
        String methodName;
        TypeDeclaration declaringClass;
        ResolvedPath resolvedPath = null;
        List<TypeInstance> invocationContext = List.of(invokingType);
        boolean isConstructor = false;

        if (pathExpression.getSegments().size() > 1) {
            // If we assume that the path points to a method, we limit path resolution to all but the
            // last segment of the path (since method names are not part of the resolvable path).
            int limit = pathExpression.getSegments().size() - 1;
            methodName = pathExpression.getSegments().get(limit).getText();
            String className = null;

            try {
                if (maybeInstanceMethod) {
                    resolvedPath = pathExpression.resolvePath(preferObservable, limit);
                    className = resolvedPath.getValueTypeInstance().javaName();
                    invocationContext = List.of(resolvedPath.getValueTypeInstance());
                }
            } catch (MarkupException ignored) {
                maybeInstanceMethod = false;
            }

            if (!maybeInstanceMethod) {
                className = pathExpression.getSimplePath(limit);

                // If we don't have a valid path expression, the only other possible interpretation would be
                // a static method call. Since a static method call is not resolved by a path expression, we
                // check that only the default binding context selector is used.
                if (!pathExpression.getBindingContext().getSelector().isDefault()) {
                    throw BindingSourceErrors.bindingContextNotApplicable(pathExpression.getBindingContext().getSourceInfo());
                }
            }

            var resolver = new Resolver(pathExpression.getSourceInfo());
            declaringClass = resolver.tryResolveClassAgainstImports(className);
            if (declaringClass == null) {
                declaringClass = resolver.tryResolveNestedClass(
                    pathExpression.getBindingContext().getType().getTypeDeclaration(), className);
            }

            if (declaringClass == null && allowConstructor) {
                className = pathExpression.getSimplePath();
                declaringClass = resolver.tryResolveClass(className);
                isConstructor = true;
            }

            if (declaringClass == null) {
                throw SymbolResolutionErrors.memberNotFound(
                    pathExpression.getSourceInfo(),
                    pathExpression.getBindingContext().getType().getTypeDeclaration(),
                    methodName);
            }
        } else {
            methodName = pathExpression.getSimplePath();
            declaringClass = pathExpression.getBindingContext().getType().getTypeDeclaration();
            invocationContext = List.of(pathExpression.getBindingContext().getType().getTypeInstance());
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
            var methodFinder = new MethodFinder(invocationContext, declaringClass);
            MethodDeclaration method = methodFinder.findMethod(
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
                if (!maybeInstanceMethod && !method.isStatic()) {
                    throw SymbolResolutionErrors.instanceMemberReferencedFromStaticContext(
                        pathExpression.getSourceInfo(), method);
                }

                ReceiverInfo receiverInfo = getMethodReceiverInfo(
                    pathExpression, resolvedPath, method, preferObservable);

                return new Callable(
                    invocationContext,
                    receiverInfo.emitters(),
                    receiverInfo.dependencyKind(),
                    method, pathExpression.getSourceInfo());
            }
        }

        // Only inverseMethod has constructor-name semantics. Ordinary calls never enter this branch.
        if (allowConstructor) {
            var resolver = new Resolver(pathExpression.getSourceInfo());
            TypeDeclaration ctorClass = resolver.tryResolveClass(pathExpression.getSimplePath());
            if (ctorClass == null) {
                ctorClass = resolver.tryResolveClassAgainstImports(methodName);
            }

            if (ctorClass != null && (ctorClass.isStatic() || ctorClass.declaringType().isEmpty())) {
                ConstructorDeclaration constructor = new MethodFinder(List.of(invokingType), ctorClass).findConstructor(
                    typeWitnesses, argumentTypes, argumentsSourceInfo,
                    diagnostics, pathExpression.getSourceInfo());

                if (constructor != null) {
                    TypeInstance constructedType = new TypeInvoker(pathExpression.getSourceInfo()).invokeType(ctorClass);
                    if (returnType != null && !constructedType.subtypeOf(returnType)) {
                        throw GeneralErrors.incompatibleReturnValue(
                            pathExpression.getSourceInfo(), constructor, returnType);
                    }

                    return new Callable(
                        List.of(constructedType), List.of(), ObservableDependencyKind.NONE,
                        constructor, pathExpression.getSourceInfo());
                }
            }
        }

        // At this point, we've tried to find a method that is applicable for the arguments and failed.
        // If we were looking for an instance method, we try again, but only look for static methods.
        if (maybeInstanceMethod && resolvedPath == null) {
            return findCallable(
                pathExpression, returnType, typeWitnesses, arguments,
                preferObservable, false, allowConstructor);
        }

        if (diagnostics.size() == 1) {
            throw new MarkupException(diagnostics.get(0).getSourceInfo(), diagnostics.get(0).getDiagnostic());
        }

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.cannotBindFunction(
                pathExpression.getSourceInfo(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        throw SymbolResolutionErrors.memberNotFound(pathExpression.getSourceInfo(), declaringClass, methodName);
    }

    private TypeInstance getArgumentType(Node argument, boolean preferObservable) {
        if (argument instanceof FunctionExpressionNode funcExpressionArg) {
            return createInvocation(funcExpressionArg, false, preferObservable, null).type();
        } else if (argument instanceof PathExpressionNode pathExpressionArg) {
            BindingOperator operator = pathExpressionArg.getOperator();
            if (operator == BindingOperator.NOT || operator == BindingOperator.BOOLIFY) {
                return TypeInstance.booleanType();
            }

            return pathExpressionArg.resolvePath(preferObservable).getValueTypeInstance();
        } else if (argument instanceof ExpressionNode expression) {
            BindingEmitterInfo emitterInfo = expression.toEmitter(
                preferObservable ? BindingMode.UNIDIRECTIONAL : BindingMode.ONCE,
                invokingType,
                null);

            return emitterInfo.getValueType();
        } else if (argument instanceof ValueEmitterNode) {
            var extensionInfo = MarkupExtensionInfo.of(argument);

            if (extensionInfo instanceof MarkupExtensionInfo.Supplier supplierInfo) {
                return supplierInfo.providedTypes().size() > 1
                    ? TypeInstance.ofUnion(supplierInfo.providedTypes())
                    : supplierInfo.providedTypes().get(0);
            }

            if (extensionInfo instanceof MarkupExtensionInfo.PropertyConsumer) {
                throw ObjectInitializationErrors.invalidMarkupExtensionUsage(argument.getSourceInfo());
            }

            return TypeHelper.getTypeInstance(argument);
        }

        throw GeneralErrors.expressionNotApplicable(argument.getSourceInfo(), false);
    }

    private ReceiverInfo getMethodReceiverInfo(PathExpressionNode pathExpression,
                                               ResolvedPath resolvedPath,
                                               MethodDeclaration method,
                                               boolean preferObservable) {
        if (resolvedPath != null) {
            if (preferObservable && resolvedPath.isObservable()) {
                return new ReceiverInfo(
                    List.of(new EmitObservablePathNode(resolvedPath, false, pathExpression.getSourceInfo())),
                    getReceiverDependencyKind(resolvedPath));
            }

            return new ReceiverInfo(
                List.of(new EmitInvariantPathNode(
                    resolvedPath.toValueEmitters(pathExpression.getSourceInfo()),
                    pathExpression.getSourceInfo())),
                ObservableDependencyKind.NONE);
        }

        if (!method.isStatic()) {
            BindingContextNode bindingSource = pathExpression.getBindingContext();
            var result = new ArrayList<ValueEmitterNode>(1);
            var segment = bindingSource.toSegment();

            if (preferObservable && segment.getObservableDependencyKind() != ObservableDependencyKind.NONE) {
                result.add(segment.toEmitter(true, bindingSource.getSourceInfo()));
                return new ReceiverInfo(result, segment.getObservableDependencyKind());
            }

            result.add(segment.toValueEmitter(false, bindingSource.getSourceInfo()));
            return new ReceiverInfo(result, ObservableDependencyKind.NONE);
        }

        return new ReceiverInfo(List.of(), ObservableDependencyKind.NONE);
    }

    private Callable findInverseFunctionViaAnnotation(
            Callable method, TypeInstance argumentType, TypeInstance returnType, SourceInfo sourceInfo) {
        AnnotationDeclaration annotation = method.getBehavior()
            .annotation(Markup.InverseMethodAnnotationName)
            .orElse(null);

        if (annotation == null) {
            throw BindingSourceErrors.methodNotInvertible(sourceInfo, method.getBehavior());
        }

        String methodName = annotation.getString("value");
        if (methodName == null) {
            throw BindingSourceErrors.invalidInverseMethodAnnotationValue(sourceInfo, method.getBehavior());
        }

        TypeDeclaration declaringClass = method.getBehavior().declaringType();
        List<DiagnosticInfo> diagnostics = new ArrayList<>();

        // TODO: Do we need a way to specify type witnesses for inverse methods?
        MethodDeclaration foundMethod = new MethodFinder(method.getInvocationContext(), declaringClass).findMethod(
            methodName, false, argumentType, List.of(), List.of(returnType), List.of(sourceInfo), diagnostics, sourceInfo);

        if (!diagnostics.isEmpty()) {
            throw BindingSourceErrors.invalidInverseMethod(
                sourceInfo, method.getBehavior(),
                diagnostics.stream().map(DiagnosticInfo::getDiagnostic).toArray(Diagnostic[]::new));
        }

        return new Callable(
            method.getInvocationContext(),
            method.getReceiver(),
            method.getReceiverDependencyKind(),
            foundMethod,
            sourceInfo);
    }

    private ObservableDependencyKind getArgumentDependencyKind(BindingEmitterInfo emitterInfo) {
        if (emitterInfo.getObservableDependencyKind() != ObservableDependencyKind.NONE) {
            return emitterInfo.getObservableDependencyKind();
        }

        return emitterInfo.getValueSourceType() != null
            ? ObservableDependencyKind.VALUE
            : ObservableDependencyKind.NONE;
    }

    private ObservableDependencyKind getReceiverDependencyKind(ResolvedPath resolvedPath) {
        if (!resolvedPath.isObservable()) {
            return ObservableDependencyKind.NONE;
        }

        return resolvedPath.getObservableDependencyKind() != ObservableDependencyKind.NONE
            ? resolvedPath.getObservableDependencyKind()
            : ObservableDependencyKind.VALUE;
    }

    private record InvocationInfoKey(
        FunctionExpressionNode functionExpression,
        boolean bidirectional,
        boolean preferObservable,
        @Nullable TypeInstance targetType) {}

    private record ConstructionInvocationInfoKey(
        ConstructorExpressionNode expression,
        boolean preferObservable,
        boolean bidirectional) {}

    private record ReceiverInfo(
        List<ValueEmitterNode> emitters,
        ObservableDependencyKind dependencyKind) {}

    private record PreparedArguments(
        List<EmitMethodArgumentNode> values,
        boolean observable) {}

    protected record InvocationInfo(
        boolean observable,
        TypeInstance type,
        Callable function,
        Callable inverseFunction,
        List<EmitMethodArgumentNode> arguments) {}
}
